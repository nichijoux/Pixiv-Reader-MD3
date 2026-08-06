package com.pixiv.reader.feature.reader

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.common.ReaderPageMode
import com.pixiv.reader.core.common.ReaderThemeMode
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.database.dao.ReadingProgressDao
import com.pixiv.reader.core.database.entity.ReadingProgressEntity
import com.pixiv.reader.core.datastore.UserPreferences
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.core.novel.NovelBlock
import com.pixiv.reader.core.novel.NovelDocument
import com.pixiv.reader.core.novel.NovelParser
import com.pixiv.reader.core.novel.percentageAt
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * 目录项。
 * [novelId] = -1 表示当前小说（页内按 [charOffset] 跳转）；
 * 否则为系列内目标小说（点击打开该本阅读器，[charOffset] 恒为 0）。
 */
data class ReaderTocItem(
    val title: String,
    val novelId: Long = -1,
    val charOffset: Int = 0,
)

/**
 * 阅读器 ViewModel。
 *
 * 职责：
 * - 加载小说详情 + 正文 HTML（NovelParser 解析）
 * - 收集阅读偏好（字号/行距/字体/主题/翻页/亮度）
 * - 字符级进度：本地 Room 落库（防抖）+ 官方 marker 同步
 * - 阅读书签（marker）/ 收藏 / 追更
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
    private val readingProgressDao: ReadingProgressDao,
    private val userPreferences: UserPreferences,
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

    private val _lineHeight = MutableStateFlow(2.05f)
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
            runCatching { userPreferences.readerLineHeight.collect { _lineHeight.value = it } }
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
    }

    fun load() {
        viewModelScope.launch {
            if (_isLocalMode.value) return@launch
            try {
                _isLoading.value = true
                _error.value = null
                _isOffline.value = false
                runCatching {
                    val detail = pixivRepository.api.getNovel(novelId).novel
                    val html = withContext(Dispatchers.IO) {
                        val raw = pixivRepository.api.getNovelHtml(novelId).string()
                        logNovelHtml(raw)
                        raw
                    }
                    // 网页小说详情：拿 textEmbeddedImages（正文嵌入图片映射，key 为 novelImageId）
                    val webNovel = runCatching {
                        pixivRepository.webApi.getNovelWeb(novelId).body
                    }.getOrNull()
                    val imageUrls = webNovel?.textEmbeddedImages
                        ?.mapNotNull { (file, info) ->
                            val urls = info?.urls
                            val url = urls?.get("1200x1200")
                                ?: urls?.get("480mw")
                                ?: urls?.get("240mw")
                                ?: urls?.get("original")
                                ?: info?.url
                            if (url.isNullOrBlank()) return@mapNotNull null
                            "uploadedimage:$file" to url
                        }
                        ?.toMap()
                        ?: emptyMap()
                    val document = withContext(Dispatchers.IO) {
                        // 解析 + [pixivimage:ID] 引用画作 → ajax/illust/{id} 解析首图 URL
                        resolvePixivImages(NovelParser.parse(html, imageUrls))
                    }
                    logParseResult(document)
                    detail to document
                }.onSuccess { (detail, document) ->
                    if (_isLocalMode.value) return@onSuccess
                    _novel.value = detail
                    _document.value = document
                    // 目录构建含系列小说列表的网络请求，放到 IO 之外异步执行
                    viewModelScope.launch { buildToc() }
                    // 进度恢复异常不应当影响正文展示
                    runCatching { restoreProgress() }
                        .onFailure { e -> Log.w(TAG, "restoreProgress failed", e) }
                    loadServerState()
                }.onFailure {
                    Log.w(TAG, "load novel failed", it)
                    _error.value = it.message?.let { m -> UiMessage(R.string.reader_error_load_failed_reason, listOf(m)) }
                        ?: UiMessage(R.string.reader_error_load_failed)
                }
            } catch (e: Exception) {
                Log.w(TAG, "load unexpected", e)
                _error.value = UiMessage(R.string.reader_error_load_failed_reason, listOf(e.message ?: ""))
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 调试：打印并保存原始 HTML，便于排查"没有正文内容"。
     * HTML 写入 cacheDir/novel_debug/{id}.html，可用 Android Studio Device Explorer 取出。
     */
    private fun logNovelHtml(html: String) {
        runCatching {
            Log.d(TAG, "novel[$novelId] html length=${html.length}")
            // 分段打印前 1200 字符（避免 logcat 单行截断）
            val preview = html.take(1200)
            Log.d(TAG, "novel[$novelId] html head:\n$preview")
            val dir = File(context.cacheDir, "novel_debug").apply { mkdirs() }
            val file = File(dir, "${novelId}.html")
            file.writeText(html)
            Log.d(TAG, "novel[$novelId] html saved to: ${file.absolutePath}")
        }.onFailure { Log.w(TAG, "logNovelHtml failed", it) }
    }

    /** 调试：打印解析结果（块数 / 全文长度 / 各块类型 / 全文开头）。 */
    private fun logParseResult(document: NovelDocument) {
        runCatching {
            Log.d(TAG, "parse result: blocks=${document.blocks.size}, textLength=${document.textLength}")
            if (document.blocks.isEmpty()) {
                Log.d(TAG, "parse produced NO blocks (fullText empty)")
            } else {
                val types = document.blocks.take(20).map { it::class.simpleName }
                Log.d(TAG, "first 20 block types: $types")
                Log.d(TAG, "fullText head: ${document.fullText.take(300)}")
            }
        }.onFailure { Log.w(TAG, "logParseResult failed", it) }
    }

    /**
     * 把正文中的 `[pixivimage:ID]` 标记解析为画作首图 URL。
     * 通过网页接口 `ajax/illust/{id}` 的 `urls.regular/original` 获取（带 Cookie，正常可访问）；
     * 解析失败的标记保留原文（渲染层按图片块显示但加载失败占位）。
     */
    private suspend fun resolvePixivImages(document: NovelDocument): NovelDocument {
        val pending = document.blocks
            .filterIsInstance<NovelBlock.Image>()
            .filter { it.url.startsWith("pixivimage:") }
        if (pending.isEmpty()) return document
        val resolved = mutableMapOf<String, String>()
        for (img in pending) {
            val id = img.url.removePrefix("pixivimage:").toLongOrNull() ?: continue
            val body = runCatching { pixivRepository.webApi.getWebIllust(id).body }.getOrNull() ?: continue
            val url = body.urls?.get("regular") ?: body.urls?.get("original")
            if (!url.isNullOrBlank()) resolved[img.url] = url
        }
        if (resolved.isEmpty()) return document
        val newBlocks = document.blocks.map { block ->
            if (block is NovelBlock.Image) {
                resolved[block.url]?.let { block.copy(url = it) } ?: block
            } else {
                block
            }
        }
        return NovelDocument(blocks = newBlocks, fullText = document.fullText, textLength = document.textLength)
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
        viewModelScope.launch { runCatching { userPreferences.setReaderLineHeight(value) } }
    }

    fun onFontFamilyChange(value: String) {
        _fontFamily.value = value
        viewModelScope.launch { runCatching { userPreferences.setReaderFontFamily(value) } }
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
     * 不再按当前小说的标题/长段落做"第 N 节"分章。
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
        _tocLoading.value = true
        try {
            val novels = fetchSeriesNovels(series.id)
            _toc.value = novels.map { ReaderTocItem(it.title ?: context.getString(R.string.reader_untitled), it.id, 0) }
        } catch (e: Exception) {
            Log.w(TAG, "buildToc series failed", e)
            _toc.value = emptyList()
        } finally {
            _tocLoading.value = false
        }
    }

    /** 拉取系列内全部小说（循环分页，防御最多 20 页）。 */
    private suspend fun fetchSeriesNovels(seriesId: Long): List<Novel> {
        val result = mutableListOf<Novel>()
        var lastOrder: Int? = null
        repeat(20) {
            val resp = pixivRepository.api.getNovelSeries(seriesId, lastOrder)
            resp.novels?.let { result.addAll(it) }
            val next = resp.next_url
            if (next.isNullOrBlank()) return result
            lastOrder = parseLastOrder(next)
            if (lastOrder == null) return result
        }
        return result
    }

    /** 从 next_url 解析 last_order 查询参数。 */
    private fun parseLastOrder(nextUrl: String?): Int? {
        if (nextUrl.isNullOrBlank()) return null
        return nextUrl.substringAfter('?', "").split('&')
            .firstOrNull { it.startsWith("last_order=") }
            ?.substringAfter('=')?.toIntOrNull()
    }

    /** 在全文（忽略大小写）中搜索关键词，记录所有匹配的字符偏移。 */
    fun searchText(query: String) {
        val text = _document.value?.fullText ?: return
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _searchIndex.value = -1
            return
        }
        val results = mutableListOf<Int>()
        var from = 0
        while (true) {
            val idx = text.indexOf(query, from, ignoreCase = true)
            if (idx < 0) break
            results.add(idx)
            from = idx + query.length
            if (results.size >= 500) break
        }
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
                } ?: throw IllegalStateException("无法读取字体文件")
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
            runCatching {
                if (current) pixivRepository.api.unbookmarkNovel(novelId)
                else pixivRepository.api.bookmarkNovel(novelId, "public", emptyList())
            }.onSuccess {
                _isBookmarked.value = !current
                _message.send(if (!current) UiMessage(R.string.reader_msg_bookmarked) else UiMessage(R.string.reader_msg_unbookmarked))
            }.onFailure {
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
