package com.pixiv.reader.feature.manga

import com.pixiv.api.model.Illust
import com.pixiv.reader.core.ranking.state.RankingModeInfo
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 漫画榜 5 段（日/周/月/新人/R18）。周/月/新人/R18 为通用 mode，可能混入插画；
 * pixiv 漫画专属榜仅有 `day_manga`，改这组 mode 前需确认服务端行为。
 */
@HiltViewModel
class MangaRankingViewModel @Inject constructor(
    pixivRepository: PixivRepository,
    favoriteActions: FavoriteActions,
) : IllustRankingViewModelBase(
    pixivRepository = pixivRepository,
    favoriteActions = favoriteActions,
    modes = listOf(
        RankingModeInfo(R.string.manga_ranking_day, "day_manga"),
        RankingModeInfo(R.string.manga_ranking_week, "week"),
        RankingModeInfo(R.string.manga_ranking_month, "month"),
        RankingModeInfo(R.string.manga_ranking_rookie, "week_rookie"),
        RankingModeInfo(R.string.manga_ranking_r18, "day_r18"),
    ),
)
