package com.pixiv.reader.feature.discover.state

import com.pixiv.api.model.Illust
import com.pixiv.reader.core.common.R as CoreR
import com.pixiv.reader.core.common.ui.RankingModeInfo
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.paging.RankingPagedViewModel
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.feature.discover.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 「排行数据 + 客户端过滤」榜单 ViewModel（AI 榜 / 壁纸榜 / 年代榜三页共用，按
 * backstack entry 各自实例化、各自缓存）：复用常规 illust 排行接口（日/周/月 × 历史日期），
 * 过滤谓词由各页 UI 传入（RankingList.filter，保留真实名次）。
 */
@HiltViewModel
class DiscoverRankingViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
    private val favoriteActions: FavoriteActions,
) : RankingPagedViewModel<Illust>(
    modes = listOf(
        RankingModeInfo(R.string.ranking_generic_day, "day"),
        RankingModeInfo(R.string.ranking_generic_week, "week"),
        RankingModeInfo(R.string.ranking_generic_month, "month"),
    ),
) {

    /** 段数据首载：拉取指定 mode（可选历史日期）的插画榜单第一页，翻页走 next_url。 */
    override suspend fun loadInitialFor(paged: com.pixiv.reader.core.network.paging.PagedState<Illust>, mode: String, date: String?) {
        paged.loadInitial(
            fetch = { pixivRepository.api.getRanking(mode, date) },
            fetchNext = { pixivRepository.api.getNextIllusts(it) },
        )
    }

    /** 收藏 / 取消收藏插画（成功/失败发通知）。 */
    fun toggleIllustFavorite(illustId: Long, nowFavorite: Boolean) =
        toggleFavoriteNotified(
            nowFavorite,
            CoreR.string.core_msg_bookmarked,
            CoreR.string.core_msg_unbookmarked,
            CoreR.string.core_msg_action_failed,
        ) { favoriteActions.toggleIllustFavorite(illustId, it) }
}
