package com.pixiv.reader.feature.reader.state

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.common.config.ReaderDualPageMode
import com.pixiv.reader.core.common.config.ReaderPageMode
import com.pixiv.reader.core.common.ToggleUiState
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.common.loadFailureMessage
import com.pixiv.reader.core.common.R as CoreR
import com.pixiv.reader.core.common.config.ReaderThemeMode
import com.pixiv.reader.core.database.dao.ReadingProgressDao
import com.pixiv.reader.core.database.entity.ReadingProgressEntity
import com.pixiv.reader.core.datastore.UserPreferences
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.message.MessageViewModel
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

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
) : MessageViewModel() {

    private val novelId: Long = savedStateHandle.get<Long>("novelId") ?: 0L

    private val _novel = MutableStateFlow<Novel?>(null)
    val novel: StateFlow<Novel?> = _novel.asStateFlow()

    private val _document = MutableStateFlow<NovelDocument?>(null)
    val document: StateFlow<NovelDocument?> = _document.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error.asStateFlow()

    /** UI 顶部据此显示「本地」徽标。 */
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    /** 本地文档直接渲染，跳过网络加载。 */
    private val _isLocalMode = MutableStateFlow(false)

    fun useLocalDocument(document: NovelDocument, title: String) {
        if (_isLocalMode.value && _document.value === document) return
        // 先切换加载分支，避免旧在线任务回写本地文档。
        loadJob?.cancel()
        loadJob = null
        _isLocalMode.value = true
        _progressRestored.value = false
        _novel.value = Novel(id = novelId, title = title)
        _document.value = document
        _isOffline.value = true
        load()
    }

    // ── 阅读偏好（DataStore 镜像到内存 StateFlow） ──
    private val _fontSize = MutableStateFlow(17f)
    val fontSize: StateFlow<Float> = _fontSize.asStateFlow()

    /** 行距增量（em，-1.0..1.0）；实际行高倍数 = 1.6 + 增量。 */
    private val _lineHeight = MutableStateFlow(0.45f)
    val lineHeight: StateFlow<Float> = _lineHeight.asStateFlow()

    private val _fontFamily = MutableStateFlow("serif")
    val fontFamily: StateFlow<String> = _fontFamily.asStateFlow()

    private val _readerTheme = MutableStateFlow(ReaderThemeMode.PAPER)
    val readerTheme: StateFlow<ReaderThemeMode> = _readerTheme.asStateFlow()

    private val _pageMode = MutableStateFlow(ReaderPageMode.SCROLL)
    val pageMode: StateFlow<ReaderPageMode> = _pageMode.asStateFlow()

    /** 仅翻页与仿真模式生效。 */
    private val _dualPageMode = MutableStateFlow(ReaderDualPageMode.LANDSCAPE_OR_TABLET)
    val dualPageMode: StateFlow<ReaderDualPageMode> = _dualPageMode.asStateFlow()

    private val _brightness = MutableStateFlow(1f)
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

    private val _followSystem = MutableStateFlow(false)
    val followSystem: StateFlow<Boolean> = _followSystem.asStateFlow()

    private val _customFontPath = MutableStateFlow("")
    val customFontPath: StateFlow<String> = _customFontPath.asStateFlow()

    private val _fontWeight = MutableStateFlow(400)
    val fontWeight: StateFlow<Int> = _fontWeight.asStateFlow()

    private val _paragraphIndent = MutableStateFlow(2)
    val paragraphIndent: StateFlow<Int> = _paragraphIndent.asStateFlow()

    /** 0 关闭 / 1 简体→繁体 / 2 繁体→简体 */
    private val _chineseConvert = MutableStateFlow(0)
    val chineseConvert: StateFlow<Int> = _chineseConvert.asStateFlow()

    /** 设置面板据此决定是否显示简繁转换区。 */
    private val _appLanguage = MutableStateFlow("system")
    val appLanguage: StateFlow<String> = _appLanguage.asStateFlow()

    /**
     * 渲染用正文：只换字符，NovelDocument 的 startChar/endChar/textLength 与 fullText 保留原值——
     * 阅读进度与搜索锚点不随转换漂移。document 就绪时先发原文再发转换结果（否则转换期
     * stateIn 缓存 null 会落入空白态），结果按 (novelId, mode) 缓存。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
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

    private val _paragraphSpacing = MutableStateFlow(0.6f)
    val paragraphSpacing: StateFlow<Float> = _paragraphSpacing.asStateFlow()

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

    /** UI 据此决定初始翻页/滚动位置。 */
    private val _progressRestored = MutableStateFlow(false)
    val progressRestored: StateFlow<Boolean> = _progressRestored.asStateFlow()

    // ── 服务端状态（ToggleUiState 状态机：阅读书签 / 收藏 / 追更） ──
    private val _markState = MutableStateFlow(ToggleUiState.OFF)
    val markState: StateFlow<ToggleUiState> = _markState.asStateFlow()

    private val _bookmarkState = MutableStateFlow(ToggleUiState.OFF)
    val bookmarkState: StateFlow<ToggleUiState> = _bookmarkState.asStateFlow()

    private val _watchlistState = MutableStateFlow(ToggleUiState.OFF)
    val watchlistState: StateFlow<ToggleUiState> = _watchlistState.asStateFlow()

    private var loadJob: Job? = null
    private var saveJob: Job? = null
    private var markerJob: Job? = null

    init {
        collectPreferences()
        // 同步会话级小说收藏覆盖态：详情页或列表卡改收藏后，阅读器顶部菜单要实时跟随。
        viewModelScope.launch {
            favoriteActions.novelBookmarks
                .map { it[novelId] }
                .distinctUntilChanged()
                .collect { bookmarked ->
                    if (!_isLocalMode.value && bookmarked != null &&
                        !_bookmarkState.value.inFlight && _bookmarkState.value.isOn != bookmarked
                    ) {
                        _bookmarkState.value = if (bookmarked) ToggleUiState.ON else ToggleUiState.OFF
                    }
                }
        }
    }

    /**
     * 每个收集器在 [bindPref] 内独立处理读取异常，否则 DataStore 读取异常会在 viewModelScope
     * 里变成未捕获异常导致闪退。
     */
    private fun collectPreferences() {
        _fontSize.bindPref(userPreferences.readerFontSize)
        _lineHeight.bindPref(userPreferences.readerLineSpacing)
        _fontFamily.bindPref(userPreferences.readerFontFamily)
        _readerTheme.bindPref(userPreferences.readerTheme)
        _pageMode.bindPref(userPreferences.readerPageMode)
        _dualPageMode.bindPref(userPreferences.readerDualPageMode)
        _brightness.bindPref(userPreferences.readerBrightness)
        _followSystem.bindPref(userPreferences.readerFollowSystem)
        _customFontPath.bindPref(userPreferences.readerCustomFontPath)
        _fontWeight.bindPref(userPreferences.readerFontWeight)
        _paragraphIndent.bindPref(userPreferences.readerParagraphIndent)
        _paragraphSpacing.bindPref(userPreferences.readerParagraphSpacing)
        _letterSpacing.bindPref(userPreferences.readerLetterSpacing)
        _chineseConvert.bindPref(userPreferences.readerChineseConvert)
        _appLanguage.bindPref(userPreferences.appLanguage)
    }

    /** 订阅偏好流 → 内存态；异常就地处理，保留协程取消语义。 */
    private fun <T> MutableStateFlow<T>.bindPref(flow: Flow<T>) {
        viewModelScope.launch {
            readerResult { flow.collect { value = it } }
        }
    }

    /** 先同步写内存让 UI 即时响应，再异步落盘。 */
    private fun <T> MutableStateFlow<T>.persist(value: T, setter: suspend (T) -> Unit) {
        this.value = value
        // 落盘失败只丢持久化，当前会话偏好保留
        viewModelScope.launch { readerResult { setter(value) } }
    }

    fun load() {
        if (loadJob?.isActive == true || _progressRestored.value) return
        loadJob = viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                // 本地文档与进度恢复重试都复用已有正文
                if (_document.value != null) {
                    restoreProgress()
                    return@launch
                }
                if (_isLocalMode.value) return@launch
                // 缓存命中（阅读时预加载过 / 此前刚读过）：跳章秒开
                val cached = readerChapterCache.getChapter(novelId)
                Log.i(
                    TAG,
                    "load novel[$novelId]: 章节缓存命中=${cached != null}（命中则跳过网络/解析，图片仍由 UI 层加载）"
                )
                if (cached != null) {
                    applyLoaded(cached.novel, cached.document)
                } else {
                    val (detail, document) = contentLoader.load(novelId).getOrThrow()
                    currentCoroutineContext().ensureActive()
                    if (detail == null) return@launch
                    readerChapterCache.putChapter(novelId, ReaderChapterCache.Entry(detail, document))
                    applyLoaded(detail, document)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "load reader failed", e)
                _error.value = loadFailureMessage(
                    e,
                    R.string.reader_error_load_failed_reason,
                    R.string.reader_error_load_failed,
                )
            } finally {
                // 已切换分支时，加载标志由新任务负责。
                if (isActive) _isLoading.value = false
            }
        }
    }

    private suspend fun applyLoaded(detail: Novel, document: NovelDocument) {
        _novel.value = detail
        _document.value = document
        // 目录构建含系列小说列表的网络请求，放到 IO 之外异步执行
        viewModelScope.launch { buildToc() }
        loadServerState()
        restoreProgress()
    }

    /** 在线章节缺本地记录时可回退官方书签。 */
    private suspend fun restoreProgress() {
        val document = _document.value ?: return
        // 快速离开再进入时，等待上一阅读器正在提交的进度。
        val local = progressWriteMutex.withLock { readingProgressDao.getByNovel(novelId) }
        val offset = when {
            seekToEndRequested -> document.textLength
            local != null -> local.charOffset.coerceIn(0, document.textLength)
            _isLocalMode.value -> 0
            else -> estimateOffsetFromServerMarker()
        }
        currentCoroutineContext().ensureActive()
        // 先发布偏移和百分比，随后放行首次定位。
        _charOffset.value = offset
        _percentage.value = document.percentageAt(offset)
        _progressRestored.value = true
    }

    /** 系列上一章「尾页」进入时置位（reader/{id}?toEnd=true）。 */
    private var seekToEndRequested = false

    fun seekToEnd() {
        if (!_progressRestored.value) seekToEndRequested = true
    }

    /** 书签缺失或读取失败时返回 0。 */
    private suspend fun estimateOffsetFromServerMarker(): Int {
        val document = _document.value ?: return 0
        return readerResult {
            // 官方页码只用于在线章节没有本地记录时的兜底定位。
            val marker = pixivRepository.api.getNovelMarkers().marked_novels
                .firstOrNull { it.novel?.id == novelId }?.novel_marker ?: return 0
            _markState.value = ToggleUiState.ON
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
                // 优先保留当前会话中的操作结果，其次回退接口详情自带的收藏状态并写入覆盖流。
                val initialBookmarked = favoriteActions.novelBookmarks.value[novelId]
                    ?: (_novel.value?.is_bookmarked == true)
                _bookmarkState.value = if (initialBookmarked) ToggleUiState.ON else ToggleUiState.OFF
                favoriteActions.setNovelBookmarkState(novelId, initialBookmarked)
                val seriesId = _novel.value?.series?.id ?: return@launch
                val resp = pixivRepository.api.getNovelSeries(seriesId)
                _watchlistState.value =
                    if (resp.novel_series_detail?.watchlist_added == true) ToggleUiState.ON
                    else ToggleUiState.OFF
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "loadServerState failed", e)
            }
        }
    }

    // ── 进度上报（由 UI 在翻页/滚动时调用） ──

    fun reportPage(startChar: Int) {
        if (!_progressRestored.value) return // 恢复完成前忽略 UI 上报
        val document = _document.value ?: return
        val offset = startChar.coerceIn(0, document.textLength)
        _charOffset.value = offset
        _percentage.value = document.percentageAt(offset)
        scheduleProgressSave()
        scheduleMarkerSync(offset)
    }

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
            progressSnapshot()?.let { saveProgress(it) }
        }
    }

    /** 尚未恢复时返回 null：此时不得用默认偏移覆盖旧记录。 */
    private fun progressSnapshot(): ReadingProgressEntity? {
        // 退出或销毁时可能仍在读 Room
        if (!_progressRestored.value) return null
        val detail = _novel.value
        return ReadingProgressEntity(
            novelId = novelId,
            seriesId = detail?.series?.id ?: 0L,
            title = detail?.title,
            coverUrl = detail?.image_urls?.medium ?: detail?.image_urls?.square_medium,
            chapterOrder = 0,
            charOffset = _charOffset.value,
            percentage = _percentage.value,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private suspend fun saveProgress(snapshot: ReadingProgressEntity) {
        readerResult {
            progressWriteMutex.withLock {
                // 防抖任务和离场任务均从主线程登记，避免旧快照在离场写入之后提交。
                readingProgressDao.upsert(snapshot)
            }
        }.onFailure { Log.w(TAG, "saveProgress failed", it) }
    }

    /** 交给进程级协程写入 Room，不随 ViewModel 销毁而取消。 */
    fun flushProgress() {
        saveJob?.cancel()
        // 主线程截取快照并立即登记到锁，保证顺序
        val snapshot = progressSnapshot() ?: return
        progressWriteScope.launch(start = CoroutineStart.UNDISPATCHED) {
            saveProgress(snapshot)
        }
        saveJob = null
    }

    /** 按字符比例低频同步在线章节的官方书签。 */
    private fun scheduleMarkerSync(offset: Int) {
        if (_isLocalMode.value) return
        val pageCount = _novel.value?.page_count ?: 0
        if (pageCount <= 0) return
        markerJob?.cancel()
        markerJob = viewModelScope.launch {
            delay(3000.milliseconds)
            val document = _document.value ?: return@launch
            // 按字符比例换算官方页码（与书签换算共用同一纯函数；pageCount 已保证 >= 1）
            val page = estimateOfficialPage(
                charOffset = offset,
                textLength = document.textLength,
                officialPageCount = pageCount,
            )
            readerResult { pixivRepository.api.addNovelMarker(novelId, page) }
        }
    }

    // ── 阅读偏好写入 ──

    fun onFontSizeChange(value: Float) {
        _fontSize.persist(value) { userPreferences.setReaderFontSize(it) }
    }

    fun onLineHeightChange(value: Float) {
        _lineHeight.persist(value) { userPreferences.setReaderLineSpacing(it) }
    }

    fun onFontFamilyChange(value: String) {
        _fontFamily.persist(value) { userPreferences.setReaderFontFamily(it) }
    }

    fun onFontWeightChange(value: Int) {
        _fontWeight.persist(value) { userPreferences.setReaderFontWeight(it) }
    }

    fun onParagraphIndentChange(value: Int) {
        _paragraphIndent.persist(value) { userPreferences.setReaderParagraphIndent(it) }
    }

    fun onParagraphSpacingChange(value: Float) {
        _paragraphSpacing.persist(value) { userPreferences.setReaderParagraphSpacing(it) }
    }

    fun onLetterSpacingChange(value: Float) {
        _letterSpacing.persist(value) { userPreferences.setReaderLetterSpacing(it) }
    }

    fun onChineseConvertChange(value: Int) {
        _chineseConvert.persist(value) { userPreferences.setReaderChineseConvert(it) }
    }

    /** 文档实例 + 模式 → 转换后文档：VM 存活期内避免重复全章转换。 */
    private val convertCache = HashMap<Pair<Int, Int>, NovelDocument>()

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
     * 只转换 Paragraph/Heading/Quote 的 text，Image/Separator 原样；startChar/endChar/textLength/fullText
     * 保留原始值——阅读进度与全文搜索锚点不随转换漂移。
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
        _readerTheme.persist(value) { userPreferences.setReaderTheme(it) }
    }

    fun onPageModeChange(value: ReaderPageMode) {
        _pageMode.persist(value) { userPreferences.setReaderPageMode(it) }
    }

    fun onDualPageModeChange(value: ReaderDualPageMode) {
        _dualPageMode.persist(value) { userPreferences.setReaderDualPageMode(it) }
    }

    fun onBrightnessChange(value: Float) {
        _brightness.persist(value) { userPreferences.setReaderBrightness(it) }
    }

    // ── 目录 / 搜索 ──

    /**
     * 系列小说展示系列内各本，非系列只显示本小说；系列列表优先走缓存，目录就绪后预加载相邻章。
     */
    private suspend fun buildToc() {
        val detail = _novel.value
        val series = detail?.series
        if (series == null) {
            _toc.value = listOf(
                ReaderTocItem(
                    detail?.title ?: context.getString(R.string.reader_toc_current_novel),
                    detail?.id ?: 0L,
                    0
                ),
            )
            return
        }
        val seriesId = series.id
        readerChapterCache.getToc(seriesId)?.let { cached ->
            _toc.value = cached.map {
                ReaderTocItem(
                    it.title ?: context.getString(R.string.reader_untitled),
                    it.id,
                    0
                )
            }
            preloadNeighborChapters()
            return
        }
        _tocLoading.value = true
        try {
            val novels = fetchAllSeriesChapters(pixivRepository, seriesId)
            readerChapterCache.putToc(seriesId, novels)
            _toc.value = novels.map {
                ReaderTocItem(
                    it.title ?: context.getString(R.string.reader_untitled),
                    it.id,
                    0
                )
            }
            preloadNeighborChapters()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "buildToc series failed", e)
            _toc.value = emptyList()
        } finally {
            _tocLoading.value = false
        }
    }

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
                            currentCoroutineContext().ensureActive()
                            if (detail != null) {
                                readerChapterCache.putChapter(
                                    id,
                                    ReaderChapterCache.Entry(detail, document)
                                )
                                Log.d(TAG, "preload neighbor chapter $id cached")
                            }
                        }
                        .onFailure { e ->
                            if (e is CancellationException) throw e
                            Log.w(TAG, "preload neighbor chapter $id failed", e)
                        }
                }
            }
    }

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

    /** 先写临时文件再原子替换，避免复制中断留下损坏的字体文件。 */
    fun importCustomFont(uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                readerResult {
                    val dir = File(context.filesDir, "fonts").apply { mkdirs() }
                    val temp = File(dir, "custom_font.tmp")
                    val dest = File(dir, "custom_font.ttf")
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            temp.outputStream().use { input.copyTo(it) }
                        } ?: throw IllegalStateException(
                            context.getString(R.string.reader_error_font_read_failed),
                        )
                        if (!temp.renameTo(dest)) {
                            temp.copyTo(dest, overwrite = true)
                            temp.delete()
                        }
                        dest.absolutePath
                    } catch (e: CancellationException) {
                        temp.delete()
                        throw e
                    } catch (e: Exception) {
                        temp.delete()
                        throw e
                    }
                }
            }
            path.onSuccess { p ->
                _customFontPath.value = p
                readerResult { userPreferences.setReaderCustomFontPath(p) }
                sendMessage(UiMessage(R.string.reader_msg_font_set))
            }.onFailure {
                Log.w(TAG, "importCustomFont failed", it)
                sendMessage(UiMessage(
                    R.string.reader_msg_font_import_failed,
                    listOf(it.message ?: "")
                ))
            }
        }
    }

    fun clearCustomFont() {
        _customFontPath.persist("") { userPreferences.setReaderCustomFontPath(it) }
        trySendMessage(UiMessage(R.string.reader_msg_font_cleared))
    }

    fun onFollowSystemChange(value: Boolean) {
        _followSystem.persist(value) { userPreferences.setReaderFollowSystem(it) }
    }

    // ── 阅读书签 / 收藏 / 追更（ToggleUiState 状态机驱动） ──

    /** 添加时需按当前阅读位置换算官方页码。 */
    fun toggleMark() {
        if (_isLocalMode.value) return
        runToggle(
            _markState,
            R.string.reader_msg_mark_added,
            R.string.reader_msg_mark_removed,
        ) { target ->
            readerResult {
                if (target) {
                    val offset = _charOffset.value
                    val pageCount = (_novel.value?.page_count ?: 1).coerceAtLeast(1)
                    val page = estimateOfficialPage(
                        charOffset = offset,
                        textLength = (_document.value?.textLength ?: 1).coerceAtLeast(1),
                        officialPageCount = pageCount,
                    )
                    pixivRepository.api.addNovelMarker(novelId, page)
                } else {
                    pixivRepository.api.removeNovelMarker(novelId)
                }
            }
        }
    }

    /** 经 FavoriteActions 统一收口，断网自动入队。 */
    fun toggleBookmark() {
        if (_isLocalMode.value || novelId <= 0L) return
        runToggle(
            _bookmarkState,
            CoreR.string.core_msg_bookmarked,
            CoreR.string.core_msg_unbookmarked,
        ) { favoriteActions.toggleNovelFavorite(novelId, it) }
    }

    /** 不在系列内或本地模式时忽略。 */
    fun toggleWatchlist() {
        if (_isLocalMode.value) return
        val seriesId = _novel.value?.series?.id ?: run {
            trySendMessage(UiMessage(R.string.reader_msg_not_in_series))
            return
        }
        runToggle(
            _watchlistState,
            R.string.reader_msg_watching_added,
            R.string.reader_msg_watching_removed,
        ) { favoriteActions.toggleNovelWatchlist(seriesId, it) }
    }

    private companion object {
        const val TAG = "ReaderViewModel"
        val progressWriteScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val progressWriteMutex = Mutex()
    }
}

/** 常规异常转 Result.failure，协程取消照常上抛。 */
private inline fun <T> readerResult(action: () -> T): Result<T> = try {
    Result.success(action())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
