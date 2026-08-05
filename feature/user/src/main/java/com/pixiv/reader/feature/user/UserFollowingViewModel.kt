package com.pixiv.reader.feature.user

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.UserPreview
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * 用户关注列表页 ViewModel：拉取指定用户的关注列表（`/v1/user/following` restrict=public）。
 */
@HiltViewModel
class UserFollowingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
) : ViewModel() {

    private val userId: Long = savedStateHandle.get<Long>("userId") ?: 0L

    val paged = PagedState<UserPreview>()

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

    /** 关注 / 取关用户（nowFollowed 为目标状态，由组件回调）。 */
    fun toggleFollowUser(userId: Long, nowFollowed: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (nowFollowed) pixivRepository.api.followUser(userId, "public")
                else pixivRepository.api.unfollowUser(userId)
            }
        }
    }
}
