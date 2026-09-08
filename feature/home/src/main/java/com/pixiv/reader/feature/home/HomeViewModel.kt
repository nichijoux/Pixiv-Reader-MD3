package com.pixiv.reader.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.reflect.TypeToken
import com.pixiv.api.model.Illust
import com.pixiv.api.model.TrendingTag
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.feed.FeedSnapshotStore
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 首页 Tab：推荐 / 关注。 */
enum class HomeTab { RECOMMEND, FOLLOW }

/**
 * 首页 ViewModel：推荐流 / 关注流（PagedState 分页，切 Tab 懒加载）+ 热门标签横滑。
 * 收藏操作即时回调（nowFavorite 为目标状态），失败静默。
 * 首页秒开：冷启动先恢复上次快照（推荐 / 关注 / 热门标签），后台刷新成功后无感替换。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
    private val favoriteActions: FavoriteActions,
    private val snapshotStore: FeedSnapshotStore,
) : ViewModel() {

    val recommendPaged = PagedState<Illust>()
    val followingPaged = PagedState<Illust>()

    private val _trendingTags = MutableStateFlow<List<TrendingTag>>(emptyList())
    val trendingTags: StateFlow<List<TrendingTag>> = _trendingTags.asStateFlow()

    private val _tab = MutableStateFlow(HomeTab.RECOMMEND)
    val tab: StateFlow<HomeTab> = _tab.asStateFlow()

    /** 下拉刷新指示（PullToRefreshBox 用，按当前 Tab 生效）。 */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Gson 列表元素类型（快照序列化用）。 */
    private val illustListType = object : TypeToken<List<Illust>>() {}.type
    private val tagListType = object : TypeToken<List<TrendingTag>>() {}.type

    init {
        restoreSnapshots()
        loadTrendingTags()
        loadRecommend()
    }

    /** 快照预填：推荐 / 关注 / 热门标签（秒开内容；后台刷新成功后自动替换）。 */
    private fun restoreSnapshots() {
        viewModelScope.launch {
            snapshotStore.restore<Illust>(FeedSnapshotStore.KEY_HOME_RECOMMEND, illustListType)
                ?.let { (items, nextUrl) -> recommendPaged.restoreSnapshot(items, nextUrl) }
            snapshotStore.restore<Illust>(FeedSnapshotStore.KEY_HOME_FOLLOW, illustListType)
                ?.let { (items, nextUrl) -> followingPaged.restoreSnapshot(items, nextUrl) }
            snapshotStore.restore<TrendingTag>(FeedSnapshotStore.KEY_HOME_TAGS, tagListType)
                ?.let { (tags, _) -> _trendingTags.value = tags }
        }
    }

    /** 切换 Tab：对应列表为空时懒加载；快照预填（stale）时静默后台刷新（内容先展示、成功后替换）。 */
    fun selectTab(tab: HomeTab) {
        _tab.value = tab
        when (tab) {
            HomeTab.RECOMMEND -> {
                if (recommendPaged.items.value.isEmpty() && !recommendPaged.isLoading.value) {
                    loadRecommend()
                }
            }
            HomeTab.FOLLOW -> {
                val shouldLoad = followingPaged.items.value.isEmpty() || followingPaged.isStale.value
                if (shouldLoad && !followingPaged.isLoading.value) {
                    loadFollowing()
                }
            }
        }
    }

    /** 加载更多：按当前 Tab 拉取对应列表下一页。 */
    fun loadMore() {
        viewModelScope.launch {
            when (_tab.value) {
                HomeTab.RECOMMEND -> recommendPaged.loadMore()
                HomeTab.FOLLOW -> followingPaged.loadMore()
            }
        }
    }

    /** 重试：按当前 Tab 重新加载。 */
    fun retry() {
        when (_tab.value) {
            HomeTab.RECOMMEND -> loadRecommend()
            HomeTab.FOLLOW -> loadFollowing()
        }
    }

    private fun loadRecommend() {
        viewModelScope.launch {
            recommendPaged.loadInitial(
                fetch = {
                    pixivRepository.api.getRecommendedIllusts(includeRanking = true).also { page ->
                        snapshotStore.save(FeedSnapshotStore.KEY_HOME_RECOMMEND, page.items, illustListType, page.nextPageUrl)
                    }
                },
                fetchNext = { pixivRepository.api.getNextIllusts(it) },
            )
        }
    }

    private fun loadFollowing() {
        viewModelScope.launch {
            followingPaged.loadInitial(
                fetch = {
                    pixivRepository.api.getFollowingIllusts("all").also { page ->
                        snapshotStore.save(FeedSnapshotStore.KEY_HOME_FOLLOW, page.items, illustListType, page.nextPageUrl)
                    }
                },
                fetchNext = { pixivRepository.api.getNextIllusts(it) },
            )
        }
    }

    /** 下拉刷新：重拉当前 Tab 第一页（清空旧列表），结束后复位指示（防重入）。 */
    fun pullRefresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                when (_tab.value) {
                    HomeTab.RECOMMEND -> {
                        recommendPaged.reset()
                        recommendPaged.loadInitial(
                            fetch = { pixivRepository.api.getRecommendedIllusts(includeRanking = true) },
                            fetchNext = { pixivRepository.api.getNextIllusts(it) },
                        )
                    }
                    HomeTab.FOLLOW -> {
                        followingPaged.reset()
                        followingPaged.loadInitial(
                            fetch = { pixivRepository.api.getFollowingIllusts("all") },
                            fetchNext = { pixivRepository.api.getNextIllusts(it) },
                        )
                    }
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** 加载热门标签（横滑区，取前 10 个；失败静默；快照命中时不请求）。 */
    private fun loadTrendingTags() {
        viewModelScope.launch {
            runCatching { pixivRepository.api.getTrendingTags("illust") }
                .onSuccess { tags ->
                    val top = tags.trend_tags.take(10)
                    _trendingTags.value = top
                    snapshotStore.save(FeedSnapshotStore.KEY_HOME_TAGS, top, tagListType)
                }
        }
    }

    /** 收藏 / 取消收藏插画（nowFavorite 为目标状态，由组件回调）。 */
    fun toggleIllustFavorite(illustId: Long, nowFavorite: Boolean) =
        favoriteActions.toggleIllustFavoriteSilent(viewModelScope, illustId, nowFavorite)
}
