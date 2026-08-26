package com.pixiv.reader.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.Illust
import com.pixiv.api.model.TrendingTag
import com.pixiv.reader.core.network.favorite.FavoriteActions
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
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
    private val favoriteActions: FavoriteActions,
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

    init {
        loadTrendingTags()
        loadRecommend()
    }

    /** 切换 Tab：对应列表为空时懒加载（避免每次切 Tab 都重新请求）。 */
    fun selectTab(tab: HomeTab) {
        _tab.value = tab
        when (tab) {
            HomeTab.RECOMMEND -> {
                if (recommendPaged.items.value.isEmpty() && !recommendPaged.isLoading.value) {
                    loadRecommend()
                }
            }
            HomeTab.FOLLOW -> {
                if (followingPaged.items.value.isEmpty() && !followingPaged.isLoading.value) {
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
                fetch = { pixivRepository.api.getRecommendedIllusts(includeRanking = true) },
                fetchNext = { pixivRepository.api.getNextIllusts(it) },
            )
        }
    }

    private fun loadFollowing() {
        viewModelScope.launch {
            followingPaged.loadInitial(
                fetch = { pixivRepository.api.getFollowingIllusts("all") },
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

    /** 加载热门标签（横滑区，取前 10 个；失败静默）。 */
    private fun loadTrendingTags() {
        viewModelScope.launch {
            runCatching { pixivRepository.api.getTrendingTags("illust") }
                .onSuccess { _trendingTags.value = it.trend_tags.take(10) }
        }
    }

    /** 收藏 / 取消收藏插画（nowFavorite 为目标状态，由组件回调）。 */
    fun toggleIllustFavorite(illustId: Long, nowFavorite: Boolean) =
        favoriteActions.toggleIllustFavoriteSilent(viewModelScope, illustId, nowFavorite)
}
