package com.pixiv.reader.feature.novel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixivapi.model.Novel
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * 小说 Tab 推荐流（P4）。
 * 推荐接口：v1/novel/recommended（带 next_url 游标分页）。
 */
@HiltViewModel
class NovelFeedViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
) : ViewModel() {

    val feed = PagedState<Novel>()

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
}
