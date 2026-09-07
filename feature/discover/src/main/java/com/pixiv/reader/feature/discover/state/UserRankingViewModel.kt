package com.pixiv.reader.feature.discover.state

import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.UserPreview
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * 画师榜 ViewModel：官方推荐创作者列表（`v1/user/recommended`，游标分页）。
 * 关注 / 取关走 [FavoriteActions]（不刷新列表数据，卡片内即时反馈）。
 */
@HiltViewModel
class UserRankingViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
    private val favoriteActions: FavoriteActions,
) : androidx.lifecycle.ViewModel() {

    /** 推荐创作者分页。 */
    val paged = PagedState<UserPreview>()

    init {
        load()
    }

    /** 首次加载 / 失败重试。 */
    fun load() {
        viewModelScope.launch {
            paged.loadInitial(
                fetch = { pixivRepository.api.getRecommendedUsers() },
                fetchNext = { pixivRepository.api.getNextUsers(it) },
            )
        }
    }

    /** 触底加载更多。 */
    fun loadMore() {
        viewModelScope.launch { paged.loadMore() }
    }

    /** 关注 / 取关（静默：卡片内即时反馈，失败无全局提示）。 */
    fun toggleFollow(userId: Long, nowFollowed: Boolean) {
        favoriteActions.toggleFollowUserSilent(viewModelScope, userId, nowFollowed)
    }
}
