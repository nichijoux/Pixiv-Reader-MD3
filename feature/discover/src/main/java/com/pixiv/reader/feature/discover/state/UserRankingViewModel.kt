package com.pixiv.reader.feature.discover.state

import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.UserPreview
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class UserRankingViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
    private val favoriteActions: FavoriteActions,
) : androidx.lifecycle.ViewModel() {

    val paged = PagedState<UserPreview>(keySelector = { it.user?.id })

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            paged.loadInitial(
                fetch = { pixivRepository.api.getRecommendedUsers() },
                fetchNext = { pixivRepository.api.getNextUsers(it) },
            )
        }
    }

    fun loadMore() {
        viewModelScope.launch { paged.loadMore() }
    }

    fun toggleFollow(userId: Long, nowFollowed: Boolean) {
        favoriteActions.toggleFollowUserSilent(viewModelScope, userId, nowFollowed)
    }
}
