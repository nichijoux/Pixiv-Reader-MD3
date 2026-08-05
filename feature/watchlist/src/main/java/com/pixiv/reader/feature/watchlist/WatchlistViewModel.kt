package com.pixiv.reader.feature.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixivapi.model.WatchlistSeries
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 追更 ViewModel：小说系列追更列表（分页）。
 */
@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
) : ViewModel() {

    private val _message = Channel<UiMessage>(Channel.BUFFERED)
    val message = _message.receiveAsFlow()

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
