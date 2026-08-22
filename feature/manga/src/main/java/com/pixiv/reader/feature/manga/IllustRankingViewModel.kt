package com.pixiv.reader.feature.manga

import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.common.ui.RankingModeInfo
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.paging.RankingPagedViewModel
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 插画排行榜 ViewModel：7 段榜单（日/周/月/男性向/女性向/新人/R18）滑动切换，
 * `GET /v1/illust/ranking?mode=` 游标分页（分段/惰性加载/重试/触底骨架在 [RankingPagedViewModel]）。
 *
 * 分段 mode（插画专属 `day`/`day_male`/`day_female`；周/月/新人/R18 为通用 mode）：
 * - 日榜 `day` / 周榜 `week` / 月榜 `month` / 男性向 `day_male` / 女性向 `day_female` / 新人 `week_rookie` / R18 `day_r18`
 */
@HiltViewModel
class IllustRankingViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
    private val favoriteActions: FavoriteActions,
) : RankingPagedViewModel<Illust>(
    modes = listOf(
        RankingModeInfo(R.string.illust_ranking_day, "day"),
        RankingModeInfo(R.string.illust_ranking_week, "week"),
        RankingModeInfo(R.string.illust_ranking_month, "month"),
        RankingModeInfo(R.string.illust_ranking_male, "day_male"),
        RankingModeInfo(R.string.illust_ranking_female, "day_female"),
        RankingModeInfo(R.string.illust_ranking_rookie, "week_rookie"),
        RankingModeInfo(R.string.illust_ranking_r18, "day_r18"),
    ),
) {

    /** 操作通知（收藏等）：UI 侧 collect 显示 NotificationHost。 */
    private val _message = Channel<UiMessage>(Channel.BUFFERED)
    val message = _message.receiveAsFlow()

    override suspend fun loadInitialFor(paged: PagedState<Illust>, mode: String) {
        paged.loadInitial(
            fetch = { pixivRepository.api.getRanking(mode) },
            fetchNext = { pixivRepository.api.getNextIllusts(it) },
        )
    }

    /** 收藏 / 取消收藏插画（nowFavorite 为目标状态，由组件回调），成功/失败发通知。 */
    fun toggleIllustFavorite(illustId: Long, nowFavorite: Boolean) {
        viewModelScope.launch {
            favoriteActions.toggleIllustFavorite(illustId, nowFavorite)
                .onSuccess {
                    _message.send(UiMessage(if (nowFavorite) R.string.manga_msg_bookmarked else R.string.manga_msg_unbookmarked))
                }.onFailure {
                    _message.send(UiMessage(R.string.manga_msg_action_failed, listOf(it.message ?: "")))
                }
        }
    }
}
