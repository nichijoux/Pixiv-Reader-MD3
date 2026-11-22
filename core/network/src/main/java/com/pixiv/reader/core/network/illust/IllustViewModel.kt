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
import com.pixiv.reader.core.network.ugoira.UgoiraFrame
import com.pixiv.reader.core.network.ugoira.UgoiraLoader
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
 * 详情 / 多页（网页接口补每 P 真实宽高）/ 动图帧（ugoira）/ 相关推荐 / 收藏 / 作者关注。
 * 附带副作用：打开详情写浏览历史；下载整个作品（全部页）由 [IllustDownloadWorker] 后台执行。
 *
 * illustId 从 SavedStateHandle 读取（详情路由参数）；排行右栏等内嵌场景无此参数（=0），
 * 不预载，由调用方 [switchTo] 驱动加载。
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

    /** 详情页路由参数 id（固定，详情路由用；排行右栏等内嵌场景无此参数时走 [switchTo]）。 */
    private val illustId: Long = savedStateHandle.get<Long>("illustId") ?: 0L

    /** 当前展示的作品 id（可变：排行右栏随选中项切换；无路由参数时初始为 0 待 [switchTo]）。 */
    private val _illustId = MutableStateFlow(illustId)
    val illustIdFlow: StateFlow<Long> = _illustId.asStateFlow()

    private val _illust = MutableStateFlow<Illust?>(null)
    val illust: StateFlow<Illust?> = _illust.asStateFlow()

    private val _pages = MutableStateFlow<List<IllustPageInfo>>(emptyList())
    val pages: StateFlow<List<IllustPageInfo>> = _pages.asStateFlow()

    /** 动图帧（ugoira 作品非空）：详情页 pager 播放动画（帧未就绪显示静态封面）。 */
    private val _ugoiraFrames = MutableStateFlow<List<UgoiraFrame>>(emptyList())
    val ugoiraFrames: StateFlow<List<UgoiraFrame>> = _ugoiraFrames.asStateFlow()

    /** 动图 zip 下载进度 0..1（下载中）；null = 未开始/已就绪/失败（详情页转圈提示用）。 */
    private val _ugoiraProgress = MutableStateFlow<Float?>(null)
    val ugoiraProgress: StateFlow<Float?> = _ugoiraProgress.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error.asStateFlow()

    /** 收藏状态机（一键收藏按钮；详情 is_bookmarked 初始化，[toggleBookmark] 驱动转移）。 */
    private val _bookmarkState = MutableStateFlow(ToggleUiState.OFF)
    val bookmarkState: StateFlow<ToggleUiState> = _bookmarkState.asStateFlow()

    /** 收藏编辑器保存进行中（防连点 + 弹层确认按钮禁用；一键收藏走 [_bookmarkState] 独立防连点）。 */
    private val _isEditorSaving = MutableStateFlow(false)
    val isEditorSaving: StateFlow<Boolean> = _isEditorSaving.asStateFlow()

    /** 作者关注状态机（作者行关注胶囊；用 user/detail 权威刷新）。 */
    private val _authorFollowState = MutableStateFlow(ToggleUiState.OFF)
    val authorFollowState: StateFlow<ToggleUiState> = _authorFollowState.asStateFlow()

    /** 所属漫画系列追更状态机（illust.series 非空时经系列详情加载；详情页追更按钮）。 */
    private val _seriesWatchlistState = MutableStateFlow(ToggleUiState.OFF)
    val seriesWatchlistState: StateFlow<ToggleUiState> = _seriesWatchlistState.asStateFlow()

    val relatedPaged = PagedState<Illust>(keySelector = { it.id })

    /** 收藏编辑器（公开/私密 + 标签收藏）：详情加载回显当前设置，弹层保存。 */
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

    /**
     * 切换到另一作品（排行右栏选中项变化时调用）。
     * 清空旧作品全部状态后重新加载；加载期间旧内容先清空避免错位。
     *
     * @param id 新选中的作品 ID
     * @return 无返回值
     */
    fun switchTo(id: Long) {
        if (id == _illustId.value || id <= 0L) return
        // 先取消旧作品的详情、附属加载和相关推荐，再发布新作品状态。
        loadJob?.cancel()
        relatedLoadJob?.cancel()
        relatedLoadJob = null
        _illustId.value = id
        _illust.value = null
        _pages.value = emptyList()
        _ugoiraFrames.value = emptyList()
        _ugoiraProgress.value = null
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

    /**
     * 加载当前作品详情，并在同一任务下启动只属于该作品的附属请求。
     *
     * @return 无返回值
     */
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

    /**
     * 独立记录实际打开过的作品，保存历史卡片需要的完整快照。
     *
     * @param ill 已打开的作品
     * @return 无返回值
     */
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

    /**
     * 从作者详情补齐作品内嵌信息可能缺失的关注态。
     *
     * @param userId 作品作者 ID
     * @return 无返回值
     * @throws CancellationException 作者详情请求被取消
     */
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

    /**
     * 关注 / 取关作者（作者行胶囊，[ToggleUiState] 状态机：进行中保留旧文案禁用防连点，
     * 失败回滚；经 FavoriteActions 统一收口）。
     *
     * @return 无返回值（操作完成后结束的协程）
     */
    fun toggleFollowAuthor() {
        val userId = _illust.value?.user?.id ?: return
        runToggle(
            _authorFollowState,
            CoreR.string.core_msg_followed_author,
            CoreR.string.core_msg_unfollowed,
        ) { favoriteActions.toggleFollowUser(userId, it) }
    }

    /**
     * 从漫画系列详情补齐所属系列的追更态。
     *
     * @param seriesId 作品所属的漫画系列 ID
     * @return 无返回值
     * @throws CancellationException 系列详情请求被取消
     */
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

    /**
     * 追更 / 取消追更所属漫画系列（详情页追更按钮，[ToggleUiState] 状态机：进行中保留旧文案
     * 禁用防连点，失败回滚；经 FavoriteActions 统一收口，断网自动入队）。
     *
     * @return 无返回值（操作完成后结束的协程）
     */
    fun toggleSeriesWatchlist() {
        val seriesId = _illust.value?.series?.id ?: return
        runToggle(
            _seriesWatchlistState,
            CoreR.string.core_msg_watching_added,
            CoreR.string.core_msg_watching_removed,
        ) { favoriteActions.toggleMangaWatchlist(seriesId, it) }
    }

    /**
     * 通过网页接口补齐当前作品的逐页原图尺寸。
     *
     * @param requestedId 发起请求时的作品 ID
     * @return 无返回值
     * @throws CancellationException 页面尺寸请求被取消
     */
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

    /**
     * 读取当前作品的动图帧，并在主线程回填下载进度。
     *
     * @param requestedId 发起请求时的作品 ID
     * @return 无返回值
     * @throws CancellationException 动图加载被取消
     */
    private suspend fun loadUgoira(requestedId: Long) {
        val scope = CoroutineScope(currentCoroutineContext())
        _ugoiraProgress.value = 0f
        try {
            val frames = ugoiraLoader.prepare(requestedId) { progress ->
                // 回调来自 IO；纳入同一任务后，切换作品会取消正在排队的进度更新。
                scope.launch {
                    if (_ugoiraProgress.value != null) _ugoiraProgress.value = progress
                }
            }
            scope.ensureActive()
            _ugoiraFrames.value = frames.orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "加载插画动图失败", e)
        } finally {
            if (scope.isActive) _ugoiraProgress.value = null
        }
    }

    /**
     * 加载或重试当前作品的相关推荐首页。
     *
     * @return 无返回值
     */
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

    /**
     * 收藏 / 取消收藏插画（[ToggleUiState] 状态机：进行中保留旧文案禁用防连点，失败回滚；
     * 经 FavoriteActions 统一收口，断网自动入队）。成功静默（详情页收藏图标即时反馈），
     * 仅刷新收藏态与编辑器回显/清空；失败发通知。
     */
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
                favoriteActions.setIllustBookmarkState(_illustId.value, true)
                bookmarkEditor.close()
            },
        ) { bookmarkEditor.save() }
    }

    /**
     * 下载整个作品到 filesDir/Downloads/，由 WorkManager 后台执行：
     * 静态插画走 [IllustDownloadWorker]（全部页），动图（ugoira）走 [UgoiraExportWorker]（默认 MP4）。
     * 索引写入完成后才提交给 WorkManager，提交失败时回滚索引。
     *
     * @return 无返回值
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
