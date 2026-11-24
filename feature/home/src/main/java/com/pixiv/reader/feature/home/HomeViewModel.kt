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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class HomeTab { RECOMMEND, FOLLOW }

/**
 * 推荐流 / 关注流 + 热门标签。冷启动先恢复上次快照（推荐/关注/标签），后台刷新成功后无感替换。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
    private val favoriteActions: FavoriteActions,
    private val snapshotStore: FeedSnapshotStore,
) : ViewModel() {

    val recommendPaged = PagedState<Illust>(keySelector = { it.id })
    val followingPaged = PagedState<Illust>(keySelector = { it.id })

    private val _trendingTags = MutableStateFlow<List<TrendingTag>>(emptyList())
    val trendingTags: StateFlow<List<TrendingTag>> = _trendingTags.asStateFlow()

    private val _tab = MutableStateFlow(HomeTab.RECOMMEND)
    val tab: StateFlow<HomeTab> = _tab.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val illustListType = object : TypeToken<List<Illust>>() {}.type
    private val tagListType = object : TypeToken<List<TrendingTag>>() {}.type

    /** 网络首载前要等它完成，否则旧快照晚到会与真实首页竞态。 */
    private val snapshotRestoreJob: Job

    init {
        snapshotRestoreJob = restoreSnapshots()
        loadTrendingTags()
        viewModelScope.launch {
            snapshotRestoreJob.join()
            loadRecommend().join()
        }
    }

    private fun restoreSnapshots(): Job = viewModelScope.launch {
        snapshotStore.restore<Illust>(FeedSnapshotStore.KEY_HOME_RECOMMEND, illustListType)
            ?.let { (items, nextUrl) -> recommendPaged.restoreSnapshot(items, nextUrl) }
        snapshotStore.restore<Illust>(FeedSnapshotStore.KEY_HOME_FOLLOW, illustListType)
            ?.let { (items, nextUrl) -> followingPaged.restoreSnapshot(items, nextUrl) }
        snapshotStore.restore<TrendingTag>(FeedSnapshotStore.KEY_HOME_TAGS, tagListType)
            ?.let { (tags, _) -> _trendingTags.value = tags }
    }

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

    fun loadMore() {
        viewModelScope.launch {
            when (_tab.value) {
                HomeTab.RECOMMEND -> recommendPaged.loadMore()
                HomeTab.FOLLOW -> followingPaged.loadMore()
            }
        }
    }

    fun retry() {
        when (_tab.value) {
            HomeTab.RECOMMEND -> loadRecommend()
            HomeTab.FOLLOW -> loadFollowing()
        }
    }

    /**
     * fetch 内含快照回写；返回 [Job] 供调用方 join 等网络真正结束。
     */
    private fun loadRecommend(): Job {
        return viewModelScope.launch {
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

    /**
     * fetch 内含快照回写；返回 [Job] 供调用方 join 等网络真正结束。
     */
    private fun loadFollowing(): Job {
        return viewModelScope.launch {
            snapshotRestoreJob.join()
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

    /**
     * 重拉当前 Tab 第一页，等网络结束后才复位指示（防重入）。复用 load* 使其 fetch 顺带回写快照——
     * 刷新后的数据即下次冷启动的秒开内容。
     */
    fun pullRefresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                when (_tab.value) {
                    HomeTab.RECOMMEND -> {
                        recommendPaged.reset()
                        // join 等待首屏网络完成，否则指示会在请求返回前提前消失
                        loadRecommend().join()
                    }

                    HomeTab.FOLLOW -> {
                        followingPaged.reset()
                        loadFollowing().join()
                    }
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

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

    /** nowFavorite 是目标状态（组件回调传入）。 */
    fun toggleIllustFavorite(illustId: Long, nowFavorite: Boolean) =
        favoriteActions.toggleIllustFavoriteSilent(viewModelScope, illustId, nowFavorite)
}
