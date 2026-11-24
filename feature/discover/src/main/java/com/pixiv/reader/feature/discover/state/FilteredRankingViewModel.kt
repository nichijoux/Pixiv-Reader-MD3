package com.pixiv.reader.feature.discover.state

import com.pixiv.api.model.Illust
import com.pixiv.reader.core.common.R as CoreR
import com.pixiv.reader.core.ranking.state.RankingModeInfo
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.ranking.state.RankingPagedViewModel
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.feature.discover.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

private val PERIOD_MODES = listOf(
    RankingModeInfo(R.string.ranking_generic_day, "day"),
    RankingModeInfo(R.string.ranking_generic_week, "week"),
    RankingModeInfo(R.string.ranking_generic_month, "month"),
)

// 常规日/周/月榜不收录 AI 作品（illust_ai_type 恒为 1），无法客户端过滤，必须走专用 day_ai。
private val AI_MODES = listOf(
    RankingModeInfo(R.string.ranking_generic_day, "day_ai"),
)

open class FilteredRankingViewModel(
    private val pixivRepository: PixivRepository,
    private val favoriteActions: FavoriteActions,
    modes: List<RankingModeInfo>,
) : RankingPagedViewModel<Illust>(modes, keySelector = { it.id }) {

    override suspend fun loadInitialFor(paged: PagedState<Illust>, mode: String, date: String?) {
        paged.loadInitial(
            fetch = { pixivRepository.api.getRanking(mode, date) },
            fetchNext = { pixivRepository.api.getNextIllusts(it) },
        )
    }

    fun toggleIllustFavorite(illustId: Long, nowFavorite: Boolean) =
        toggleFavoriteNotified(
            nowFavorite,
            CoreR.string.core_msg_bookmarked,
            CoreR.string.core_msg_unbookmarked,
            CoreR.string.core_msg_action_failed,
        ) { favoriteActions.toggleIllustFavorite(illustId, it) }
}

@HiltViewModel
class PeriodRankingViewModel @Inject constructor(
    pixivRepository: PixivRepository,
    favoriteActions: FavoriteActions,
) : FilteredRankingViewModel(pixivRepository, favoriteActions, PERIOD_MODES)

@HiltViewModel
class AiRankingViewModel @Inject constructor(
    pixivRepository: PixivRepository,
    favoriteActions: FavoriteActions,
) : FilteredRankingViewModel(pixivRepository, favoriteActions, AI_MODES)
