package com.pixiv.reader.feature.novel.state

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 小说系列详情页 ViewModel：系列信息 + 分册列表（`/v2/novel/series` 分页）。
 * 系列封面（第一册 medium）走 [SeriesDetailCache]，与用户主页系列列表共享缓存。
 * 关注作者：内嵌 `user.is_followed` 初始 + `getUserDetail` 权威刷新（与详情页一致）。
 * 追更：detail.watchlist_added 初始 + [toggleWatchlist] 乐观翻转（与漫画系列页/小说详情页同模式）。
 * 导出（worker）：整系列 / 部分分册（合并为一个文件），完成/失败观察下载索引发应用内通知。
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

    /** 系列第一册封面 URL（系列无独立封面，用首册 `image_urls` 兜底；null 表示无可用图）。 */
    private val _firstNovelCover = MutableStateFlow<String?>(null)
    val firstNovelCover: StateFlow<String?> = _firstNovelCover.asStateFlow()

    val paged = PagedState<Novel>()

    // ── 关注作者 / 追更（ToggleUiState 状态机） ────────────────────────────────

    /** 作者关注状态机（内嵌 is_followed 初始 + getUserDetail 权威刷新；[toggleFollowAuthor] 驱动）。 */
    private val _authorFollowState = MutableStateFlow(ToggleUiState.OFF)
    val authorFollowState: StateFlow<ToggleUiState> = _authorFollowState.asStateFlow()

    /** 追更状态机（detail.watchlist_added 初始化；[toggleWatchlist] 驱动转移）。 */
    private val _watchlistState = MutableStateFlow(ToggleUiState.OFF)
    val watchlistState: StateFlow<ToggleUiState> = _watchlistState.asStateFlow()

    // ── 下载 / 导出 ──────────────────────────────────────────────────────────

    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow<String?>(null)
    val downloadProgress: StateFlow<String?> = _downloadProgress.asStateFlow()

    /** 系列全量分册（供「选取部分」下载；首次按需拉取并缓存，非分页）。 */
    private val _allChapters = MutableStateFlow<List<Novel>>(emptyList())
    val allChapters: StateFlow<List<Novel>> = _allChapters.asStateFlow()

    init {
        // 仅路由场景自动加载（seriesId 非 0）；pane 场景等待 switchTo 指定目标
        if (seriesId != 0L) load()
    }

    /**
     * pane 场景切换加载目标（类比 [NovelViewModel.switchTo]）：重置全部状态后重新加载。
     * 幂等：同 id 且已有详情时不重载（重组 / 重复点击不触发多余请求）。
     *
     * @param id 目标系列 id
     * @return 无返回值
     */
    fun switchTo(id: Long) {
        if (id == seriesId && _detail.value != null) return
        seriesId = id
        // 重置状态：详情 / 封面 / 关注与追更状态机 / 全量分册清空，分页游标重置防旧系列数据串页
        _detail.value = null
        _firstNovelCover.value = null
        _authorFollowState.value = ToggleUiState.OFF
        _watchlistState.value = ToggleUiState.OFF
        _allChapters.value = emptyList()
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

    /** 收藏 / 取消收藏小说（nowFavorite 为目标状态，由组件回调）。 */
    fun toggleNovelFavorite(novelId: Long, nowFavorite: Boolean) =
        favoriteActions.toggleNovelFavoriteSilent(viewModelScope, novelId, nowFavorite)

    /** 用 user/detail 权威刷新作者关注态（best-effort，失败保留内嵌值）。 */
    private fun loadAuthorFollowState(userId: Long) {
        viewModelScope.launch {
            runCatching { pixivRepository.api.getUserDetail(userId) }
                .onSuccess { resp ->
                    resp.user?.is_followed?.let {
                        _authorFollowState.value = if (it) ToggleUiState.ON else ToggleUiState.OFF
                    }
                }
        }
    }

    /**
     * 关注 / 取关作者（系列页作者行按钮，[ToggleUiState] 状态机：进行中保留旧文案禁用防连点，
     * 失败回滚；经 FavoriteActions 统一收口）。
     *
     * @return 无返回值（操作完成后结束的协程）
     */
    fun toggleFollowAuthor() {
        val userId = _detail.value?.user?.id ?: return
        runToggle(
            _authorFollowState,
            CoreR.string.core_msg_followed_author,
            CoreR.string.core_msg_unfollowed,
        ) { favoriteActions.toggleFollowUser(userId, it) }
    }

    /**
     * 追更 / 取消追更当前系列（信息头按钮，[ToggleUiState] 状态机：进行中保留旧文案
     * 禁用防连点，失败回滚；成功经消息通道提示；经 FavoriteActions 统一收口，断网自动入队待同步）。
     *
     * @return 无返回值（操作完成后结束）
     */
    fun toggleWatchlist() {
        runToggle(
            _watchlistState,
            CoreR.string.core_msg_watching_added,
            CoreR.string.core_msg_watching_removed,
        ) { favoriteActions.toggleNovelWatchlist(seriesId, it) }
    }

    /** 拉取系列全量分册（供「选取部分下载」多选；复用已加载分页数据，避免重复请求）。 */
    fun ensureAllChaptersLoaded() {
        if (_allChapters.value.isNotEmpty()) return
        // 复用 paged 已加载的分册作初始快照（含触底已加载的）：弹窗立即有内容，不空白等待
        val initial = paged.items.value
        if (initial.isNotEmpty()) _allChapters.value = initial
        // 分页已全部加载完（hasMore=false，如小系列第一页即全量）：直接复用，零额外请求
        if (!paged.hasMore.value) return

        viewModelScope.launch {
            // 大系列补拉剩余页：LinkedHashMap 按 id 去重（初始快照 + 补拉合并，保持顺序）
            val result = LinkedHashMap<Long, Novel>().apply { initial.forEach { put(it.id, it) } }
            runCatching {
                // 共享游标分页（core:network），防御上限放宽到 200 页（约 2000 章）
                fetchAllSeriesChapters(pixivRepository, seriesId, maxPages = 200)
                    .forEach { result[it.id] = it }
            }.onSuccess {
                _allChapters.value = result.values.toList()
            }
        }
    }

    /** 导出小说为指定格式文件（整系列或部分分册，后台队列，支持断点续传）。
     * 入队即建「待同步」索引条目：断网停留待同步（Worker 网络约束），联网自动开始。 */
    fun export(format: NovelExportFormat, chapterIds: List<Long>) {
        if (_downloading.value) return
        // targetId 用系列首册（全量未加载时用分页已加载的第一本兜底）
        val firstNovel = _allChapters.value.firstOrNull() ?: paged.items.value.firstOrNull()
        val novelId = firstNovel?.id ?: return
        // 入队即建「待同步」条目：卡片立即可见；Worker 起跑后覆写为下载中并补全快照
        viewModelScope.launch {
            runCatching {
                DownloadQueue.markPending(
                    downloadEntryDao,
                    NovelExportQueue.pendingEntry(
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
                    ),
                )
            }
        }
        WorkManager.getInstance(context).enqueue(
            NovelExportQueue.buildRequest(
                novelId = novelId,
                seriesId = seriesId,
                chapterIds = chapterIds,
                format = format,
            )
        )
        _downloading.value = true
        _downloadProgress.value = context.getString(R.string.novel_msg_export_queued)
        trySendMessage(UiMessage(R.string.novel_msg_export_queued))
        observeExportStateReset(novelId)
    }

    /** 观察导出结束：等 done/failed 终态后复位导出中状态（待同步/下载中不复位，反映排队与进行中）。
     * 完成/失败通知由全局 DownloadCompletionNotifier 统一负责（离开页面也能收到）。 */
    private fun observeExportStateReset(id: Long) {
        viewModelScope.launch {
            downloadEntryDao.observeAll().first { entries ->
                entries.any { it.targetId == id && it.targetType == "novel" && (it.status == DownloadEntryEntity.STATUS_DONE || it.status == DownloadEntryEntity.STATUS_FAILED) }
            }
            _downloading.value = false
            _downloadProgress.value = null
        }
    }
}
