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

    /** 当前阅读是否为本地文件（下载管理解析 TXT/EPUB/MD 进入），UI 顶部显示「本地」徽标。 */
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    /** 是否本地文件阅读模式（TXT/EPUB 解析后直接渲染，跳过网络/离线加载）。 */
    private val _isLocalMode = MutableStateFlow(false)

    /**
     * 直接使用下载管理已解析的本地文档，并恢复本地阅读进度。
     *
     * @param document 已解析的 TXT、EPUB 或 Markdown 文档
     * @param title 本地文档标题
     * @return 无返回值
     */
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

    /** 行距增量（em，-1.0..1.0）；实际行高倍数 = 1.6 + 增量，默认 2.05 倍 → 0.45 */
    private val _lineHeight = MutableStateFlow(0.45f)
    val lineHeight: StateFlow<Float> = _lineHeight.asStateFlow()

    private val _fontFamily = MutableStateFlow("serif")
    val fontFamily: StateFlow<String> = _fontFamily.asStateFlow()

    private val _readerTheme = MutableStateFlow(ReaderThemeMode.PAPER)
    val readerTheme: StateFlow<ReaderThemeMode> = _readerTheme.asStateFlow()

    private val _pageMode = MutableStateFlow(ReaderPageMode.SCROLL)
    val pageMode: StateFlow<ReaderPageMode> = _pageMode.asStateFlow()

    /** 双页显示模式：关闭 / 强制开启 / 仅横屏 / 横屏或平板（仅翻页与仿真模式生效） */
    private val _dualPageMode = MutableStateFlow(ReaderDualPageMode.LANDSCAPE_OR_TABLET)
    val dualPageMode: StateFlow<ReaderDualPageMode> = _dualPageMode.asStateFlow()

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

    // ── 服务端状态（ToggleUiState 状态机：阅读书签 / 收藏 / 追更） ──
    /** 阅读书签状态机（服务端 marker 初始化；[toggleMark] 驱动转移）。 */
    private val _markState = MutableStateFlow(ToggleUiState.OFF)
    val markState: StateFlow<ToggleUiState> = _markState.asStateFlow()

    /** 收藏状态机（详情 is_bookmarked 初始化；[toggleBookmark] 驱动转移）。 */
    private val _bookmarkState = MutableStateFlow(ToggleUiState.OFF)
    val bookmarkState: StateFlow<ToggleUiState> = _bookmarkState.asStateFlow()

    /** 追更状态机（getNovelSeries 的 watchlist_added 初始化；[toggleWatchlist] 驱动转移）。 */
    private val _watchlistState = MutableStateFlow(ToggleUiState.OFF)
    val watchlistState: StateFlow<ToggleUiState> = _watchlistState.asStateFlow()


    private var loadJob: Job? = null
    private var saveJob: Job? = null
    private var markerJob: Job? = null

    init {
        collectPreferences()
        // 同步会话级小说收藏覆盖态，确保详情页或列表卡修改收藏后阅读器顶部菜单实时更新。
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
     * 收集阅读偏好。每个收集器在 [bindPref] 内独立处理读取异常，
     * 避免 DataStore 读取异常在 viewModelScope 里变成未捕获异常导致闪退。
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

    /**
     * 订阅偏好流 → 内存态（DataStore 变更驱动 UI）。
     * 收集器独立处理 DataStore 读取异常，并保留协程取消语义。
     *
     * @param flow DataStore 偏好流
     * @return 无返回值（收集协程挂在 viewModelScope，随 ViewModel 销毁结束）
     */
    private fun <T> MutableStateFlow<T>.bindPref(flow: Flow<T>) {
        viewModelScope.launch {
            readerResult { flow.collect { value = it } }
        }
    }

    /**
     * 写内存态并持久化到 DataStore（先同步写内存让 UI 即时响应，再异步落盘）。
     *
     * @param value 新偏好值
     * @param setter DataStore 持久化挂起函数
     * @return 无返回值（持久化协程挂在 viewModelScope）
     */
    private fun <T> MutableStateFlow<T>.persist(value: T, setter: suspend (T) -> Unit) {
        this.value = value
        // 落盘失败保留当前会话偏好，页面销毁时允许取消写入。
        viewModelScope.launch { readerResult { setter(value) } }
    }

    /**
     * 按当前阅读方式加载正文或重试进度恢复。
     *
     * @return 无返回值
     */
    fun load() {
        if (loadJob?.isActive == true || _progressRestored.value) return
        loadJob = viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                // 本地文档及进度恢复重试均复用已有正文，无需获取在线章节。
                if (_document.value != null) {
                    restoreProgress()
                    return@launch
                }
                if (_isLocalMode.value) return@launch
                // 缓存命中（阅读时预加载过 / 此前刚读过）：跳过网络与重新解析，跳章秒开
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

    /**
     * 发布在线正文，初始化目录和服务端状态，并恢复阅读进度。
     *
     * @param detail 在线作品详情
     * @param document 在线正文
     * @return 无返回值
     * @throws CancellationException 所属加载任务被取消
     */
    private suspend fun applyLoaded(detail: Novel, document: NovelDocument) {
        _novel.value = detail
        _document.value = document
        // 目录构建含系列小说列表的网络请求，放到 IO 之外异步执行
        viewModelScope.launch { buildToc() }
        loadServerState()
        restoreProgress()
    }

    /**
     * 查询 Room 并确定首次定位。在线章节缺少本地记录时可回退官方书签。
     *
     * @return 无返回值
     * @throws CancellationException 所属加载任务被取消
     */
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

    /** 是否请求了定位到文档末尾（reader/{id}?toEnd=true，系列上一章「尾页」进入）。 */
    private var seekToEndRequested = false

    /**
     * 在首次进度恢复时定位到上一章的末尾。
     *
     * @return 无返回值
     */
    fun seekToEnd() {
        if (!_progressRestored.value) seekToEndRequested = true
    }

    /**
     * 从官方书签估算在线章节的阅读位置。
     *
     * @return 字符偏移，书签缺失或读取失败时为 0
     * @throws CancellationException 官方书签查询被取消
     */
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

    /**
     * 初始化在线章节的收藏与系列追更状态。
     *
     * @return 无返回值
     */
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

    /** 翻页模式：页码变化 → 上报页首字符偏移。 */
    fun reportPage(startChar: Int) {
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

    /**
     * 合并短时间内的进度变动，在停止翻页或滚动后保存。
     *
     * @return 无返回值
     */
    private fun scheduleProgressSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(800.milliseconds)
            progressSnapshot()?.let { saveProgress(it) }
        }
    }

    /**
     * 同步取得已恢复的阅读进度快照，供后台保存。
     *
     * @return 当前进度快照，尚未恢复时返回 null
     */
    private fun progressSnapshot(): ReadingProgressEntity? {
        // 退出或销毁时可能仍在读 Room，此时不得用默认偏移覆盖旧记录。
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

    /**
     * 按登记顺序将快照写入 Room。
     *
     * @param snapshot 待保存的进度快照
     * @return 无返回值
     * @throws CancellationException 防抖写入被取消
     */
    private suspend fun saveProgress(snapshot: ReadingProgressEntity) {
        readerResult {
            progressWriteMutex.withLock {
                // 防抖任务和离场任务均从主线程登记，避免旧快照在离场写入之后提交。
                readingProgressDao.upsert(snapshot)
            }
        }.onFailure { Log.w(TAG, "saveProgress failed", it) }
    }

    /**
     * 离场时捕获末次进度，交给进程级协程写入 Room。
     *
     * @return 无返回值
     */
    fun flushProgress() {
        saveJob?.cancel()
        // 在主线程截取快照并立即登记到锁，Room 挂起写入不随 ViewModel 销毁而取消。
        val snapshot = progressSnapshot() ?: return
        progressWriteScope.launch(start = CoroutineStart.UNDISPATCHED) {
            saveProgress(snapshot)
        }
        saveJob = null
    }

    /**
     * 按字符比例低频同步在线章节的官方书签。
     *
     * @param offset 当前阅读字符偏移
     * @return 无返回值
     */
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

    /** 简繁转换切换：0 关闭 / 1 简体→繁体 / 2 繁体→简体。 */
    fun onChineseConvertChange(value: Int) {
        _chineseConvert.persist(value) { userPreferences.setReaderChineseConvert(it) }
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
     * 构建目录：系列小说展示**系列内各本小说**；非系列小说只显示本小说一条。
     * 系列列表优先走缓存（跳章后新阅读器立即显示目录并高亮新位置）；
     * 目录就绪后预加载下一章（阅读时缓存，跳下一章秒开）。
     *
     * @return 无返回值
     * @throws CancellationException 目录请求被取消
     */
    private suspend fun buildToc() {
        val detail = _novel.value
        val series = detail?.series
        if (series == null) {
            // 非系列：目录只显示本小说
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

    /**
     * 预加载尚未缓存的相邻章节，减少翻到上一章或下一章时的等待。
     *
     * @return 无返回值
     */
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

    /**
     * 在 IO 调度器上先写入临时文件再原子替换目标字体文件，并设为自定义阅读字体。
     *
     * @param uri 文件选择器返回的字体地址
     * @return 无返回值
     */
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
                        // 先写入临时文件并校验替换，避免复制中断留下损坏字体文件。
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
                // 字体文件复制完成后再应用并保存路径。
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

    /**
     * 添加 / 移除阅读书签（[ToggleUiState] 状态机：进行中防连点，失败回滚）：
     * 添加时按当前阅读位置换算官方页码。
     *
     * @return 无返回值（操作完成后结束的协程）
     */
    fun toggleMark() {
        if (_isLocalMode.value) return
        runToggle(
            _markState,
            R.string.reader_msg_mark_added,
            R.string.reader_msg_mark_removed,
        ) { target ->
            readerResult {
                // 添加书签需要将当前字符位置换算成官方页码。
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

    /**
     * 收藏 / 取消收藏小说（[ToggleUiState] 状态机：进行中防连点，失败回滚；
     * 经 FavoriteActions 统一收口，断网自动入队）。
     *
     * @return 无返回值（操作完成后结束的协程）
     */
    fun toggleBookmark() {
        if (_isLocalMode.value || novelId <= 0L) return
        runToggle(
            _bookmarkState,
            CoreR.string.core_msg_bookmarked,
            CoreR.string.core_msg_unbookmarked,
        ) { favoriteActions.toggleNovelFavorite(novelId, it) }
    }

    /**
     * 追更 / 取消追更（[ToggleUiState] 状态机：进行中防连点，失败回滚；
     * 经 FavoriteActions 统一收口，断网自动入队）。不在系列内或本地模式时忽略。
     *
     * @return 无返回值（操作完成后结束的协程）
     */
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

/**
 * 将常规异常转换为失败结果，并继续传播协程取消。
 *
 * @param action 待执行的读取、写入或网络操作
 * @return 操作结果
 * @throws CancellationException 操作被取消
 */
private inline fun <T> readerResult(action: () -> T): Result<T> = try {
    Result.success(action())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}
