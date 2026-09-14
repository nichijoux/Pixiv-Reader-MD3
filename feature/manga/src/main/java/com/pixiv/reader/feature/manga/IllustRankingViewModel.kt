package com.pixiv.reader.feature.manga

import com.pixiv.api.model.Illust
import com.pixiv.reader.core.ranking.state.RankingModeInfo
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 插画排行榜 ViewModel：7 段榜单（日/周/月/男性向/女性向/新人/R18）滑动切换。
 * 榜单拉取与收藏切换在共享基类 [IllustRankingViewModelBase]，本类仅提供插画分段 mode。
 *
 * 分段 mode（插画专属 `day`/`day_male`/`day_female`；周/月/新人/R18 为通用 mode）：
 * - 日榜 `day` / 周榜 `week` / 月榜 `month` / 男性向 `day_male` / 女性向 `day_female` / 新人 `week_rookie` / R18 `day_r18`
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
