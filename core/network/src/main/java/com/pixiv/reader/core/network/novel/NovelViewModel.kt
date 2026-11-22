package com.pixiv.reader.core.network.novel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.common.ToggleUiState
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.common.loadFailureMessage
import com.pixiv.reader.core.common.R as CoreR
import com.pixiv.reader.core.common.model.toCardData
import com.pixiv.reader.core.database.dao.BrowseHistoryDao
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.dao.ReadingProgressDao
import com.pixiv.reader.core.database.entity.BrowseHistoryEntity
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.database.entity.ReadingProgressEntity
import com.pixiv.reader.core.network.download.DownloadQueue
import com.pixiv.reader.core.network.favorite.BookmarkEditor
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.message.MessageViewModel
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 小说详情 ViewModel：详情 / 系列章节 / 阅读进度 / 收藏 / 追更。
 * 评论已独立（详情页不加载评论）。导出触发经 [exportRequest] 回调交 feature 层处理
 * （core 不依赖 feature 的 NovelExportWorker/Format）。
 *
 * 排行右栏复用：无路由参数时 init 不预载，等 [switchTo] 换作品加载。
 */
@HiltViewModel
class NovelViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val pixivRepository: PixivRepository,
    private val readingProgressDao: ReadingProgressDao,
    private val browseHistoryDao: BrowseHistoryDao,
    private val downloadEntryDao: DownloadEntryDao,
    private val favoriteActions: FavoriteActions,
) : MessageViewModel() {

    private val novelId: Long = savedStateHandle.get<Long>("novelId") ?: 0L

    private val _novel = MutableStateFlow<Novel?>(null)
    val novel: StateFlow<Novel?> = _novel.asStateFlow()

    /** 系列详情（含章节列表） */
    private val _seriesNovels = MutableStateFlow<List<Novel>>(emptyList())
    val seriesNovels: StateFlow<List<Novel>> = _seriesNovels.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error.asStateFlow()

    private val _progress = MutableStateFlow<ReadingProgressEntity?>(null)
    val progress: StateFlow<ReadingProgressEntity?> = _progress.asStateFlow()

    /** 收藏状态机（一键收藏；详情 is_bookmarked 初始化，[toggleBookmark] 驱动转移）。 */
    private val _bookmarkState = MutableStateFlow(ToggleUiState.OFF)
    val bookmarkState: StateFlow<ToggleUiState> = _bookmarkState.asStateFlow()

    /** 收藏编辑器保存进行中（防连点 + 弹层确认按钮禁用；一键收藏走 [_bookmarkState] 独立防连点）。 */
    private val _isEditorSaving = MutableStateFlow(false)
    val isEditorSaving: StateFlow<Boolean> = _isEditorSaving.asStateFlow()

    /** 所属系列追更状态机（getNovelSeries 的 watchlist_added 初始化；详情页追更按钮）。 */
    private val _watchlistState = MutableStateFlow(ToggleUiState.OFF)
    val watchlistState: StateFlow<ToggleUiState> = _watchlistState.asStateFlow()

    /** 作者关注状态机（详情页作者名旁关注按钮；user/detail 权威刷新）。 */
    private val _authorFollowState = MutableStateFlow(ToggleUiState.OFF)
    val authorFollowState: StateFlow<ToggleUiState> = _authorFollowState.asStateFlow()

    /** 下载/导出进行中 */
    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading.asStateFlow()

    /** 下载/导出进度文案（如"正在下载第 3/12 章…"） */
    private val _downloadProgress = MutableStateFlow<String?>(null)
    val downloadProgress: StateFlow<String?> = _downloadProgress.asStateFlow()

    /**
     * 导出请求挂起回调（feature 层接线：先落库 pending 再以唯一任务入队 WorkManager）。
     * core 只转发触发参数并管理导出态与错误提示，不依赖 feature 导出实现。
     */
    var exportRequest: (suspend (novelId: Long, seriesId: Long?, formatName: String) -> Unit)? = null

    /**
     * 写入「待同步」下载索引条目（挂起等待落库完成，保证 Worker 启动前索引已存在）。
     *
     * @param entry 待入队的索引条目（status 强制置 pending）
     * @return 无返回值
     */
    suspend fun markDownloadPending(entry: DownloadEntryEntity) {
        DownloadQueue.markPending(downloadEntryDao, entry)
    }

    /** 收藏编辑器（公开/私密 + 标签收藏）：详情加载回显当前设置，弹层保存。 */
    val bookmarkEditor = BookmarkEditor(
        scope = viewModelScope,
        api = pixivRepository.api,
        targetType = "novel",
        targetId = { _novel.value?.id ?: novelId },
        uid = { pixivRepository.pixivApi.session.loggedInUid },
    )

    private var exportObserverJob: Job? = null

    init {
        // 同步跨页小说收藏与作者关注覆写流，保证卡片与详情双向一致。
        viewModelScope.launch {
            combine(_novel, favoriteActions.novelBookmarks) { current, map ->
                val targetId = current?.id ?: 0L
                targetId to map[targetId]
            }.distinctUntilChanged().collect { (targetId, bookmarked) ->
                if (targetId <= 0L || bookmarked == null) return@collect
                if (!_bookmarkState.value.inFlight && _bookmarkState.value.isOn != bookmarked) {
                    _bookmarkState.value = if (bookmarked) ToggleUiState.ON else ToggleUiState.OFF
                    bookmarkEditor.onTargetLoaded(bookmarked)
                }
            }
        }
        viewModelScope.launch {
            combine(_novel, favoriteActions.followedUsers) { current, map ->
                current?.user?.id?.let { uid -> uid to map[uid] }
            }.distinctUntilChanged().collect { pair ->
                val (_, followed) = pair ?: return@collect
                if (followed != null && !_authorFollowState.value.inFlight && _authorFollowState.value.isOn != followed) {
                    _authorFollowState.value = if (followed) ToggleUiState.ON else ToggleUiState.OFF
                }
            }
        }
        // 详情路由必有 id；排行右栏（无路由参数）不预载，等 switchTo
        if (novelId > 0L) load()
    }

    /**
     * 切换到另一小说（排行右栏选中项变化时调用）。
     * 清空旧作品全部状态后重新加载；加载期间旧内容先清空避免错位。
     */
    fun switchTo(id: Long) {
        // 与当前展示中的小说比较去重（内嵌场景 novelId=0，比较展示 id 而非路由参数）
        val currentShown = _novel.value?.id ?: novelId
        if (id == currentShown || id <= 0L) return
        exportObserverJob?.cancel()
        _novel.value = null
        _seriesNovels.value = emptyList()
        _error.value = null
        // 三对开关状态机 + 编辑器保存标志整体复位（成对生命周期：一条流清一条）
        _bookmarkState.value = ToggleUiState.OFF
        _isEditorSaving.value = false
        bookmarkEditor.onTargetLoaded(false)
        _watchlistState.value = ToggleUiState.OFF
        _authorFollowState.value = ToggleUiState.OFF
        _downloading.value = false
        _downloadProgress.value = null
        load(id)
    }

    /** 当前请求中的小说 id（响应竞态守卫：右栏快速切换后旧响应直接丢弃）。 */
    private var requestedId: Long = novelId

    fun load(id: Long = novelId) {
        requestedId = id
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val resp = pixivRepository.api.getNovel(id)
                // 响应到达时已切到其他小说：丢弃（旧数据不覆盖新目标状态）
                if (requestedId != id) return@launch
                val detail = resp.novel
                if (detail == null) {
                    _error.value = UiMessage(CoreR.string.core_novel_load_failed)
                    return@launch
                }
                _novel.value = detail
                val bookmarked = detail.is_bookmarked == true
                _bookmarkState.value = if (bookmarked) ToggleUiState.ON else ToggleUiState.OFF
                favoriteActions.setNovelBookmarkState(detail.id, bookmarked)
                bookmarkEditor.onTargetLoaded(bookmarked)
                // 详情内嵌 user.is_followed 可能缺失，用 user/detail 权威刷新关注态（失败保留内嵌值）
                _authorFollowState.value = if (detail.user?.is_followed == true) ToggleUiState.ON else ToggleUiState.OFF
                detail.user?.id?.let { loadAuthorFollowState(it, id) }
                recordHistory(detail)
                loadProgress(id)
                loadSeries(detail)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestedId != id) return@launch
                _error.value = loadFailureMessage(
                    e,
                    CoreR.string.core_novel_load_failed_reason,
                    CoreR.string.core_novel_load_failed,
                )
            } finally {
                if (requestedId == id) _isLoading.value = false
            }
        }
    }

    /** 打开详情时原子写入浏览历史（先删旧再插实现去重置顶；payloadJson 存完整卡片数据供历史页展示）。 */
    private fun recordHistory(detail: Novel) {
        viewModelScope.launch {
            runCatching {
                val card = detail.toCardData()
                browseHistoryDao.replaceTop(
                    BrowseHistoryEntity(
                        targetType = "novel",
                        targetId = detail.id,
                        title = detail.title,
                        coverUrl = detail.image_urls?.medium ?: detail.image_urls?.square_medium,
                        payloadJson = Gson().toJson(card),
                    ),
                )
            }
        }
    }

    private suspend fun loadProgress(id: Long) {
        _progress.value = readingProgressDao.getByNovel(id)
    }

    private fun loadSeries(detail: Novel) {
        val seriesId = detail.series?.id ?: return
        val expectedNovelId = detail.id
        viewModelScope.launch {
            runCatching { pixivRepository.api.getNovelSeries(seriesId) }
                .onSuccess { resp ->
                    if ((_novel.value?.id ?: requestedId) != expectedNovelId) return@onSuccess
                    _seriesNovels.value = resp.novels.orEmpty()
                    _watchlistState.value =
                        if (resp.novel_series_detail?.watchlist_added == true) ToggleUiState.ON
                        else ToggleUiState.OFF
                }
        }
    }

    /**
     * 收藏 / 取消收藏小说（[ToggleUiState] 状态机：进行中保留旧文案禁用防连点，失败回滚；
     * 经 FavoriteActions 统一收口，断网自动入队）。id 与 [bookmarkEditor] 同源取自当前展示小说
     * （排行右栏内嵌场景路由 novelId=0）。
     */
    fun toggleBookmark() {
        val id = _novel.value?.id ?: novelId
        runToggle(
            _bookmarkState,
            CoreR.string.core_msg_bookmarked,
            CoreR.string.core_msg_unbookmarked,
        ) { target ->
            favoriteActions.toggleNovelFavorite(id, target).onSuccess {
                bookmarkEditor.onTargetLoaded(target)
            }
        }
    }

    /**
     * 收藏编辑器保存（公开/私密 + 标签收藏）：成功后刷新收藏态、关闭弹层并提示。
     * 期间以 [_isEditorSaving] 防连点（弹层确认按钮禁用同一标志）。
     */
    fun saveBookmarkEditor() {
        runActionNotified(
            _isEditorSaving,
            CoreR.string.core_msg_bookmark_updated,
            {
                _bookmarkState.value = ToggleUiState.ON
                favoriteActions.setNovelBookmarkState(_novel.value?.id ?: novelId, true)
                bookmarkEditor.close()
            },
        ) { bookmarkEditor.save() }
    }

    /**
     * 追更 / 取消追更系列（[ToggleUiState] 状态机：进行中保留旧文案禁用防连点，失败回滚；
     * 经 FavoriteActions 统一收口，断网自动入队）。
     *
     * @return 无返回值（操作完成后结束的协程）
     */
    fun toggleWatchlist() {
        val seriesId = _novel.value?.series?.id ?: return
        runToggle(
            _watchlistState,
            CoreR.string.core_msg_watching_added,
            CoreR.string.core_msg_watching_removed,
        ) { favoriteActions.toggleNovelWatchlist(seriesId, it) }
    }

    // ── 关注作者 ─────────────────────────────────────────────────────────────

    /** 用 user/detail 权威刷新作者关注态（best-effort，失败保留内嵌值，并校验目标未切换）。 */
    private fun loadAuthorFollowState(userId: Long, expectedNovelId: Long) {
        viewModelScope.launch {
            runCatching { pixivRepository.api.getUserDetail(userId) }
                .onSuccess { resp ->
                    if ((_novel.value?.id ?: requestedId) != expectedNovelId) return@onSuccess
                    resp.user?.is_followed?.let { followed ->
                        _authorFollowState.value = if (followed) ToggleUiState.ON else ToggleUiState.OFF
                        favoriteActions.setUserFollowState(userId, followed)
                    }
                }
        }
    }

    /** 关注 / 取关作者（详情页作者名旁按钮，[ToggleUiState] 状态机防连点；经 FavoriteActions 统一收口）。 */
    fun toggleFollowAuthor() {
        val userId = _novel.value?.user?.id ?: return
        runToggle(
            _authorFollowState,
            CoreR.string.core_msg_followed_author,
            CoreR.string.core_msg_unfollowed,
        ) { favoriteActions.toggleFollowUser(userId, it) }
    }

    // ── 下载 / 导出 ──────────────────────────────────────────────────────────

    /**
     * 导出小说（单本或整个系列）。先挂起等待 feature 层写好 pending 索引并入队唯一 Worker，
     * 随后按精确主键观察单条下载状态，避免历史其他格式的完成记录提前结束等待。
     *
     * @param formatName NovelExportFormat.name（"TXT"/"EPUB"/"PDF"/"MARKDOWN"/"DOCX"）
     * @param series true 表示导出所属整系列，false 表示仅导出当前单本
     * @return 无返回值
     */
    fun export(formatName: String, series: Boolean = false) {
        val detail = _novel.value ?: return
        if (_downloading.value) return
        val seriesId = if (series) detail.series?.id else null
        if (series && seriesId == null) return
        val requestHandler = exportRequest ?: return
        val scopeKey = if ((seriesId ?: 0L) > 0L) "series" else ""
        _downloading.value = true
        _downloadProgress.value = context.getString(CoreR.string.core_msg_export_queued)
        exportObserverJob?.cancel()
        exportObserverJob = viewModelScope.launch {
            try {
                requestHandler(detail.id, seriesId, formatName)
                trySendMessage(UiMessage(CoreR.string.core_msg_export_queued))
                observeExportStateReset(detail.id, formatName, scopeKey)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _downloading.value = false
                _downloadProgress.value = null
                trySendMessage(UiMessage(CoreR.string.core_msg_action_failed, listOf(e.message.orEmpty())))
            }
        }
    }

    /**
     * 按完整复合主键精确等待导出结束（done / failed / 条目被删除），随后复位导出中状态。
     *
     * @param id 小说 ID
     * @param format 导出格式名称
     * @param scopeKey 下载作用域（""=单本 / "series"=整系列）
     * @return 无返回值
     */
    private suspend fun observeExportStateReset(id: Long, format: String, scopeKey: String) {
        try {
            downloadEntryDao.observeStatus("novel", id, format, scopeKey).first { status ->
                status == null ||
                    status == DownloadEntryEntity.STATUS_DONE ||
                    status == DownloadEntryEntity.STATUS_FAILED
            }
        } finally {
            _downloading.value = false
            _downloadProgress.value = null
        }
    }
}
