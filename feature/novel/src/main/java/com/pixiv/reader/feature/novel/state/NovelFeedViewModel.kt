package com.pixiv.reader.feature.novel.state

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.Novel
import com.pixiv.api.model.WatchlistSeries
import com.pixiv.reader.core.common.NovelDefaultTab
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.datastore.UserPreferences
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.core.network.session.SeriesDetailCache
import com.pixiv.reader.core.network.session.SeriesDetailInfo
import com.pixiv.reader.feature.novel.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 小说 Tab 推荐流（P4）+ 关注流 + 默认页偏好（第五十三/五十四轮）。
 * 推荐接口：v1/novel/recommended（带 next_url 游标分页）；关注接口：v1/novel/follow（公开关注）。
 */
@HiltViewModel
class NovelFeedViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
    private val userPreferences: UserPreferences,
    private val seriesDetailCache: SeriesDetailCache,
    private val favoriteActions: FavoriteActions,
) : ViewModel() {

    /** 追更 Tab 调试日志 tag（排查隐藏系列闪退用：`adb logcat -s NovelWatchlist`）。 */
    private companion object {
        const val TAG = "NovelWatchlist"
    }

    /** 推荐 Tab：v1/novel/recommended 游标分页。 */
    val feed = PagedState<Novel>()

    /** 关注 Tab：v1/novel/follow?restrict=public 游标分页（数据驻留 VM，切回不重复请求）。 */
    val follow = PagedState<Novel>()

    /** 关注流是否已触发首次加载（防切回重复请求）。 */
    private var followInitialized = false

    /** 追更 Tab：v1/watchlist/novel 已追更小说系列游标分页（数据驻留 VM，切回不重复请求）。 */
    val watchlist = PagedState<WatchlistSeries>()

    /** 追更流是否已触发首次加载（防切回重复请求）。 */
    private var watchlistInitialized = false

    /** 追更列表系列详情：seriesId → 封面/简介/连载状态（SeriesDetailCache 内存缓存 + in-flight 去重）。 */
    private val _watchlistInfos = MutableStateFlow<Map<Long, SeriesDetailInfo>>(emptyMap())
    val watchlistInfos: StateFlow<Map<Long, SeriesDetailInfo>> = _watchlistInfos.asStateFlow()

    /** 推荐流下拉刷新指示。 */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** 关注流下拉刷新指示。 */
    private val _isFollowRefreshing = MutableStateFlow(false)
    val isFollowRefreshing: StateFlow<Boolean> = _isFollowRefreshing.asStateFlow()

    /** 追更流下拉刷新指示。 */
    private val _isWatchlistRefreshing = MutableStateFlow(false)
    val isWatchlistRefreshing: StateFlow<Boolean> = _isWatchlistRefreshing.asStateFlow()

    /** 小说 Tab 默认页偏好（我的页-浏览设置）：推荐 / 关注。 */
    val novelDefaultTab: StateFlow<NovelDefaultTab> = userPreferences.novelDefaultTab
        .stateIn(viewModelScope, SharingStarted.Eagerly, NovelDefaultTab.RECOMMEND)

    /** 操作通知（收藏等）：UI 侧 collect 显示 NotificationHost。 */
    private val _message = Channel<UiMessage>(Channel.BUFFERED)
    val message = _message.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            feed.reset()
            feed.loadInitial(
                fetch = { pixivRepository.api.getRecommendedNovels() },
                fetchNext = { pixivRepository.api.getNextNovels(it) },
            )
        }
    }

    fun loadMore() {
        viewModelScope.launch { feed.loadMore() }
    }

    /** 推荐流下拉刷新：重拉第一页，结束后复位指示（防重入）。 */
    fun pullRefresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                feed.reset()
                feed.loadInitial(
                    fetch = { pixivRepository.api.getRecommendedNovels() },
                    fetchNext = { pixivRepository.api.getNextNovels(it) },
                )
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** 关注 Tab 首次进入时加载（幂等）；失败可重试。 */
    fun ensureFollowLoaded() {
        if (followInitialized) return
        followInitialized = true
        viewModelScope.launch {
            follow.loadInitial(
                fetch = { pixivRepository.api.getFollowingNovels("public") },
                fetchNext = { pixivRepository.api.getNextNovels(it) },
            )
        }
    }

    fun refreshFollow() {
        followInitialized = true
        viewModelScope.launch {
            follow.reset()
            follow.loadInitial(
                fetch = { pixivRepository.api.getFollowingNovels("public") },
                fetchNext = { pixivRepository.api.getNextNovels(it) },
            )
        }
    }

    fun loadMoreFollow() {
        viewModelScope.launch { follow.loadMore() }
    }

    /** 关注流下拉刷新：重拉第一页，结束后复位指示（防重入）。 */
    fun pullRefreshFollow() {
        if (_isFollowRefreshing.value) return
        viewModelScope.launch {
            _isFollowRefreshing.value = true
            try {
                follow.reset()
                follow.loadInitial(
                    fetch = { pixivRepository.api.getFollowingNovels("public") },
                    fetchNext = { pixivRepository.api.getNextNovels(it) },
                )
            } finally {
                _isFollowRefreshing.value = false
            }
        }
    }

    /** 追更 Tab 首次进入时加载（幂等）；失败可重试。 */
    fun ensureWatchlistLoaded() {
        if (watchlistInitialized) return
        watchlistInitialized = true
        Log.d(TAG, "ensureWatchlistLoaded: 首次进入追更 Tab，开始加载")
        viewModelScope.launch {
            watchlist.loadInitial(
                fetch = { pixivRepository.api.getWatchlistNovel() },
                fetchNext = { pixivRepository.api.getNextWatchlist(it) },
            )
            logWatchlistPage("首载完成")
            loadWatchlistInfos()
        }
    }

    /** 追更流重试 / 下拉刷新重拉第一页（非刷新手势入口）。 */
    fun refreshWatchlist() {
        watchlistInitialized = true
        Log.d(TAG, "refreshWatchlist: 重拉第一页")
        viewModelScope.launch {
            watchlist.reset()
            watchlist.loadInitial(
                fetch = { pixivRepository.api.getWatchlistNovel() },
                fetchNext = { pixivRepository.api.getNextWatchlist(it) },
            )
            logWatchlistPage("重拉第一页完成")
            loadWatchlistInfos()
        }
    }

    /** 追更流下拉刷新：重拉第一页，结束后复位指示（防重入）。 */
    fun pullRefreshWatchlist() {
        if (_isWatchlistRefreshing.value) return
        viewModelScope.launch {
            _isWatchlistRefreshing.value = true
            try {
                watchlist.reset()
                watchlist.loadInitial(
                    fetch = { pixivRepository.api.getWatchlistNovel() },
                    fetchNext = { pixivRepository.api.getNextWatchlist(it) },
                )
                logWatchlistPage("下拉刷新完成")
                loadWatchlistInfos()
            } finally {
                _isWatchlistRefreshing.value = false
            }
        }
    }

    fun loadMoreWatchlist() {
        viewModelScope.launch {
            watchlist.loadMore()
            logWatchlistPage("加载更多完成")
            loadWatchlistInfos()
        }
    }

    /** 追更列表页状态日志：当前条目数 / 被隐藏（masked）系列数 / 分页状态。 */
    private fun logWatchlistPage(step: String) {
        val items = watchlist.items.value
        Log.d(
            TAG,
            "$step: items=${items.size} masked=${items.count { it.isMasked }} " +
                "hasMore=${watchlist.hasMore.value} error=${watchlist.error.value?.take(80) ?: "null"} " +
                "maskedIds=${items.filter { it.isMasked }.map { it.id }}",
        )
    }

    /**
     * 为追更列表批量取系列详情（复用 SeriesDetailCache 内存缓存 + in-flight 去重，
     * 与用户页系列列表同一缓存；封面/简介/连载状态同源自一次 `getNovelSeries`）。
     * 列表项无这些字段，已缓存的零请求；并发限 6，避免首屏一批详情请求打满连接池。
     *
     * 隐藏（masked）系列：`getNovelSeries` 可能抛异常（非 2xx 等），此前异常经
     * `getOrFetch` 重抛到本函数再上抛到 launch 未捕获 → 闪退。现逐系列 try-catch：
     * 失败打 Log.e 留痕并视为无详情（UI 空兜底），不再中断整批/崩溃。
     */
    private suspend fun loadWatchlistInfos() {
        val missing = watchlist.items.value.map { it.id }
            .filter { seriesDetailCache.get(it) == null }
        if (missing.isEmpty()) return
        Log.d(TAG, "loadWatchlistInfos: 待取详情 ${missing.size} 个: $missing")
        missing.chunked(6).forEach { batch ->
            val results = batch.map { id ->
                id to runCatching {
                    seriesDetailCache.getOrFetch(id) { fetchSeriesDetail(id) }
                }.getOrElse { e ->
                    Log.e(TAG, "getOrFetch(series=$id) 异常: ${e.message}", e)
                    null
                }
            }
            val newMap = _watchlistInfos.value.toMutableMap()
            results.forEach { (id, info) ->
                if (info != null) {
                    newMap[id] = info
                } else {
                    Log.w(TAG, "loadWatchlistInfos: series=$id 详情为 null（跳过，卡片用兜底展示）")
                }
            }
            _watchlistInfos.value = newMap
        }
    }

    /**
     * 单个系列详情请求（日志包裹）：成功打响应摘要，异常打堆栈并返回 null
     * （隐藏系列常见此路径，日志用于定位其 API 失败形态）。
     */
    private suspend fun fetchSeriesDetail(seriesId: Long): SeriesDetailInfo? {
        return try {
            val resp = pixivRepository.api.getNovelSeries(seriesId)
            Log.d(
                TAG,
                "getNovelSeries($seriesId) 成功: " +
                    "detail.id=${resp.novel_series_detail?.id} " +
                    "detail.title=${resp.novel_series_detail?.title} " +
                    "is_concluded=${resp.novel_series_detail?.is_concluded} " +
                    "first=${resp.novel_series_first_novel?.id} " +
                    "latest=${resp.novel_series_latest_novel?.id}",
            )
            SeriesDetailInfo(
                coverUrl = resp.novel_series_first_novel?.image_urls?.medium,
                caption = resp.novel_series_detail?.caption,
                isConcluded = resp.novel_series_detail?.is_concluded,
                totalChars = resp.novel_series_detail?.total_character_count ?: 0,
                updatedAt = resp.novel_series_latest_novel?.create_date,
            )
        } catch (e: Exception) {
            Log.e(TAG, "getNovelSeries($seriesId) 请求异常: ${e.message}", e)
            null
        }
    }

    /** 读取小说 Tab 默认页（首帧定位用，取真实落盘值而非 stateIn 占位）。 */
    suspend fun loadDefaultTab(): NovelDefaultTab = userPreferences.novelDefaultTab.first()

    /** 收藏 / 取消收藏小说（nowFavorite 为目标状态，由组件回调），成功/失败发通知。 */
    fun toggleNovelFavorite(novelId: Long, nowFavorite: Boolean) {
        viewModelScope.launch {
            favoriteActions.toggleNovelFavorite(novelId, nowFavorite)
                .onSuccess {
                    _message.send(UiMessage(if (nowFavorite) R.string.novel_msg_bookmarked else R.string.novel_msg_unbookmarked))
                }.onFailure {
                    _message.send(UiMessage(R.string.novel_msg_action_failed, listOf(it.message ?: "")))
                }
        }
    }
}
