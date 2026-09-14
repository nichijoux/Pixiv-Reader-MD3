package com.pixiv.reader.core.network.illust

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.pixiv.api.model.Illust
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked: StateFlow<Boolean> = _isBookmarked.asStateFlow()

    private val _isBookmarking = MutableStateFlow(false)
    val isBookmarking: StateFlow<Boolean> = _isBookmarking.asStateFlow()

    /** 作者是否已关注（作者行关注胶囊；用 user/detail 权威刷新）。 */
    private val _isAuthorFollowed = MutableStateFlow(false)
    val isAuthorFollowed: StateFlow<Boolean> = _isAuthorFollowed.asStateFlow()

    /** 作者关注操作进行中（防连点）。 */
    private val _isAuthorFollowing = MutableStateFlow(false)
    val isAuthorFollowing: StateFlow<Boolean> = _isAuthorFollowing.asStateFlow()

    /** 所属漫画系列是否已追更（illust.series 非空时经系列详情加载；详情页追更按钮）。 */
    private val _isSeriesWatchlisted = MutableStateFlow(false)
    val isSeriesWatchlisted: StateFlow<Boolean> = _isSeriesWatchlisted.asStateFlow()

    /** 系列追更操作进行中（防连点）。 */
    private val _isSeriesWatchlisting = MutableStateFlow(false)
    val isSeriesWatchlisting: StateFlow<Boolean> = _isSeriesWatchlisting.asStateFlow()

    val relatedPaged = PagedState<Illust>()

    /** 收藏编辑器（公开/私密 + 标签收藏）：详情加载回显当前设置，弹层保存。 */
    val bookmarkEditor = BookmarkEditor(
        scope = viewModelScope,
        api = pixivRepository.api,
        targetType = "illust",
        targetId = { _illustId.value },
        uid = { pixivRepository.pixivApi.session.loggedInUid },
    )

    init {
        // 详情路由必有 id；排行右栏（无路由参数）不预载，等 switchTo
        if (illustId > 0L) load()
    }

    /**
     * 切换到另一作品（排行右栏选中项变化时调用）。
     * 清空旧作品全部状态后重新加载；加载期间旧内容先清空避免错位。
     */
    fun switchTo(id: Long) {
        if (id == _illustId.value || id <= 0L) return
        _illustId.value = id
        _illust.value = null
        _pages.value = emptyList()
        _ugoiraFrames.value = emptyList()
        _ugoiraProgress.value = null
        _error.value = null
        _isBookmarked.value = false
        _isBookmarking.value = false
        bookmarkEditor.onTargetLoaded(false)
        _isAuthorFollowed.value = false
        _isAuthorFollowing.value = false
        _isSeriesWatchlisted.value = false
        _isSeriesWatchlisting.value = false
        relatedPaged.reset()
        load()
    }

    fun load() {
        // 发起前捕获目标 id：右栏快速切换时旧响应到达但目标已变，直接丢弃（防旧数据覆盖新作品）
        val requestedId = _illustId.value
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching { pixivRepository.api.getIllust(requestedId) }
                .onSuccess { resp ->
                    if (_illustId.value != requestedId) return@onSuccess
                    val ill = resp.illust ?: return@onSuccess
                    _illust.value = ill
                    _isBookmarked.value = ill.is_bookmarked == true
                    bookmarkEditor.onTargetLoaded(ill.is_bookmarked == true)
                    // 内嵌 user.is_followed 可能缺失，用 user/detail 权威刷新关注态（失败保留内嵌值）
                    _isAuthorFollowed.value = ill.user?.is_followed == true
                    ill.user?.id?.let { loadAuthorFollowState(it) }
                    // 漫画系列作品：拉系列详情回填追更态（详情页追更按钮）
                    ill.series?.id?.takeIf { it > 0L }?.let { loadSeriesWatchlistState(it) }
                    _pages.value = ill.toPages()
                    recordHistory(ill)
                    loadRelated()
                    loadRealSizes()
                    if (ill.isGif()) loadUgoira()
                }
                .onFailure {
                    if (_illustId.value != requestedId) return@onFailure
                    _error.value = loadFailureMessage(
                        it,
                        CoreR.string.core_illust_load_failed_reason,
                        CoreR.string.core_illust_load_failed,
                    )
                }
            if (_illustId.value == requestedId) _isLoading.value = false
        }
    }

    /** 打开详情时写入浏览历史（先删旧记录避免重复；payloadJson 存宽高等，供历史页完整显示）。 */
    private fun recordHistory(ill: Illust) {
        viewModelScope.launch {
            runCatching {
                browseHistoryDao.deleteByTarget("illust", ill.id)
                browseHistoryDao.upsert(
                    BrowseHistoryEntity(
                        targetType = "illust",
                        targetId = ill.id,
                        title = ill.title,
                        coverUrl = ill.bestCoverUrl,
                        payloadJson = ill.snapshotPayload(),
                    ),
                )
            }
        }
    }

    /** user/detail 权威刷新作者关注态（详情内嵌 user.is_followed 可能缺失）。 */
    private fun loadAuthorFollowState(userId: Long) {
        viewModelScope.launch {
            runCatching { pixivRepository.api.getUserDetail(userId) }
                .onSuccess { resp ->
                    resp.user?.is_followed?.let { _isAuthorFollowed.value = it }
                }
        }
    }

    /** 关注 / 取关作者（作者行胶囊，乐观翻转 + 防连点；经 FavoriteActions 统一收口）。 */
    fun toggleFollowAuthor() {
        val userId = _illust.value?.user?.id ?: return
        runOptimisticToggle(
            _isAuthorFollowing,
            _isAuthorFollowed.value,
            { _isAuthorFollowed.value = it },
            CoreR.string.core_msg_followed_author,
            CoreR.string.core_msg_unfollowed,
        ) { favoriteActions.toggleFollowUser(userId, it) }
    }

    /** 加载所属漫画系列的追更态（v1/illust/series 的 detail.watchlist_added；失败保留默认未追更）。 */
    private fun loadSeriesWatchlistState(seriesId: Long) {
        viewModelScope.launch {
            runCatching { pixivRepository.api.getIllustSeries(seriesId) }
                .onSuccess { resp ->
                    resp.illust_series_detail?.watchlist_added?.let { _isSeriesWatchlisted.value = it }
                }
        }
    }

    /** 追更 / 取消追更所属漫画系列（详情页追更按钮，乐观翻转 + 防连点；经 FavoriteActions 统一收口，断网自动入队）。 */
    fun toggleSeriesWatchlist() {
        val seriesId = _illust.value?.series?.id ?: return
        runOptimisticToggle(
            _isSeriesWatchlisting,
            _isSeriesWatchlisted.value,
            { _isSeriesWatchlisted.value = it },
            CoreR.string.core_msg_watching_added,
            CoreR.string.core_msg_watching_removed,
        ) { favoriteActions.toggleMangaWatchlist(seriesId, it) }
    }

    /** 网页接口补齐每 P 真实宽高（app-api 不提供） */
    private fun loadRealSizes() {
        viewModelScope.launch {
            runCatching { pixivRepository.webApi.getIllustPages(_illustId.value) }
                .onSuccess { resp ->
                    val sizes = resp.body.orEmpty()
                    if (sizes.isNotEmpty()) {
                        val updated = _pages.value.mapIndexed { index, page ->
                            val size = sizes.getOrNull(index)
                            if (size != null && size.width > 0 && size.height > 0) {
                                page.copy(width = size.width, height = size.height)
                            } else {
                                page
                            }
                        }
                        _pages.value = updated
                    }
                }
        }
    }

    /** 动图：加载 zip 帧（详情页 pager 播放；失败帧空则保持静态封面，查看器有失败提示）。 */
    private fun loadUgoira() {
        viewModelScope.launch {
            _ugoiraProgress.value = 0f
            _ugoiraFrames.value =
                ugoiraLoader.prepare(_illustId.value) { p -> _ugoiraProgress.value = p }.orEmpty()
            _ugoiraProgress.value = null
        }
    }

    fun loadRelated() {
        viewModelScope.launch {
            relatedPaged.loadInitial(
                fetch = { pixivRepository.api.getRelatedIllusts(_illustId.value) },
                fetchNext = { pixivRepository.api.getNextIllusts(it) },
            )
        }
    }

    fun loadMoreRelated() {
        viewModelScope.launch { relatedPaged.loadMore() }
    }

    /**
     * 收藏 / 取消收藏插画（乐观翻转 + 防连点；经 FavoriteActions 统一收口，断网自动入队）。
     * 成功静默（详情页收藏图标即时反馈），仅刷新收藏态与编辑器回显/清空；失败发通知。
     */
    fun toggleBookmark() {
        runOptimisticToggle(
            _isBookmarking,
            _isBookmarked.value,
            { state ->
                _isBookmarked.value = state
                // 收藏成功 → 编辑器回显当前设置；取消收藏 → 清空回显
                bookmarkEditor.onTargetLoaded(state)
            },
            addedRes = null,
            removedRes = null,
        ) { favoriteActions.toggleIllustFavorite(_illustId.value, it) }
    }

    /**
     * 收藏编辑器保存（公开/私密 + 标签收藏）：成功后刷新收藏态、关闭弹层并提示。
     * 期间复用 [_isBookmarking] 防连点（与一键收藏互斥）。
     */
    fun saveBookmarkEditor() {
        runActionNotified(
            _isBookmarking,
            CoreR.string.core_msg_bookmark_updated,
            {
                _isBookmarked.value = true
                bookmarkEditor.close()
            },
        ) { bookmarkEditor.save() }
    }

    /**
     * 下载整个作品到 filesDir/Downloads/，由 WorkManager 后台执行：
     * 静态插画走 [IllustDownloadWorker]（全部页），动图（ugoira）走 [UgoiraExportWorker]（默认 MP4）。
     * 入队即建「待同步」索引条目（断网时停留待同步，联网后 Worker 网络约束自动开始）。
     */
    fun download() {
        val illust = _illust.value
        val isGif = illust?.isGif() == true
        val targetType = if (isGif) "ugoira" else "illust"
        val format = if (isGif) UgoiraExportFormat.MP4.format else ""
        // 网络约束：断网时 Worker 挂起（条目停留待同步），联网自动开始；tag 供删除时精确取消
        val request = (if (isGif) {
            OneTimeWorkRequestBuilder<UgoiraExportWorker>()
                .setInputData(
                    workDataOf(
                        UgoiraExportWorker.KEY_ILLUST_ID to _illustId.value,
                        UgoiraExportWorker.KEY_FORMAT to UgoiraExportFormat.MP4.format,
                    )
                )
        } else {
            OneTimeWorkRequestBuilder<IllustDownloadWorker>()
                .setInputData(workDataOf(IllustDownloadWorker.KEY_ILLUST_ID to _illustId.value))
        })
            .setConstraints(DownloadQueue.networkConstraints())
            .addTag(DownloadQueue.workTag(targetType, _illustId.value, format, ""))
            .build()
        // 入队即建「待同步」条目：卡片立即出现在下载管理页（断网也不丢），Worker 起跑覆写为下载中
        viewModelScope.launch {
            runCatching {
                DownloadQueue.markPending(
                    downloadEntryDao,
                    DownloadEntryEntity(
                        targetId = _illustId.value,
                        targetType = targetType,
                        title = illust?.title,
                        coverUrl = illust?.bestCoverUrl,
                        format = format,
                        pageCount = illust?.page_count ?: 0,
                        width = illust?.width ?: 0,
                        height = illust?.height ?: 0,
                    ),
                )
            }
        }
        WorkManager.getInstance(context).enqueue(request)
        trySendMessage(UiMessage(CoreR.string.core_illust_download_started))
    }
}
