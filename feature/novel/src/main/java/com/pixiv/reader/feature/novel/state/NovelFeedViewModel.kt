package com.pixiv.reader.feature.novel.state

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.gson.reflect.TypeToken
import com.pixiv.api.model.Novel
import com.pixiv.api.model.WatchlistSeries
import com.pixiv.reader.core.common.config.NovelDefaultTab
import com.pixiv.reader.core.common.MessageType
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.common.R as CoreR
import com.pixiv.reader.core.datastore.UserPreferences
import com.pixiv.reader.core.network.message.MessageViewModel
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.feed.FeedSnapshotStore
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.core.network.session.SeriesDetailInfo
import com.pixiv.reader.core.network.session.SeriesDetailLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 冷启动先恢复推荐流快照再联网替换：秒开，且弱网/离线时快照内容仍保留。 */
@HiltViewModel
class NovelFeedViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
    private val userPreferences: UserPreferences,
    private val seriesDetailLoader: SeriesDetailLoader,
    private val favoriteActions: FavoriteActions,
    private val snapshotStore: FeedSnapshotStore,
) : MessageViewModel() {

    /** 排查隐藏系列闪退：`adb logcat -s NovelWatchlist`。 */
    private companion object {
        const val TAG = "NovelWatchlist"
    }

    private val novelListType = object : TypeToken<List<Novel>>() {}.type

    val feed = PagedState<Novel>(keySelector = { it.id })

    /** 数据驻留 VM，切回 Tab 不重复请求。 */
    val follow = PagedState<Novel>(keySelector = { it.id })

    private var followInitialized = false

    val watchlist = PagedState<WatchlistSeries>(keySelector = { it.id })

    private val pendingFavorites = mutableSetOf<Long>()

    private var watchlistInitialized = false

    private val _watchlistInfos = MutableStateFlow<Map<Long, SeriesDetailInfo>>(emptyMap())
    val watchlistInfos: StateFlow<Map<Long, SeriesDetailInfo>> = _watchlistInfos.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isFollowRefreshing = MutableStateFlow(false)
    val isFollowRefreshing: StateFlow<Boolean> = _isFollowRefreshing.asStateFlow()

    private val _isWatchlistRefreshing = MutableStateFlow(false)
    val isWatchlistRefreshing: StateFlow<Boolean> = _isWatchlistRefreshing.asStateFlow()

    val novelDefaultTab: StateFlow<NovelDefaultTab> = userPreferences.novelDefaultTab
        .stateIn(viewModelScope, SharingStarted.Eagerly, NovelDefaultTab.RECOMMEND)

    init {
        viewModelScope.launch {
            restoreFeedSnapshot().join()
            // 冷启动后台静默更新时不先 reset，确保弱网或离线时保留已恢复的快照内容。
            loadFeedInitial()
        }
    }

    private fun restoreFeedSnapshot(): Job = viewModelScope.launch {
        snapshotStore.restore<Novel>(FeedSnapshotStore.KEY_NOVEL_FEED, novelListType)
            ?.let { (items, nextUrl) -> feed.restoreSnapshot(items, nextUrl) }
    }

    fun refresh() {
        viewModelScope.launch {
            feed.reset()
            loadFeedInitial()
        }
    }

    private suspend fun loadFeedInitial() {
        feed.loadInitial(
            fetch = {
                pixivRepository.api.getRecommendedNovels().also { page ->
                    snapshotStore.save(FeedSnapshotStore.KEY_NOVEL_FEED, page.items, novelListType, page.nextPageUrl)
                }
            },
            fetchNext = { pixivRepository.api.getNextNovels(it) },
        )
    }

    fun loadMore() {
        viewModelScope.launch { feed.loadMore() }
    }

    fun pullRefresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                feed.reset()
                feed.loadInitial(
                    fetch = {
                        pixivRepository.api.getRecommendedNovels().also { page ->
                            snapshotStore.save(FeedSnapshotStore.KEY_NOVEL_FEED, page.items, novelListType, page.nextPageUrl)
                        }
                    },
                    fetchNext = { pixivRepository.api.getNextNovels(it) },
                )
            } finally {
                _isRefreshing.value = false
            }
        }
    }

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
     * 列表项无封面/简介字段，需批量补详情；并发限 6 避免首屏一批请求打满连接池。
     * 已缓存命中也要回填 [_watchlistInfos]——VM 重建后本地流为空，不回填则二次进入封面全空白。
     * 隐藏系列 getNovelSeries 会抛非 2xx 异常，必须逐条捕获，否则整批中断并闪退。
     */
    private suspend fun loadWatchlistInfos() {
        val ids = watchlist.items.value.map { it.id }
        seriesDetailLoader.backfillInfos(
            target = _watchlistInfos,
            ids = ids,
            concurrency = 6,
            onError = { id, e -> Log.e(TAG, "getOrFetch(series=$id) 异常: ${e.message}", e) },
        ) { id -> fetchSeriesDetail(id) }
    }

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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "getNovelSeries($seriesId) 请求异常: ${e.message}", e)
            null
        }
    }

    /** 取真实落盘值而非 stateIn 占位（首帧定位用）。 */
    suspend fun loadDefaultTab(): NovelDefaultTab = userPreferences.novelDefaultTab.first()

    fun toggleNovelFavorite(novelId: Long, nowFavorite: Boolean) {
        if (!pendingFavorites.add(novelId)) return
        viewModelScope.launch {
            try {
                favoriteActions.toggleNovelFavorite(novelId, nowFavorite)
                    .onSuccess {
                        sendMessage(UiMessage(if (nowFavorite) CoreR.string.core_msg_bookmarked else CoreR.string.core_msg_unbookmarked))
                    }.onFailure {
                        sendMessage(
                            UiMessage(
                                CoreR.string.core_msg_action_failed,
                                listOf(it.message ?: ""),
                                MessageType.ERROR,
                            ),
                        )
                    }
            } finally {
                pendingFavorites.remove(novelId)
            }
        }
    }
}
