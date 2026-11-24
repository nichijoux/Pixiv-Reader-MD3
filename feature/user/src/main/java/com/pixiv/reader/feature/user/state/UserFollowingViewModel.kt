package com.pixiv.reader.feature.user.state

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.UserPreview
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class UserFollowingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
    private val favoriteActions: FavoriteActions,
) : ViewModel() {

    private val userId: Long = savedStateHandle.get<Long>("userId") ?: 0L

    val paged = PagedState<UserPreview>(keySelector = { it.user?.id })

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            paged.loadInitial(
                fetch = { pixivRepository.api.getFollowingUsers(userId, "public") },
                fetchNext = { pixivRepository.api.getNextUsers(it) },
            )
        }
    }

    fun loadMore() {
        viewModelScope.launch { paged.loadMore() }
    }

    fun toggleFollowUser(userId: Long, nowFollowed: Boolean) =
        favoriteActions.toggleFollowUserSilent(viewModelScope, userId, nowFollowed)
}
