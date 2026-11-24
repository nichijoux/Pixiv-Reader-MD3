package com.pixiv.reader.feature.discover.state

import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.Article
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class PixivisionViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
) : androidx.lifecycle.ViewModel() {

    val paged = PagedState<Article>(keySelector = { it.id })

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            paged.loadInitial(
                fetch = { pixivRepository.api.getArticles("all") },
                fetchNext = { pixivRepository.api.getNextArticles(it) },
            )
        }
    }

    fun loadMore() {
        viewModelScope.launch { paged.loadMore() }
    }
}
