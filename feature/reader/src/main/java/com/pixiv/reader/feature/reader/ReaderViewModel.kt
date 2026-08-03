package com.pixiv.reader.feature.reader

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixivapi.model.Novel
import com.pixiv.reader.core.database.dao.ReadingProgressDao
import com.pixiv.reader.core.database.entity.ReadingProgressEntity
import com.pixiv.reader.core.datastore.UserPreferences
import com.pixiv.reader.core.network.session.PixivRepository
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

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ── 阅读偏好（DataStore 镜像到内存 StateFlow） ──
    private val _fontSize = MutableStateFlow(17f)
    val fontSize: StateFlow<Float> = _fontSize.asStateFlow()

    private val _lineHeight = MutableStateFlow(2.05f)
    val lineHeight: StateFlow<Float> = _lineHeight.asStateFlow()

    private val _fontFamily = MutableStateFlow("serif")
    val fontFamily: StateFlow<String> = _fontFamily.asStateFlow()

    private val _readerTheme = MutableStateFlow(1)
    val readerTheme: StateFlow<Int> = _readerTheme.asStateFlow()

    private val _pageMode = MutableStateFlow(0)
    val pageMode: StateFlow<Int> = _pageMode.asStateFlow()

    private val _brightness = MutableStateFlow(1f)
    val brightness: StateFlow<Float> = _brightness.asStateFlow()

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

    private val _message = Channel<String>(Channel.BUFFERED)
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
    }

    fun load() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                runCatching {
                    val detail = pixivRepository.api.getNovel(novelId).novel
                    val html = withContext(Dispatchers.IO) {
                        val raw = pixivRepository.api.getNovelHtml(novelId).string()
                        logNovelHtml(raw)
                        raw
                    }
                    val document = NovelParser.parse(html)
                    logParseResult(document)
                    detail to document
                }.onSuccess { (detail, document) ->
                    _novel.value = detail
                    _document.value = document
                    // 进度恢复异常不应当影响正文展示
                    runCatching { restoreProgress() }
                        .onFailure { e -> Log.w(TAG, "restoreProgress failed", e) }
                    loadServerState()
                }.onFailure {
                    Log.w(TAG, "load novel failed", it)
                    _error.value = it.message ?: "加载失败"
                }
            } catch (e: Exception) {
                Log.w(TAG, "load unexpected", e)
                _error.value = "加载失败：${e.message}"
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

    fun onReaderThemeChange(value: Int) {
        _readerTheme.value = value
        viewModelScope.launch { runCatching { userPreferences.setReaderTheme(value) } }
    }

    fun onPageModeChange(value: Int) {
        _pageMode.value = value
        viewModelScope.launch { runCatching { userPreferences.setReaderPageMode(value) } }
    }

    fun onBrightnessChange(value: Float) {
        _brightness.value = value
        viewModelScope.launch { runCatching { userPreferences.setReaderBrightness(value) } }
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
                _message.send(if (!current) "已添加阅读书签" else "已移除阅读书签")
            }.onFailure {
                _message.send("操作失败：${it.message}")
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
                _message.send(if (!current) "已收藏" else "已取消收藏")
            }.onFailure {
                _message.send("操作失败：${it.message}")
            }
        }
    }

    fun toggleWatchlist() {
        val seriesId = _novel.value?.series?.id ?: run {
            _message.trySend("该作品不属于系列")
            return
        }
        viewModelScope.launch {
            val current = _isWatchlisted.value
            runCatching {
                if (current) pixivRepository.api.removeWatchlistNovel(seriesId)
                else pixivRepository.api.addWatchlistNovel(seriesId)
            }.onSuccess {
                _isWatchlisted.value = !current
                _message.send(if (!current) "已加入追更" else "已取消追更")
            }.onFailure {
                _message.send("操作失败：${it.message}")
            }
        }
    }

    private companion object {
        const val TAG = "ReaderViewModel"
    }
}
