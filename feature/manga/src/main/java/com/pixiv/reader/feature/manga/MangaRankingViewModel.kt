package com.pixiv.reader.feature.manga

import com.pixiv.api.model.Illust
import com.pixiv.reader.core.common.R as CoreR
import com.pixiv.reader.core.common.ui.RankingModeInfo
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.paging.RankingPagedViewModel
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 漫画排行榜 ViewModel：5 段榜单（日/周/月/新人/R18）滑动切换，
 * `GET /v1/illust/ranking?mode=&date=` 游标分页（分段/惰性加载/重试/触底骨架在
 * [RankingPagedViewModel]，支持 [selectDate] 按日期回看历史榜单）。
 *
 * 分段 mode（周/月/新人/R18 为通用 mode，可能混入插画——pixiv 漫画专属榜仅 `day_manga`）：
 * - 日榜 `day_manga` / 周榜 `week` / 月榜 `month` / 新人 `week_rookie` / R18 `day_r18`
 */
@HiltViewModel
class MangaRankingViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
    private val favoriteActions: FavoriteActions,
) : RankingPagedViewModel<Illust>(
    modes = listOf(
        RankingModeInfo(R.string.manga_ranking_day, "day_manga"),
        RankingModeInfo(R.string.manga_ranking_week, "week"),
        RankingModeInfo(R.string.manga_ranking_month, "month"),
        RankingModeInfo(R.string.manga_ranking_rookie, "week_rookie"),
        RankingModeInfo(R.string.manga_ranking_r18, "day_r18"),
    ),
) {


    /** 段数据首载：拉取指定 mode（可选历史日期）的漫画榜单第一页，翻页走 next_url。 */
    override suspend fun loadInitialFor(paged: PagedState<Illust>, mode: String, date: String?) {
        paged.loadInitial(
            fetch = { pixivRepository.api.getRanking(mode, date) },
            fetchNext = { pixivRepository.api.getNextIllusts(it) },
        )
    }

    /** 收藏 / 取消收藏插画（nowFavorite 为目标状态，由组件回调），成功/失败发通知。 */
    fun toggleIllustFavorite(illustId: Long, nowFavorite: Boolean) =
        toggleFavoriteNotified(
            nowFavorite,
            CoreR.string.core_msg_bookmarked,
            CoreR.string.core_msg_unbookmarked,
            CoreR.string.core_msg_action_failed,
        ) { favoriteActions.toggleIllustFavorite(illustId, it) }
}
