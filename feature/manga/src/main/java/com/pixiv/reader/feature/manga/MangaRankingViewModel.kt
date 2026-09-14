package com.pixiv.reader.feature.manga

import com.pixiv.api.model.Illust
import com.pixiv.reader.core.common.ui.RankingModeInfo
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 漫画排行榜 ViewModel：5 段榜单（日/周/月/新人/R18）滑动切换。
 * 榜单拉取与收藏切换在共享基类 [IllustRankingViewModelBase]，本类仅提供漫画分段 mode。
 *
 * 分段 mode（周/月/新人/R18 为通用 mode，可能混入插画——pixiv 漫画专属榜仅 `day_manga`）：
 * - 日榜 `day_manga` / 周榜 `week` / 月榜 `month` / 新人 `week_rookie` / R18 `day_r18`
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
