package com.pixiv.reader.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixivapi.model.Illust
import com.example.pixivapi.model.TrendingTag
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class HomeTab { RECOMMEND, FOLLOW }

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
) : ViewModel() {

    val recommendPaged = PagedState<Illust>()
    val followingPaged = PagedState<Illust>()

    private val _trendingTags = MutableStateFlow<List<TrendingTag>>(emptyList())
    val trendingTags: StateFlow<List<TrendingTag>> = _trendingTags.asStateFlow()

    private val _tab = MutableStateFlow(HomeTab.RECOMMEND)
    val tab: StateFlow<HomeTab> = _tab.asStateFlow()

    init {
        loadTrendingTags()
        loadRecommend()
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
                if (followingPaged.items.value.isEmpty() && !followingPaged.isLoading.value) {
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

    private fun loadTrendingTags() {
        viewModelScope.launch {
            runCatching { pixivRepository.api.getTrendingTags("illust") }
                .onSuccess { _trendingTags.value = it.trend_tags.take(10) }
        }
    }
}
