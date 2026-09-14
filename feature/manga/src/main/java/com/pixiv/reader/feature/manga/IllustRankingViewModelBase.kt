package com.pixiv.reader.feature.manga

import com.pixiv.api.model.Illust
import com.pixiv.reader.core.common.R as CoreR
import com.pixiv.reader.core.common.ui.RankingModeInfo
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.paging.RankingPagedViewModel
import com.pixiv.reader.core.network.session.PixivRepository

/**
 * 插画/漫画排行榜共享 ViewModel 基类：两者的榜单拉取（`GET /v1/illust/ranking?mode=&date=`
 * 游标分页）与收藏切换完全一致，收敛于此；子类仅提供各自分段 mode 与 Hilt 装配。
 *
 * 分页/日期筛选/重试在 [RankingPagedViewModel]，本基类补齐插画类榜单的两处具体实现。
 *
 * @param pixivRepository 网络仓库（榜单接口）
 * @param favoriteActions 收藏动作统一收口
 * @param modes 分段 mode 列表（漫画 5 段 / 插画 7 段）
 */
abstract class IllustRankingViewModelBase(
    private val pixivRepository: PixivRepository,
    private val favoriteActions: FavoriteActions,
    modes: List<RankingModeInfo>,
) : RankingPagedViewModel<Illust>(modes) {

    /** 段数据首载：拉取指定 mode（可选历史日期）的榜单第一页，翻页走 next_url。 */
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
