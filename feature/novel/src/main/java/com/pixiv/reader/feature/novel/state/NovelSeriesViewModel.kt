package com.pixiv.reader.feature.novel.state

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.await
import com.pixiv.api.model.Novel
import com.pixiv.api.model.NovelSeriesDetail
import com.pixiv.reader.core.common.ToggleUiState
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.common.R as CoreR
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.network.download.DownloadQueue
import com.pixiv.reader.core.network.message.MessageViewModel
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.novel.fetchAllSeriesChapters
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.core.network.session.SeriesDetailCache
import com.pixiv.reader.core.network.session.SeriesDetailInfo
import com.pixiv.reader.feature.novel.R
import com.pixiv.reader.feature.novel.data.NovelExportFormat
import com.pixiv.reader.feature.novel.data.NovelExportQueue
import com.pixiv.reader.feature.novel.data.novelScopeKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 系列无独立封面，取首册 medium 并经 [SeriesDetailCache] 与用户主页系列列表共享缓存。
 * 关注态以 detail 内嵌 `is_followed` 初始 + getUserDetail 权威刷新；导出整系列/部分分册合并为单文件。
 */
@HiltViewModel
class NovelSeriesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val pixivRepository: PixivRepository,
    private val seriesDetailCache: SeriesDetailCache,
    private val downloadEntryDao: DownloadEntryDao,
    private val favoriteActions: FavoriteActions,
) : MessageViewModel() {

    // 路由场景由 SavedStateHandle 提供（init 自动加载）；pane 场景为 0，由 [switchTo] 显式指定加载目标
    private var seriesId: Long = savedStateHandle.get<Long>("seriesId") ?: 0L

    private val _detail = MutableStateFlow<NovelSeriesDetail?>(null)
    val detail: StateFlow<NovelSeriesDetail?> = _detail.asStateFlow()

    /** 系列无独立封面，用首册 `image_urls` 兜底；null 表示无可用图。 */
    private val _firstNovelCover = MutableStateFlow<String?>(null)
    val firstNovelCover: StateFlow<String?> = _firstNovelCover.asStateFlow()

    val paged = PagedState<Novel>(keySelector = { it.id })

    // ── 关注作者 / 追更（ToggleUiState 状态机） ────────────────────────────────

    private val _authorFollowState = MutableStateFlow(ToggleUiState.OFF)
    val authorFollowState: StateFlow<ToggleUiState> = _authorFollowState.asStateFlow()

    private val _watchlistState = MutableStateFlow(ToggleUiState.OFF)
    val watchlistState: StateFlow<ToggleUiState> = _watchlistState.asStateFlow()

    // ── 下载 / 导出 ──────────────────────────────────────────────────────────

    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow<String?>(null)
    val downloadProgress: StateFlow<String?> = _downloadProgress.asStateFlow()

    /** 非分页：首次按需全量拉取并缓存，供「选取部分」下载。 */
    private val _allChapters = MutableStateFlow<List<Novel>>(emptyList())
    val allChapters: StateFlow<List<Novel>> = _allChapters.asStateFlow()

    private var exportObserverJob: Job? = null

    init {
        // 同步作者关注状态覆写流，确保详情与列表卡片关注操作后系列头实时同步。
        viewModelScope.launch {
            combine(_detail, favoriteActions.followedUsers) { d, map ->
                d?.user?.id?.let { uid -> uid to map[uid] }
            }.distinctUntilChanged().collect { pair ->
                val (_, followed) = pair ?: return@collect
                if (followed != null && !_authorFollowState.value.inFlight && _authorFollowState.value.isOn != followed) {
                    _authorFollowState.value = if (followed) ToggleUiState.ON else ToggleUiState.OFF
                }
            }
        }
        // 仅路由场景自动加载（seriesId 非 0）；pane 场景等待 switchTo 指定目标
        if (seriesId != 0L) load()
    }

    /** pane 场景切换加载目标；同 id 且已有详情时不重载（重组 / 重复点击不触发多余请求）。 */
    fun switchTo(id: Long) {
        if (id == seriesId && _detail.value != null) return
        seriesId = id
        exportObserverJob?.cancel()
        // 分页游标也要重置，否则旧系列数据串页
        _detail.value = null
        _firstNovelCover.value = null
        _authorFollowState.value = ToggleUiState.OFF
        _watchlistState.value = ToggleUiState.OFF
        _allChapters.value = emptyList()
        _downloading.value = false
        _downloadProgress.value = null
        paged.reset()
        load()
    }

    fun load() {
        viewModelScope.launch {
            paged.loadInitial(
                fetch = {
                    pixivRepository.api.getNovelSeries(seriesId).also { resp ->
                        _detail.value = resp.novel_series_detail
                        // 追更态以服务端返回为准（重试 / 重新加载时同步刷新）
                        _watchlistState.value =
                            if (resp.novel_series_detail?.watchlist_added == true) ToggleUiState.ON
                            else ToggleUiState.OFF
                        // 走进程级缓存：用户主页列表已取过系列详情则零请求
                        _firstNovelCover.value = seriesDetailCache.getOrFetch(seriesId) {
                            SeriesDetailInfo(
                                coverUrl = resp.novel_series_first_novel?.image_urls?.medium
                                    ?: resp.novel_series_first_novel?.image_urls?.square_medium,
                                caption = resp.novel_series_detail?.caption,
                                isConcluded = resp.novel_series_detail?.is_concluded,
                                totalChars = resp.novel_series_detail?.total_character_count ?: 0,
                                updatedAt = resp.novel_series_latest_novel?.create_date,
                            )
                        }?.coverUrl
                        val seriesDetail = resp.novel_series_detail
                        seriesDetail?.user?.id?.let { userId ->
                            _authorFollowState.value =
                                if (seriesDetail.user?.is_followed == true) ToggleUiState.ON
                                else ToggleUiState.OFF
                            loadAuthorFollowState(userId)
                        }
                    }
                },
                fetchNext = { pixivRepository.api.getNextNovelSeriesDetail(it) },
            )
        }
    }

    fun loadMore() {
        viewModelScope.launch { paged.loadMore() }
    }

    fun toggleNovelFavorite(novelId: Long, nowFavorite: Boolean) =
        favoriteActions.toggleNovelFavoriteSilent(viewModelScope, novelId, nowFavorite)

    /** best-effort：失败保留内嵌值。 */
    private fun loadAuthorFollowState(userId: Long) {
        viewModelScope.launch {
            runCatching { pixivRepository.api.getUserDetail(userId) }
                .onSuccess { resp ->
                    resp.user?.is_followed?.let { followed ->
                        _authorFollowState.value = if (followed) ToggleUiState.ON else ToggleUiState.OFF
                        favoriteActions.setUserFollowState(userId, followed)
                    }
                }
        }
    }

    fun toggleFollowAuthor() {
        val userId = _detail.value?.user?.id ?: return
        runToggle(
            _authorFollowState,
            CoreR.string.core_msg_followed_author,
            CoreR.string.core_msg_unfollowed,
        ) { favoriteActions.toggleFollowUser(userId, it) }
    }

    /** 断网自动入队待同步。 */
    fun toggleWatchlist() {
        runToggle(
            _watchlistState,
            CoreR.string.core_msg_watching_added,
            CoreR.string.core_msg_watching_removed,
        ) { favoriteActions.toggleNovelWatchlist(seriesId, it) }
    }

    /** 复用已加载分页数据，避免重复请求。 */
    fun ensureAllChaptersLoaded() {
        if (_allChapters.value.isNotEmpty()) return
        // 先用已加载分册作快照，弹窗立即有内容
        val initial = paged.items.value
        if (initial.isNotEmpty()) _allChapters.value = initial
        // hasMore=false（如小系列第一页即全量）时直接复用，零额外请求
        if (!paged.hasMore.value) return

        viewModelScope.launch {
            // LinkedHashMap 按 id 去重合并初始快照与补拉结果，保持原顺序
            val result = LinkedHashMap<Long, Novel>().apply { initial.forEach { put(it.id, it) } }
            runCatching {
                // 共享游标分页，防御上限放宽到 200 页（约 2000 章）
                fetchAllSeriesChapters(pixivRepository, seriesId, maxPages = 200)
                    .forEach { result[it.id] = it }
            }.onSuccess {
                _allChapters.value = result.values.toList()
            }
        }
    }

    /** 先写好待同步索引并唯一入队 WorkManager，再按精确复合主键等待终态。 */
    fun export(format: NovelExportFormat, chapterIds: List<Long>) {
        if (_downloading.value) return
        // targetId 用系列首册（全量未加载时用分页已加载的第一本兜底）
        val firstNovel = _allChapters.value.firstOrNull() ?: paged.items.value.firstOrNull()
        val novelId = firstNovel?.id ?: return
        val scopeKey = novelScopeKey(seriesId, chapterIds.takeIf { it.isNotEmpty() })
        val workTag = DownloadQueue.workTag("novel", novelId, format.name, scopeKey)
        val entry = NovelExportQueue.pendingEntry(
            novelId = novelId,
            seriesId = seriesId,
            chapterIds = chapterIds,
            format = format,
            title = _detail.value?.title ?: firstNovel.title,
            coverUrl = firstNovel.image_urls?.medium ?: firstNovel.image_urls?.square_medium,
            seriesTitle = _detail.value?.title,
            authorName = firstNovel.user?.name,
            authorAvatarUrl = firstNovel.user?.profile_image_urls?.best(),
            wordCount = firstNovel.text_length ?: 0,
            favoriteCount = firstNovel.total_bookmarks ?: 0,
            publishDate = firstNovel.create_date,
        )
        val request = NovelExportQueue.buildRequest(
            novelId = novelId,
            seriesId = seriesId,
            chapterIds = chapterIds,
            format = format,
        )
        _downloading.value = true
        _downloadProgress.value = context.getString(R.string.novel_msg_export_queued)
        exportObserverJob?.cancel()
        exportObserverJob = viewModelScope.launch {
            try {
                // 等 pending 落库及唯一入队完成后再观察终态
                DownloadQueue.markPending(downloadEntryDao, entry)
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(workTag, ExistingWorkPolicy.KEEP, request)
                    .await()
                trySendMessage(UiMessage(R.string.novel_msg_export_queued))
                downloadEntryDao.observeStatus("novel", novelId, format.name, scopeKey).first { status ->
                    status == null ||
                        status == DownloadEntryEntity.STATUS_DONE ||
                        status == DownloadEntryEntity.STATUS_FAILED
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                trySendMessage(UiMessage(CoreR.string.core_msg_action_failed, listOf(e.message.orEmpty())))
            } finally {
                _downloading.value = false
                _downloadProgress.value = null
            }
        }
    }
}
