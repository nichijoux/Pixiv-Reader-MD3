package com.pixiv.reader.feature.manga

import com.pixiv.api.model.Illust
import com.pixiv.reader.core.common.RankingModeInfo
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.paging.RankingPagedViewModel
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 漫画排行榜 ViewModel：5 段榜单（日/周/月/新人/R18）滑动切换，
 * `GET /v1/illust/ranking?mode=` 游标分页（分段/惰性加载/重试/触底骨架在 [RankingPagedViewModel]）。
 *
 * 分段 mode（周/月/新人/R18 为通用 mode，可能混入插画——pixiv 漫画专属榜仅 `day_manga`）：
 * - 日榜 `day_manga` / 周榜 `week` / 月榜 `month` / 新人 `week_rookie` / R18 `day_r18`
 */
@HiltViewModel
class MangaRankingViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
) : RankingPagedViewModel<Illust>(
    modes = listOf(
        RankingModeInfo(R.string.manga_ranking_day, "day_manga"),
        RankingModeInfo(R.string.manga_ranking_week, "week"),
        RankingModeInfo(R.string.manga_ranking_month, "month"),
        RankingModeInfo(R.string.manga_ranking_rookie, "week_rookie"),
        RankingModeInfo(R.string.manga_ranking_r18, "day_r18"),
    ),
) {

    override suspend fun loadInitialFor(paged: PagedState<Illust>, mode: String) {
        paged.loadInitial(
            fetch = { pixivRepository.api.getRanking(mode) },
            fetchNext = { pixivRepository.api.getNextIllusts(it) },
        )
    }
}
