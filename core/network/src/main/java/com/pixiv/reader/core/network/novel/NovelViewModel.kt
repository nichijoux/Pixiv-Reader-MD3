package com.pixiv.reader.core.network.novel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.common.ExportState
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
 * 小说详情 ViewModel：详情 / 系列章节 / 阅读进度 / 收藏 / 追更（评论已独立）。
 * 导出经 [exportRequest] 回调交 feature 层（core 不依赖 feature 的 Worker/Format）。
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

    private val _seriesNovels = MutableStateFlow<List<Novel>>(emptyList())
    val seriesNovels: StateFlow<List<Novel>> = _seriesNovels.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error.asStateFlow()

    private val _progress = MutableStateFlow<ReadingProgressEntity?>(null)
    val progress: StateFlow<ReadingProgressEntity?> = _progress.asStateFlow()

    private val _bookmarkState = MutableStateFlow(ToggleUiState.OFF)
    val bookmarkState: StateFlow<ToggleUiState> = _bookmarkState.asStateFlow()

    private val _isEditorSaving = MutableStateFlow(false)
    val isEditorSaving: StateFlow<Boolean> = _isEditorSaving.asStateFlow()

    private val _watchlistState = MutableStateFlow(ToggleUiState.OFF)
    val watchlistState: StateFlow<ToggleUiState> = _watchlistState.asStateFlow()

    private val _authorFollowState = MutableStateFlow(ToggleUiState.OFF)
    val authorFollowState: StateFlow<ToggleUiState> = _authorFollowState.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    /** feature 层接线（先落库 pending 再以唯一任务入队）；core 只转发触发参数并管理导出态。 */
    var exportRequest: (suspend (novelId: Long, seriesId: Long?, formatName: String) -> Unit)? = null

    /** 挂起等待落库完成，保证 Worker 启动前索引已存在。 */
    suspend fun markDownloadPending(entry: DownloadEntryEntity) {
        DownloadQueue.markPending(downloadEntryDao, entry)
    }

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

    /** 排行右栏切换选中项时调用；先清空旧状态避免加载期间错位。 */
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
        _exportState.value = ExportState.Idle
        load(id)
    }

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

    /** 收藏 id 取自当前展示小说（排行右栏路由 novelId=0）；断网由 [FavoriteActions] 入队。 */
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

    /** [ToggleUiState] 状态机；断网由 [FavoriteActions] 入队。 */
    fun toggleWatchlist() {
        val seriesId = _novel.value?.series?.id ?: return
        runToggle(
            _watchlistState,
            CoreR.string.core_msg_watching_added,
            CoreR.string.core_msg_watching_removed,
        ) { favoriteActions.toggleNovelWatchlist(seriesId, it) }
    }

    // ── 关注作者 ─────────────────────────────────────────────────────────────

    /** best-effort：失败保留内嵌值，并校验目标未切换。 */
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
     * 先等 feature 层写好 pending 索引并入队，再按完整复合主键观察单条状态，
     * 避免历史其他格式的完成记录提前结束等待。
     */
    fun export(formatName: String, series: Boolean = false) {
        val detail = _novel.value ?: return
        if (_exportState.value.isRunning) return
        val seriesId = if (series) detail.series?.id else null
        if (series && seriesId == null) return
        val requestHandler = exportRequest ?: return
        val scopeKey = if ((seriesId ?: 0L) > 0L) "series" else ""
        _exportState.value = ExportState.Running(context.getString(CoreR.string.core_msg_export_queued))
        exportObserverJob?.cancel()
        exportObserverJob = viewModelScope.launch {
            try {
                requestHandler(detail.id, seriesId, formatName)
                trySendMessage(UiMessage(CoreR.string.core_msg_export_queued))
                observeExportStateReset(detail.id, formatName, scopeKey)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _exportState.value = ExportState.Idle
                trySendMessage(UiMessage(CoreR.string.core_msg_action_failed, listOf(e.message.orEmpty())))
            }
        }
    }

    private suspend fun observeExportStateReset(id: Long, format: String, scopeKey: String) {
        try {
            downloadEntryDao.observeStatus("novel", id, format, scopeKey).first { status ->
                status == null ||
                    status == DownloadEntryEntity.STATUS_DONE ||
                    status == DownloadEntryEntity.STATUS_FAILED
            }
        } finally {
            _exportState.value = ExportState.Idle
        }
    }
}
