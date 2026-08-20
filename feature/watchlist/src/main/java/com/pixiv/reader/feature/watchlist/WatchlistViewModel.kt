package com.pixiv.reader.feature.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.WatchlistSeries
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * 追更 ViewModel：小说系列追更列表（分页）。
 */
@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
) : ViewModel() {

    val watchlistPaged = PagedState<WatchlistSeries>()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            watchlistPaged.loadInitial(
                fetch = { pixivRepository.api.getWatchlistNovel() },
                fetchNext = { pixivRepository.api.getNextWatchlist(it) },
            )
        }
    }

    fun loadMore() {
        viewModelScope.launch { watchlistPaged.loadMore() }
    }
}
