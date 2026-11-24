package com.pixiv.reader.feature.manga

import com.pixiv.api.model.Illust
import com.pixiv.reader.core.common.R as CoreR
import com.pixiv.reader.core.ranking.state.RankingModeInfo
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.ranking.state.RankingPagedViewModel
import com.pixiv.reader.core.network.session.PixivRepository

/**
 * 插画/漫画榜共享基类：两榜拉取（`GET /v1/illust/ranking?mode=&date=`）与收藏切换完全一致，
 * 子类只提供各自 mode 列表与 Hilt 装配。
 */
abstract class IllustRankingViewModelBase(
    private val pixivRepository: PixivRepository,
    private val favoriteActions: FavoriteActions,
    modes: List<RankingModeInfo>,
) : RankingPagedViewModel<Illust>(modes, keySelector = { it.id }) {

    /** 段数据首载：翻页一律走 next_url，不自行拼 mode/date。 */
    override suspend fun loadInitialFor(paged: PagedState<Illust>, mode: String, date: String?) {
        paged.loadInitial(
            fetch = { pixivRepository.api.getRanking(mode, date) },
            fetchNext = { pixivRepository.api.getNextIllusts(it) },
        )
    }

    /** nowFavorite 是目标状态（组件回调传入）。 */
    fun toggleIllustFavorite(illustId: Long, nowFavorite: Boolean) =
        toggleFavoriteNotified(
            nowFavorite,
            CoreR.string.core_msg_bookmarked,
            CoreR.string.core_msg_unbookmarked,
            CoreR.string.core_msg_action_failed,
        ) { favoriteActions.toggleIllustFavorite(illustId, it) }
}
