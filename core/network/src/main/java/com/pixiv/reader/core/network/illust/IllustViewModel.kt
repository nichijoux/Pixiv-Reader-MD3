package com.pixiv.reader.core.network.illust

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.common.ToggleUiState
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.common.loadFailureMessage
import com.pixiv.reader.core.common.R as CoreR
import com.pixiv.reader.core.database.dao.BrowseHistoryDao
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.entity.BrowseHistoryEntity
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.network.download.DownloadQueue
import com.pixiv.reader.core.network.favorite.BookmarkEditor
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.download.UgoiraExportFormat
import com.pixiv.reader.core.network.download.UgoiraExportWorker
import com.pixiv.reader.core.network.message.MessageViewModel
import com.pixiv.reader.core.network.model.IllustPageInfo
import com.pixiv.reader.core.network.model.bestCoverUrl
import com.pixiv.reader.core.network.model.snapshotPayload
import com.pixiv.reader.core.network.model.toPages
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.core.network.ugoira.UgoiraLoader
import com.pixiv.reader.core.network.ugoira.UgoiraState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 插画详情 ViewModel（core:network 下沉，供 feature:illust 详情路由与 feature:manga 排行右栏共用）。
 * illustId 取自 SavedStateHandle；排行右栏无该参数（=0）不预载，由 [switchTo] 驱动加载。
 */
@HiltViewModel
class IllustViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
    private val ugoiraLoader: UgoiraLoader,
    private val browseHistoryDao: BrowseHistoryDao,
    private val downloadEntryDao: DownloadEntryDao,
    private val favoriteActions: FavoriteActions,
    @param:ApplicationContext private val context: Context,
) : MessageViewModel() {

    private val illustId: Long = savedStateHandle.get<Long>("illustId") ?: 0L

    private val _illustId = MutableStateFlow(illustId)
    val illustIdFlow: StateFlow<Long> = _illustId.asStateFlow()

    private val _illust = MutableStateFlow<Illust?>(null)
    val illust: StateFlow<Illust?> = _illust.asStateFlow()

    private val _pages = MutableStateFlow<List<IllustPageInfo>>(emptyList())
    val pages: StateFlow<List<IllustPageInfo>> = _pages.asStateFlow()

    private val _ugoiraState = MutableStateFlow<UgoiraState>(UgoiraState.Idle)
    val ugoiraState: StateFlow<UgoiraState> = _ugoiraState.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error.asStateFlow()

    private val _bookmarkState = MutableStateFlow(ToggleUiState.OFF)
    val bookmarkState: StateFlow<ToggleUiState> = _bookmarkState.asStateFlow()

    private val _isEditorSaving = MutableStateFlow(false)
    val isEditorSaving: StateFlow<Boolean> = _isEditorSaving.asStateFlow()

    private val _authorFollowState = MutableStateFlow(ToggleUiState.OFF)
    val authorFollowState: StateFlow<ToggleUiState> = _authorFollowState.asStateFlow()

    private val _seriesWatchlistState = MutableStateFlow(ToggleUiState.OFF)
    val seriesWatchlistState: StateFlow<ToggleUiState> = _seriesWatchlistState.asStateFlow()

    val relatedPaged = PagedState<Illust>(keySelector = { it.id })

    val bookmarkEditor = BookmarkEditor(
        scope = viewModelScope,
        api = pixivRepository.api,
        targetType = "illust",
        targetId = { _illustId.value },
        uid = { pixivRepository.pixivApi.session.loggedInUid },
    )

    private var loadJob: Job? = null
    private var relatedLoadJob: Job? = null

    init {
        // 同步跨页收藏与关注覆写流（如从全屏查看器或列表卡片操作后返回详情页时实时更新）。
        viewModelScope.launch {
            combine(_illustId, favoriteActions.illustBookmarks) { id, map ->
                id to map[id]
            }.distinctUntilChanged().collect { (id, bookmarked) ->
                if (id <= 0L || bookmarked == null || _illust.value?.id != id) return@collect
                if (!_bookmarkState.value.inFlight && _bookmarkState.value.isOn != bookmarked) {
                    _bookmarkState.value = if (bookmarked) ToggleUiState.ON else ToggleUiState.OFF
                    bookmarkEditor.onTargetLoaded(bookmarked)
                }
            }
        }
        viewModelScope.launch {
            combine(_illust, favoriteActions.followedUsers) { ill, map ->
                ill?.user?.id?.let { uid -> uid to map[uid] }
            }.distinctUntilChanged().collect { pair ->
                val (_, followed) = pair ?: return@collect
                if (followed != null && !_authorFollowState.value.inFlight && _authorFollowState.value.isOn != followed) {
                    _authorFollowState.value = if (followed) ToggleUiState.ON else ToggleUiState.OFF
                }
            }
        }
        // 详情路由必有 id；排行右栏（无路由参数）不预载，等 switchTo
        if (illustId > 0L) load()
    }

    /** 排行右栏切换选中项时调用；先清空旧作品全部状态，避免加载期间新旧内容错位。 */
    fun switchTo(id: Long) {
        if (id == _illustId.value || id <= 0L) return
        // 先取消旧作品的详情、附属加载和相关推荐，再发布新作品状态。
        loadJob?.cancel()
        relatedLoadJob?.cancel()
        relatedLoadJob = null
        _illustId.value = id
        _illust.value = null
        _pages.value = emptyList()
        _ugoiraState.value = UgoiraState.Idle
        _error.value = null
        // 三对开关状态机 + 编辑器保存标志整体复位（成对生命周期：一条流清一条）
        _bookmarkState.value = ToggleUiState.OFF
        _isEditorSaving.value = false
        bookmarkEditor.onTargetLoaded(false)
        _authorFollowState.value = ToggleUiState.OFF
        _seriesWatchlistState.value = ToggleUiState.OFF
        relatedPaged.reset()
        load()
    }

    /** 附属请求挂在同一任务下，随作品切换一起取消。 */
    fun load() {
        val requestedId = _illustId.value
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val ill = pixivRepository.api.getIllust(requestedId).illust
                ensureActive()
                if (ill == null) {
                    _error.value = UiMessage(CoreR.string.core_illust_load_failed)
                    return@launch
                }
                _illust.value = ill
                _pages.value = ill.toPages()
                val bookmarked = ill.is_bookmarked == true
                _bookmarkState.value = if (bookmarked) ToggleUiState.ON else ToggleUiState.OFF
                favoriteActions.setIllustBookmarkState(ill.id, bookmarked)
                bookmarkEditor.onTargetLoaded(bookmarked)
                // 先展示详情随附的关注态，再用对应作者和系列的详情各自校正。
                val embeddedFollowed = ill.user?.is_followed == true
                _authorFollowState.value = if (embeddedFollowed) ToggleUiState.ON else ToggleUiState.OFF
                ill.user?.id?.let { userId -> launch { loadAuthorFollowState(userId) } }
                ill.series?.id?.takeIf { it > 0L }?.let { seriesId ->
                    launch { loadSeriesWatchlistState(seriesId) }
                }
                recordHistory(ill)
                loadRelated()
                launch { loadRealSizes(requestedId) }
                if (ill.isGif()) launch { loadUgoira(requestedId) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ensureActive()
                _error.value = loadFailureMessage(
                    e,
                    CoreR.string.core_illust_load_failed_reason,
                    CoreR.string.core_illust_load_failed,
                )
            } finally {
                // 取消的旧任务不能结束新作品的加载指示。
                if (isActive) _isLoading.value = false
            }
        }
    }

    /** 独立任务写入，保存历史卡片渲染所需的完整快照。 */
    private fun recordHistory(ill: Illust) {
        viewModelScope.launch {
            try {
                // 浏览过的作品即使随即切换也保留，在同一事务内原子去重置顶。
                browseHistoryDao.replaceTop(
                    BrowseHistoryEntity(
                        targetType = "illust",
                        targetId = ill.id,
                        title = ill.title,
                        coverUrl = ill.bestCoverUrl,
                        payloadJson = ill.snapshotPayload(),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "记录插画浏览历史失败", e)
            }
        }
    }

    /** 作品内嵌的关注态可能过期，用作者详情校正。 */
    private suspend fun loadAuthorFollowState(userId: Long) {
        try {
            val followed = pixivRepository.api.getUserDetail(userId).user?.is_followed ?: return
            currentCoroutineContext().ensureActive()
            _authorFollowState.value = if (followed) ToggleUiState.ON else ToggleUiState.OFF
            favoriteActions.setUserFollowState(userId, followed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 作者详情失败时继续沿用作品内嵌的关注态。
            Log.w(TAG, "更新作者关注状态失败", e)
        }
    }

    /** [ToggleUiState] 状态机：进行中保留旧文案并禁用防连点，失败回滚。 */
    fun toggleFollowAuthor() {
        val userId = _illust.value?.user?.id ?: return
        runToggle(
            _authorFollowState,
            CoreR.string.core_msg_followed_author,
            CoreR.string.core_msg_unfollowed,
        ) { favoriteActions.toggleFollowUser(userId, it) }
    }

    private suspend fun loadSeriesWatchlistState(seriesId: Long) {
        try {
            val watching = pixivRepository.api.getIllustSeries(seriesId)
                .illust_series_detail?.watchlist_added ?: return
            currentCoroutineContext().ensureActive()
            _seriesWatchlistState.value = if (watching) ToggleUiState.ON else ToggleUiState.OFF
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "更新漫画系列追更状态失败", e)
        }
    }

    /** [ToggleUiState] 状态机；断网时由 [FavoriteActions] 自动入队待恢复。 */
    fun toggleSeriesWatchlist() {
        val seriesId = _illust.value?.series?.id ?: return
        runToggle(
            _seriesWatchlistState,
            CoreR.string.core_msg_watching_added,
            CoreR.string.core_msg_watching_removed,
        ) { favoriteActions.toggleMangaWatchlist(seriesId, it) }
    }

    /** 网页接口按原页序给尺寸，缺失项保留已有值。 */
    private suspend fun loadRealSizes(requestedId: Long) {
        try {
            val sizes = pixivRepository.webApi.getIllustPages(requestedId).body.orEmpty()
            currentCoroutineContext().ensureActive()
            // 网页接口按原页序给出尺寸，保留缺失尺寸的已有页面。
            if (sizes.isEmpty()) return
            _pages.value = _pages.value.mapIndexed { index, page ->
                val size = sizes.getOrNull(index)
                if (size != null && size.width > 0 && size.height > 0) {
                    page.copy(width = size.width, height = size.height)
                } else {
                    page
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "补齐插画页面尺寸失败", e)
        }
    }

    private suspend fun loadUgoira(requestedId: Long) {
        val scope = CoroutineScope(currentCoroutineContext())
        _ugoiraState.value = UgoiraState.Loading(0f)
        try {
            val frames = ugoiraLoader.prepare(requestedId) { progress ->
                // 回调来自 IO；纳入同一任务后，切换作品会取消正在排队的进度更新。
                scope.launch {
                    if (_ugoiraState.value is UgoiraState.Loading) {
                        _ugoiraState.value = UgoiraState.Loading(progress)
                    }
                }
            }
            scope.ensureActive()
            _ugoiraState.value = if (frames.isNullOrEmpty()) UgoiraState.Failed else UgoiraState.Ready(frames)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "加载插画动图失败", e)
            if (scope.isActive) _ugoiraState.value = UgoiraState.Failed
        }
    }

    fun loadRelated() {
        if (relatedLoadJob?.isActive == true) return
        val requestedId = _illustId.value
        relatedLoadJob = viewModelScope.launch {
            // 请求固定使用发起时的 ID，切换时同时重置分页的游标代次。
            relatedPaged.loadInitial(
                fetch = { pixivRepository.api.getRelatedIllusts(requestedId) },
                fetchNext = { pixivRepository.api.getNextIllusts(it) },
            )
        }
    }

    /** 成功静默（图标即时反馈），仅失败发通知。 */
    fun toggleBookmark() {
        runToggle(
            _bookmarkState,
            addedRes = null,
            removedRes = null,
        ) { target ->
            favoriteActions.toggleIllustFavorite(_illustId.value, target).onSuccess {
                // 收藏成功 → 编辑器回显当前设置；取消收藏 → 清空回显
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
                favoriteActions.setIllustBookmarkState(_illustId.value, true)
                bookmarkEditor.close()
            },
        ) { bookmarkEditor.save() }
    }

    /**
     * 静态插画走 [IllustDownloadWorker]（全部页），动图走 [UgoiraExportWorker]（MP4）。
     * 先写索引再入队、入队失败回滚索引，避免管理页残留无任务的条目。
     */
    fun download() {
        // 固定目标及详情快照，避免切换右栏后下载记录写到其他作品。
        val targetId = _illustId.value
        if (targetId <= 0L) return
        val illust = _illust.value
        val isGif = illust?.isGif() == true
        val targetType = if (isGif) "ugoira" else "illust"
        val format = if (isGif) UgoiraExportFormat.MP4.format else ""
        val workTag = DownloadQueue.workTag(targetType, targetId, format, "")
        // 网络约束：断网时 Worker 挂起（条目停留待同步），联网自动开始；tag 供删除时精确取消与去重入队。
        val request = (if (isGif) {
            OneTimeWorkRequestBuilder<UgoiraExportWorker>()
                .setInputData(
                    workDataOf(
                        UgoiraExportWorker.KEY_ILLUST_ID to targetId,
                        UgoiraExportWorker.KEY_FORMAT to UgoiraExportFormat.MP4.format,
                    )
                )
        } else {
            OneTimeWorkRequestBuilder<IllustDownloadWorker>()
                .setInputData(workDataOf(IllustDownloadWorker.KEY_ILLUST_ID to targetId))
        })
            .setConstraints(DownloadQueue.networkConstraints())
            .addTag(workTag)
            .build()
        val pending = DownloadEntryEntity(
            targetId = targetId,
            targetType = targetType,
            title = illust?.title,
            coverUrl = illust?.bestCoverUrl,
            format = format,
            pageCount = illust?.page_count ?: 0,
            width = illust?.width ?: 0,
            height = illust?.height ?: 0,
            status = DownloadEntryEntity.STATUS_PENDING,
        )
        viewModelScope.launch {
            try {
                // 短暂保护索引和唯一入队交接，若任务已在队列或下载中则不覆盖现有进度。
                withContext(NonCancellable + Dispatchers.IO) {
                    downloadEnqueueMutex.withLock {
                        val workManager = WorkManager.getInstance(context)
                        val previous = downloadEntryDao.get(targetType, targetId, format)
                        val alreadyActive = previous?.status == DownloadEntryEntity.STATUS_PENDING ||
                            previous?.status == DownloadEntryEntity.STATUS_DOWNLOADING
                        if (!alreadyActive) {
                            downloadEntryDao.upsert(pending)
                        }
                        try {
                            workManager.enqueueUniqueWork(workTag, ExistingWorkPolicy.KEEP, request).await()
                        } catch (e: Exception) {
                            // 仅回滚这次提交留下的标记，保留 Worker 或其他入口更新的记录。
                            if (!alreadyActive && downloadEntryDao.get(targetType, targetId, format) == pending) {
                                if (previous == null) downloadEntryDao.delete(pending)
                                else downloadEntryDao.upsert(previous)
                            }
                            throw e
                        }
                    }
                }
                sendMessage(UiMessage(CoreR.string.core_illust_download_started))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "插画下载入队失败", e)
                sendMessage(UiMessage(CoreR.string.core_msg_action_failed, listOf(e.message.orEmpty())))
            }
        }
    }

    private companion object {
        const val TAG = "IllustViewModel"
        val downloadEnqueueMutex = Mutex()
    }
}
