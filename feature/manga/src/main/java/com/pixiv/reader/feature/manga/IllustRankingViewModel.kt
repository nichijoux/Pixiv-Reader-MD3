package com.pixiv.reader.feature.manga

import com.pixiv.api.model.Illust
import com.pixiv.reader.core.ranking.state.RankingModeInfo
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 插画榜 7 段（日/周/月/男性向/女性向/新人/R18）。插画专属 mode：day / day_male / day_female；
 * 周/月/新人/R18 为通用 mode。
 */
@HiltViewModel
class IllustRankingViewModel @Inject constructor(
    pixivRepository: PixivRepository,
    favoriteActions: FavoriteActions,
) : IllustRankingViewModelBase(
    pixivRepository = pixivRepository,
    favoriteActions = favoriteActions,
    modes = listOf(
        RankingModeInfo(R.string.illust_ranking_day, "day"),
        RankingModeInfo(R.string.illust_ranking_week, "week"),
        RankingModeInfo(R.string.illust_ranking_month, "month"),
        RankingModeInfo(R.string.illust_ranking_male, "day_male"),
        RankingModeInfo(R.string.illust_ranking_female, "day_female"),
        RankingModeInfo(R.string.illust_ranking_rookie, "week_rookie"),
        RankingModeInfo(R.string.illust_ranking_r18, "day_r18"),
    ),
)
