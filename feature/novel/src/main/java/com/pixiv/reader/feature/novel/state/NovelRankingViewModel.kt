package com.pixiv.reader.feature.novel.state

import com.pixiv.api.model.Novel
import com.pixiv.reader.core.common.ui.RankingModeInfo
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.paging.RankingPagedViewModel
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.feature.novel.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 小说排行榜 ViewModel：6 段榜单（日榜/周榜/男性向/女性向/新人/R18）滑动切换，
 * `GET /v1/novel/ranking?mode=` 游标分页（分段/惰性加载/重试/触底骨架在 [RankingPagedViewModel]，
 * mode 与插画榜通用，无小说专属 mode）。
 */
@HiltViewModel
class NovelRankingViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
    private val favoriteActions: FavoriteActions,
) : RankingPagedViewModel<Novel>(
    modes = listOf(
        RankingModeInfo(R.string.novel_ranking_day, "day"),
        RankingModeInfo(R.string.novel_ranking_week, "week"),
        RankingModeInfo(R.string.novel_ranking_male, "day_male"),
        RankingModeInfo(R.string.novel_ranking_female, "day_female"),
        RankingModeInfo(R.string.novel_ranking_rookie, "week_rookie"),
        RankingModeInfo(R.string.novel_ranking_r18, "day_r18"),
    ),
) {

    /** 操作通知（收藏等）：UI 侧 collect 显示 NotificationHost。 */
    private val _message = Channel<UiMessage>(Channel.BUFFERED)
    val message = _message.receiveAsFlow()

    override suspend fun loadInitialFor(paged: PagedState<Novel>, mode: String) {
        paged.loadInitial(
            fetch = { pixivRepository.api.getRankingNovels(mode) },
            fetchNext = { pixivRepository.api.getNextNovels(it) },
        )
    }

    /** 收藏 / 取消收藏小说（nowFavorite 为目标状态，由组件回调），成功/失败发通知。 */
    fun toggleNovelFavorite(novelId: Long, nowFavorite: Boolean) {
        viewModelScope.launch {
            favoriteActions.toggleNovelFavorite(novelId, nowFavorite)
                .onSuccess {
                    _message.send(UiMessage(if (nowFavorite) R.string.novel_msg_bookmarked else R.string.novel_msg_unbookmarked))
                }.onFailure {
                    _message.send(UiMessage(R.string.novel_msg_action_failed, listOf(it.message ?: "")))
                }
        }
    }
}
