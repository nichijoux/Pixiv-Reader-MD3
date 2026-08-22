package com.pixiv.reader.feature.reader.state

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.common.config.ReaderPageMode
import com.pixiv.reader.core.common.config.ReaderThemeMode
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.database.dao.ReadingProgressDao
import com.pixiv.reader.core.database.entity.ReadingProgressEntity
import com.pixiv.reader.core.datastore.UserPreferences
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.novel.NovelContentLoader
import com.pixiv.reader.core.network.novel.fetchAllSeriesChapters
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.core.novel.model.NovelBlock
import com.pixiv.reader.core.novel.model.NovelDocument
import com.pixiv.reader.core.novel.model.percentageAt
import com.pixiv.reader.feature.reader.R
import com.pixiv.reader.feature.reader.data.NovelTextSearch
import com.pixiv.reader.feature.reader.data.ReaderChapterCache
import com.zqc.opencc.android.lib.ChineseConverter
import com.zqc.opencc.android.lib.ConversionType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 阅读器 ViewModel。
 *
 * 职责：
 * - 加载小说详情 + 正文 HTML（数据层委托 [NovelContentLoader] 解析）
 * - 收集阅读偏好（字号/行距/字体/主题/翻页/亮度）
 * - 字符级进度：本地 Room 落库（防抖）+ 官方 marker 同步
 * - 阅读书签（marker）/ 收藏 / 追更
 * - 目录（系列分页，委托 [ReaderSeriesToc]）与全文搜索（[NovelTextSearch]）
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
    private val contentLoader: NovelContentLoader,
    private val readingProgressDao: ReadingProgressDao,
    private val userPreferences: UserPreferences,
    private val readerChapterCache: ReaderChapterCache,
    private val favoriteActions: FavoriteActions,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val novelId: Long = savedStateHandle.get<Long>("novelId") ?: 0L

    private val _novel = MutableStateFlow<Novel?>(null)
    val novel: StateFlow<Novel?> = _novel.asStateFlow()

    private val _document = MutableStateFlow<NovelDocument?>(null)
    val document: StateFlow<NovelDocument?> = _document.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error.asStateFlow()

    /** 当前阅读是否为本地文件（下载管理解析 TXT/EPUB/MD 进入），UI 顶部显示「本地」徽标。 */
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    /** 是否本地文件阅读模式（TXT/EPUB 解析后直接渲染，跳过网络/离线加载）。 */
    private val _isLocalMode = MutableStateFlow(false)

    /** 直接注入本地解析文档（下载管理点 txt/epub 进入）。 */
    fun useLocalDocument(document: NovelDocument, title: String) {
        _isLocalMode.value = true
        _isLoading.value = false
        _error.value = null
        _novel.value = Novel(id = novelId, title = title)
        _document.value = document
        _isOffline.value = true
        viewModelScope.launch {
            runCatching { restoreProgress() }
                .onFailure { e -> Log.w(TAG, "restoreProgress failed", e) }
        }
    }

    // ── 阅读偏好（DataStore 镜像到内存 StateFlow） ──
    private val _fontSize = MutableStateFlow(17f)
    val fontSize: StateFlow<Float> = _fontSize.asStateFlow()

    /** 行距增量（em，-1.0..1.0）；实际行高倍数 = 1.6 + 增量，默认 2.05 倍 → 0.45 */
    private val _lineHeight = MutableStateFlow(0.45f)
    val lineHeight: StateFlow<Float> = _lineHeight.asStateFlow()

    private val _fontFamily = MutableStateFlow("serif")
    val fontFamily: StateFlow<String> = _fontFamily.asStateFlow()

    private val _readerTheme = MutableStateFlow(ReaderThemeMode.PAPER)
    val readerTheme: StateFlow<ReaderThemeMode> = _readerTheme.asStateFlow()

    private val _pageMode = MutableStateFlow(ReaderPageMode.SCROLL)
    val pageMode: StateFlow<ReaderPageMode> = _pageMode.asStateFlow()

    private val _brightness = MutableStateFlow(1f)
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

    /** 阅读器主题是否跟随系统深色模式 */
    private val _followSystem = MutableStateFlow(false)
    val followSystem: StateFlow<Boolean> = _followSystem.asStateFlow()

    /** 自定义阅读字体文件路径（空=未设置） */
    private val _customFontPath = MutableStateFlow("")
    val customFontPath: StateFlow<String> = _customFontPath.asStateFlow()

    /** 正文字重：300 细体 / 400 常规 / 700 粗体 / 其他 100..900 自定义 */
    private val _fontWeight = MutableStateFlow(400)
    val fontWeight: StateFlow<Int> = _fontWeight.asStateFlow()

    /** 段首全角空格缩进数量（0..4） */
    private val _paragraphIndent = MutableStateFlow(2)
    val paragraphIndent: StateFlow<Int> = _paragraphIndent.asStateFlow()

    /** 简繁转换：0 关闭 / 1 简体→繁体 / 2 繁体→简体 */
    private val _chineseConvert = MutableStateFlow(0)
    val chineseConvert: StateFlow<Int> = _chineseConvert.asStateFlow()

    /** 应用语言（system/zh/en）：设置面板据此决定是否显示简繁转换区 */
    private val _appLanguage = MutableStateFlow("system")
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    /**
     * 渲染用正文（按 [chineseConvert] 转换文本块；[startChar]/[endChar] 与 [textLength]
     * 保留原始值，阅读进度/全文搜索锚点不受转换影响；fullText 保持原文供搜索匹配）。
     *
     * 转换策略（修复「重进显示无正文」/「切换不即时」）：
     * - document 就绪时**先发射原始文档**，转换完成后再发射转换结果——转换耗时期间
     *   显示原文而非空白（杜绝 stateIn 缓存 null + isLoading=false 落入 EmptyBox）
     * - 转换结果按 (novelId, mode) 缓存，切换设置/重复进入不重复转换整章
     * - 转换在 [Dispatchers.Default] 执行，不阻塞 UI
     */
    val displayDocument: StateFlow<NovelDocument?> =
        combine(_document, _chineseConvert) { doc, mode -> doc to mode }
            .flatMapLatest { (doc, mode) ->
                when {
                    doc == null -> flowOf(null)
                    mode == 0 -> flowOf(doc)
                    else -> flow {
                        emit(doc) // 先显示原文，避免转换空窗
                        emit(convertedDocument(doc, mode))
                    }.flowOn(Dispatchers.Default)
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 段距（em，0..2.0） */
    private val _paragraphSpacing = MutableStateFlow(0.6f)
    val paragraphSpacing: StateFlow<Float> = _paragraphSpacing.asStateFlow()

    /** 字距（em，-0.5..0.5） */
    private val _letterSpacing = MutableStateFlow(0f)
    val letterSpacing: StateFlow<Float> = _letterSpacing.asStateFlow()

    // ── 目录 / 搜索 ──
    private val _toc = MutableStateFlow<List<ReaderTocItem>>(emptyList())
    val toc: StateFlow<List<ReaderTocItem>> = _toc.asStateFlow()

    /** 系列目录是否加载中（系列小说需拉取系列内各本）。 */
    private val _tocLoading = MutableStateFlow(false)
    val tocLoading: StateFlow<Boolean> = _tocLoading.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Int>>(emptyList())
    val searchResults: StateFlow<List<Int>> = _searchResults.asStateFlow()

    private val _searchIndex = MutableStateFlow(-1)
    val searchIndex: StateFlow<Int> = _searchIndex.asStateFlow()

    // ── 阅读进度 ──
    private val _charOffset = MutableStateFlow(0)
    val charOffset: StateFlow<Int> = _charOffset.asStateFlow()

    private val _percentage = MutableStateFlow(0)
    val percentage: StateFlow<Int> = _percentage.asStateFlow()

    /** 进度是否已完成恢复（UI 据此决定初始翻页/滚动位置）。 */
    private val _progressRestored = MutableStateFlow(false)
    val progressRestored: StateFlow<Boolean> = _progressRestored.asStateFlow()

    // ── 服务端状态 ──
    private val _isMarked = MutableStateFlow(false)
    val isMarked: StateFlow<Boolean> = _isMarked.asStateFlow()

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked: StateFlow<Boolean> = _isBookmarked.asStateFlow()

    private val _isWatchlisted = MutableStateFlow(false)
    val isWatchlisted: StateFlow<Boolean> = _isWatchlisted.asStateFlow()

    private val _message = Channel<UiMessage>(Channel.BUFFERED)
    val message = _message.receiveAsFlow()

    private var saveJob: Job? = null
    private var markerJob: Job? = null

    init {
        collectPreferences()
        load()
    }

    /**
     * 收集阅读偏好。每个收集器独立包裹 runCatching，
     * 避免 DataStore 读取异常在 viewModelScope 里变成未捕获异常导致闪退。
     */
    private fun collectPreferences() {
        viewModelScope.launch {
            runCatching { userPreferences.readerFontSize.collect { _fontSize.value = it } }
        }
        viewModelScope.launch {
            runCatching { userPreferences.readerLineSpacing.collect { _lineHeight.value = it } }
        }
        viewModelScope.launch {
            runCatching { userPreferences.readerFontFamily.collect { _fontFamily.value = it } }
        }
        viewModelScope.launch {
            runCatching { userPreferences.readerTheme.collect { _readerTheme.value = it } }
        }
        viewModelScope.launch {
            runCatching { userPreferences.readerPageMode.collect { _pageMode.value = it } }
        }
        viewModelScope.launch {
            runCatching { userPreferences.readerBrightness.collect { _brightness.value = it } }
        }
        viewModelScope.launch {
            runCatching { userPreferences.readerFollowSystem.collect { _followSystem.value = it } }
        }
        viewModelScope.launch {
            runCatching { userPreferences.readerCustomFontPath.collect { _customFontPath.value = it } }
        }
        viewModelScope.launch {
            runCatching { userPreferences.readerFontWeight.collect { _fontWeight.value = it } }
        }
        viewModelScope.launch {
            runCatching { userPreferences.readerParagraphIndent.collect { _paragraphIndent.value = it } }
        }
        viewModelScope.launch {
            runCatching { userPreferences.readerParagraphSpacing.collect { _paragraphSpacing.value = it } }
        }
        viewModelScope.launch {
            runCatching { userPreferences.readerLetterSpacing.collect { _letterSpacing.value = it } }
        }
        viewModelScope.launch {
            runCatching { userPreferences.readerChineseConvert.collect { _chineseConvert.value = it } }
        }
        viewModelScope.launch {
            runCatching { userPreferences.appLanguage.collect { _appLanguage.value = it } }
        }
    }

    fun load() {
        viewModelScope.launch {
            if (_isLocalMode.value) return@launch
            try {
                _isLoading.value = true
                _error.value = null
                _isOffline.value = false
                // 缓存命中（阅读时预加载过 / 此前刚读过）：跳过网络与重新解析，跳章秒开
                val cached = readerChapterCache.getChapter(novelId)
                Log.i(TAG, "load novel[$novelId]: 章节缓存命中=${cached != null}（命中则跳过网络/解析，图片仍由 UI 层加载）")
                if (cached != null) {
                    applyLoaded(cached.novel, cached.document)
                } else {
                    contentLoader.load(novelId).onSuccess { (detail, document) ->
                        if (_isLocalMode.value) return@onSuccess
                        if (detail == null) return@onSuccess
                        readerChapterCache.putChapter(novelId, ReaderChapterCache.Entry(detail, document))
                        applyLoaded(detail, document)
                    }.onFailure {
                        Log.w(TAG, "load novel failed", it)
                        _error.value = it.message?.let { m -> UiMessage(R.string.reader_error_load_failed_reason, listOf(m)) }
                            ?: UiMessage(R.string.reader_error_load_failed)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "load unexpected", e)
                _error.value = UiMessage(R.string.reader_error_load_failed_reason, listOf(e.message ?: ""))
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** 正文就绪后的公共状态流转（缓存命中与网络加载共用）。 */
    private suspend fun applyLoaded(detail: Novel, document: NovelDocument) {
        _novel.value = detail
        _document.value = document
        // 目录构建含系列小说列表的网络请求，放到 IO 之外异步执行
        viewModelScope.launch { buildToc() }
        // 进度恢复异常不应当影响正文展示
        runCatching { restoreProgress() }
            .onFailure { e -> Log.w(TAG, "restoreProgress failed", e) }
        loadServerState()
    }

    /** 恢复进度：优先本地 Room，其次官方 marker，最后回到开头。 */
    private suspend fun restoreProgress() {
        val document = _document.value ?: return
        val local = readingProgressDao.getByNovel(novelId)
        val offset = when {
            local != null && local.charOffset > 0 ->
                local.charOffset.coerceIn(0, document.textLength)

            else -> estimateOffsetFromServerMarker()
        }
        _charOffset.value = offset
        _percentage.value = document.percentageAt(offset)
        _progressRestored.value = true
        // 若本次进入请求了「定位到尾页」（系列上一章首页向前翻），恢复完成后覆盖为文档末尾
        applySeekToEnd()
    }

    /** 是否请求了定位到文档末尾（reader/{id}?toEnd=true，系列上一章「尾页」进入）。 */
    private var seekToEndRequested = false

    /** 请求定位到文档末尾（系列首页向前翻 → 上一章尾页）。 */
    fun seekToEnd() {
        seekToEndRequested = true
        applySeekToEnd()
    }

    private fun applySeekToEnd() {
        if (!seekToEndRequested) return
        val document = _document.value ?: return
        seekToEndRequested = false
        val end = document.textLength
        _charOffset.value = end
        _percentage.value = document.percentageAt(end)
        _progressRestored.value = true
    }

    private suspend fun estimateOffsetFromServerMarker(): Int {
        val document = _document.value ?: return 0
        return runCatching {
            val marker = pixivRepository.api.getNovelMarkers().marked_novels
                .firstOrNull { it.novel?.id == novelId }?.novel_marker ?: return 0
            _isMarked.value = true
            estimateCharFromOfficialPage(
                page = marker.page,
                textLength = document.textLength,
                officialPageCount = (_novel.value?.page_count ?: 1).coerceAtLeast(1),
            )
        }.getOrDefault(0)
    }

    private fun loadServerState() {
        viewModelScope.launch {
            try {
                _isBookmarked.value = _novel.value?.is_bookmarked == true
                val seriesId = _novel.value?.series?.id ?: return@launch
                val resp = pixivRepository.api.getNovelSeries(seriesId)
                _isWatchlisted.value = resp.novel_series_detail?.watchlist_added == true
            } catch (e: Exception) {
                Log.w(TAG, "loadServerState failed", e)
            }
        }
    }

    // ── 进度上报（由 UI 在翻页/滚动时调用） ──

    /** 翻页模式：页码变化 → 上报页首字符偏移。 */
    fun reportPage(startChar: Int, totalPages: Int) {
        if (!_progressRestored.value) return // 恢复完成前忽略 UI 上报
        val document = _document.value ?: return
        val offset = startChar.coerceIn(0, document.textLength)
        _charOffset.value = offset
        _percentage.value = document.percentageAt(offset)
        scheduleProgressSave()
        scheduleMarkerSync(offset)
    }

    /** 滑动模式：滚动 → 上报可见位置字符偏移。 */
    fun reportScrollOffset(offset: Int) {
        if (!_progressRestored.value) return
        val document = _document.value ?: return
        val clamped = offset.coerceIn(0, document.textLength)
        if (kotlin.math.abs(clamped - _charOffset.value) < 50) return // 防抖：避免高频写
        _charOffset.value = clamped
        _percentage.value = document.percentageAt(clamped)
        scheduleProgressSave()
    }

    private fun scheduleProgressSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(800.milliseconds)
            saveProgress()
        }
    }

    private suspend fun saveProgress() {
        runCatching {
            val document = _document.value ?: return@runCatching
            val offset = _charOffset.value
            readingProgressDao.upsert(
                ReadingProgressEntity(
                    novelId = novelId,
                    seriesId = _novel.value?.series?.id ?: 0L,
                    title = _novel.value?.title,
                    coverUrl = _novel.value?.image_urls?.medium
                        ?: _novel.value?.image_urls?.square_medium,
                    chapterOrder = 0,
                    charOffset = offset,
                    percentage = document.percentageAt(offset),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }.onFailure { Log.w(TAG, "saveProgress failed", it) }
    }

    /** 离开阅读器前立即落库（由 UI onDispose 调用）。 */
    fun flushProgress() {
        saveJob?.cancel()
        viewModelScope.launch { saveProgress() }
    }

    /** 官方 marker 同步：按字符比例换算官方页码，低频上报。 */
    private fun scheduleMarkerSync(offset: Int) {
        val pageCount = _novel.value?.page_count ?: 0
        if (pageCount <= 0) return
        markerJob?.cancel()
        markerJob = viewModelScope.launch {
            delay(3000.milliseconds)
            val document = _document.value ?: return@launch
            val ratio = offset.toFloat() / document.textLength.coerceAtLeast(1)
            val page = (ratio * pageCount).toInt().coerceIn(1, pageCount)
            runCatching { pixivRepository.api.addNovelMarker(novelId, page) }
        }
    }

    // ── 阅读偏好写入 ──

    fun onFontSizeChange(value: Float) {
        _fontSize.value = value
        viewModelScope.launch { runCatching { userPreferences.setReaderFontSize(value) } }
    }

    fun onLineHeightChange(value: Float) {
        _lineHeight.value = value
        viewModelScope.launch { runCatching { userPreferences.setReaderLineSpacing(value) } }
    }

    fun onFontFamilyChange(value: String) {
        _fontFamily.value = value
        viewModelScope.launch { runCatching { userPreferences.setReaderFontFamily(value) } }
    }

    fun onFontWeightChange(value: Int) {
        _fontWeight.value = value
        viewModelScope.launch { runCatching { userPreferences.setReaderFontWeight(value) } }
    }

    fun onParagraphIndentChange(value: Int) {
        _paragraphIndent.value = value
        viewModelScope.launch { runCatching { userPreferences.setReaderParagraphIndent(value) } }
    }

    fun onParagraphSpacingChange(value: Float) {
        _paragraphSpacing.value = value
        viewModelScope.launch { runCatching { userPreferences.setReaderParagraphSpacing(value) } }
    }

    fun onLetterSpacingChange(value: Float) {
        _letterSpacing.value = value
        viewModelScope.launch { runCatching { userPreferences.setReaderLetterSpacing(value) } }
    }

    /** 简繁转换切换：0 关闭 / 1 简体→繁体 / 2 繁体→简体。 */
    fun onChineseConvertChange(value: Int) {
        _chineseConvert.value = value
        viewModelScope.launch { runCatching { userPreferences.setReaderChineseConvert(value) } }
    }

    /** 转换结果缓存：文档实例 + 模式 → 转换后文档（VM 存活期内避免重复全章转换）。 */
    private val convertCache = HashMap<Pair<Int, Int>, NovelDocument>()

    /** 转换（带实例级缓存）：先查缓存，未命中转换后写入。 */
    private fun convertedDocument(document: NovelDocument, mode: Int): NovelDocument {
        if (mode == 0) return document
        val key = System.identityHashCode(document) to mode
        synchronized(convertCache) {
            convertCache[key]?.let { return it }
        }
        val converted = convertDocument(document, mode)
        synchronized(convertCache) {
            convertCache[key] = converted
        }
        return converted
    }

    /**
     * 按转换模式重建正文文本块（Paragraph/Heading/Quote 的 text 转换；Image/Separator 原样）。
     * startChar/endChar/textLength/fullText 全部保留原始值——阅读进度、全文搜索锚点不随转换漂移。
     */
    private fun convertDocument(document: NovelDocument, mode: Int): NovelDocument {
        if (mode == 0) return document
        val type = if (mode == 1) ConversionType.S2T else ConversionType.T2S
        val convert: (String) -> String = { text ->
            runCatching { ChineseConverter.convert(text, type, context) }.getOrDefault(text)
        }
        val newBlocks = document.blocks.map { block ->
            when (block) {
                is NovelBlock.Paragraph -> block.copy(text = convert(block.text))
                is NovelBlock.Heading -> block.copy(text = convert(block.text))
                is NovelBlock.Quote -> block.copy(text = convert(block.text))
                else -> block
            }
        }
        return NovelDocument(
            blocks = newBlocks,
            fullText = document.fullText,
            textLength = document.textLength,
        )
    }

    fun onReaderThemeChange(value: ReaderThemeMode) {
        _readerTheme.value = value
        viewModelScope.launch { runCatching { userPreferences.setReaderTheme(value) } }
    }

    fun onPageModeChange(value: ReaderPageMode) {
        _pageMode.value = value
        viewModelScope.launch { runCatching { userPreferences.setReaderPageMode(value) } }
    }

    fun onBrightnessChange(value: Float) {
        _brightness.value = value
        viewModelScope.launch { runCatching { userPreferences.setReaderBrightness(value) } }
    }

    // ── 目录 / 搜索 ──

    /**
     * 构建目录：系列小说展示**系列内各本小说**；非系列小说只显示本小说一条。
     * 系列列表优先走缓存（跳章后新阅读器立即显示目录并高亮新位置）；
     * 目录就绪后预加载下一章（阅读时缓存，跳下一章秒开）。
     */
    private suspend fun buildToc() {
        val detail = _novel.value
        val series = detail?.series
        if (series == null) {
            // 非系列：目录只显示本小说
            _toc.value = listOf(
                ReaderTocItem(detail?.title ?: context.getString(R.string.reader_toc_current_novel), detail?.id ?: 0L, 0),
            )
            return
        }
        val seriesId = series.id
        readerChapterCache.getToc(seriesId)?.let { cached ->
            _toc.value = cached.map { ReaderTocItem(it.title ?: context.getString(R.string.reader_untitled), it.id, 0) }
            preloadNeighborChapters()
            return
        }
        _tocLoading.value = true
        try {
            val novels = fetchAllSeriesChapters(pixivRepository, seriesId)
            readerChapterCache.putToc(seriesId, novels)
            _toc.value = novels.map { ReaderTocItem(it.title ?: context.getString(R.string.reader_untitled), it.id, 0) }
            preloadNeighborChapters()
        } catch (e: Exception) {
            Log.w(TAG, "buildToc series failed", e)
            _toc.value = emptyList()
        } finally {
            _tocLoading.value = false
        }
    }

    /** 阅读时预加载系列上一章与下一章（已缓存则跳过）：上下章跳转秒开。 */
    private fun preloadNeighborChapters() {
        val current = _novel.value ?: return
        val tocIndex = _toc.value.indexOfFirst { it.novelId == current.id }
        if (tocIndex < 0) return
        val neighbors = listOfNotNull(
            _toc.value.getOrNull(tocIndex - 1)?.novelId,
            _toc.value.getOrNull(tocIndex + 1)?.novelId,
        )
        neighbors
            .filter { it > 0L && readerChapterCache.getChapter(it) == null }
            .forEach { id ->
                viewModelScope.launch {
                    contentLoader.load(id)
                        .onSuccess { (detail, document) ->
                            if (detail != null) {
                                readerChapterCache.putChapter(id, ReaderChapterCache.Entry(detail, document))
                                Log.d(TAG, "preload neighbor chapter $id cached")
                            }
                        }
                        .onFailure { e -> Log.w(TAG, "preload neighbor chapter $id failed", e) }
                }
            }
    }

    /** 在全文（忽略大小写）中搜索关键词，记录所有匹配的字符偏移。 */
    fun searchText(query: String) {
        val text = _document.value?.fullText ?: return
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _searchIndex.value = -1
            return
        }
        val results = NovelTextSearch.search(text, query)
        _searchResults.value = results
        _searchIndex.value = if (results.isNotEmpty()) 0 else -1
    }

    fun setSearchIndex(index: Int) {
        if (index in _searchResults.value.indices) _searchIndex.value = index
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
        _searchIndex.value = -1
    }

    // ── 自定义字体 / 跟随系统 ──

    /** 从系统文件选择器导入字体文件到私有目录，并设为自定义阅读字体。 */
    fun importCustomFont(uri: Uri) {
        viewModelScope.launch {
            val path = runCatching {
                val dir = File(context.filesDir, "fonts").apply { mkdirs() }
                val dest = File(dir, "custom_font.ttf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                } ?: throw IllegalStateException(context.getString(R.string.reader_error_font_read_failed))
                dest.absolutePath
            }
            path.onSuccess { p ->
                _customFontPath.value = p
                runCatching { userPreferences.setReaderCustomFontPath(p) }
                _message.send(UiMessage(R.string.reader_msg_font_set))
            }.onFailure {
                Log.w(TAG, "importCustomFont failed", it)
                _message.send(UiMessage(R.string.reader_msg_font_import_failed, listOf(it.message ?: "")))
            }
        }
    }

    fun clearCustomFont() {
        _customFontPath.value = ""
        viewModelScope.launch { runCatching { userPreferences.setReaderCustomFontPath("") } }
        _message.trySend(UiMessage(R.string.reader_msg_font_cleared))
    }

    fun onFollowSystemChange(value: Boolean) {
        _followSystem.value = value
        viewModelScope.launch { runCatching { userPreferences.setReaderFollowSystem(value) } }
    }

    // ── 阅读书签 / 收藏 / 追更 ──

    fun toggleMark() {
        viewModelScope.launch {
            val current = _isMarked.value
            runCatching {
                if (current) {
                    pixivRepository.api.removeNovelMarker(novelId)
                } else {
                    val offset = _charOffset.value
                    val pageCount = (_novel.value?.page_count ?: 1).coerceAtLeast(1)
                    val page = estimateOfficialPage(
                        charOffset = offset,
                        textLength = (_document.value?.textLength ?: 1).coerceAtLeast(1),
                        officialPageCount = pageCount,
                    )
                    pixivRepository.api.addNovelMarker(novelId, page)
                }
            }.onSuccess {
                _isMarked.value = !current
                _message.send(if (!current) UiMessage(R.string.reader_msg_mark_added) else UiMessage(R.string.reader_msg_mark_removed))
            }.onFailure {
                _message.send(UiMessage(R.string.reader_msg_action_failed, listOf(it.message ?: "")))
            }
        }
    }

    fun toggleBookmark() {
        viewModelScope.launch {
            val current = _isBookmarked.value
            favoriteActions.toggleNovelFavorite(novelId, !current)
                .onSuccess {
                    _isBookmarked.value = !current
                    _message.send(if (!current) UiMessage(R.string.reader_msg_bookmarked) else UiMessage(R.string.reader_msg_unbookmarked))
                }
                .onFailure {
                    _message.send(UiMessage(R.string.reader_msg_action_failed, listOf(it.message ?: "")))
                }
        }
    }

    fun toggleWatchlist() {
        val seriesId = _novel.value?.series?.id ?: run {
            _message.trySend(UiMessage(R.string.reader_msg_not_in_series))
            return
        }
        viewModelScope.launch {
            val current = _isWatchlisted.value
            runCatching {
                if (current) pixivRepository.api.removeWatchlistNovel(seriesId)
                else pixivRepository.api.addWatchlistNovel(seriesId)
            }.onSuccess {
                _isWatchlisted.value = !current
                _message.send(if (!current) UiMessage(R.string.reader_msg_watching_added) else UiMessage(R.string.reader_msg_watching_removed))
            }.onFailure {
                _message.send(UiMessage(R.string.reader_msg_action_failed, listOf(it.message ?: "")))
            }
        }
    }

    private companion object {
        const val TAG = "ReaderViewModel"
    }
}
