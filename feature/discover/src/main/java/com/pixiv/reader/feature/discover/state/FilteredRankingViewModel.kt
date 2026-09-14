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

/** 日/周/月三段常规 mode（壁纸榜 / 年代榜用，可按分辨率 / 历史日期客户端过滤）。 */
private val PERIOD_MODES = listOf(
    RankingModeInfo(R.string.ranking_generic_day, "day"),
    RankingModeInfo(R.string.ranking_generic_week, "week"),
    RankingModeInfo(R.string.ranking_generic_month, "month"),
)

/**
 * AI 榜 mode：pixiv 官方 AI 生成排行榜（仅日榜，服务端已过滤出 AI 作品）。
 * 注意常规日/周/月榜不收录 AI 作品（响应中 illust_ai_type 恒为 1），客户端过滤不可行，
 * 必须使用专用 mode。
 */
private val AI_MODES = listOf(
    RankingModeInfo(R.string.ranking_generic_day, "day_ai"),
)

/**
 * 「排行数据 + 客户端过滤」榜单 ViewModel 基类（AI 榜 / 壁纸榜 / 年代榜三页共用，按
 * backstack entry 各自实例化、各自缓存）：复用 illust 排行接口（mode + 可选历史日期），
 * 分段配置由叶子类按页传入，过滤谓词由各页 UI 传入（RankingList.filter，保留真实名次）。
 *
 * @param pixivRepository 网络仓库（排行接口 accessor）
 * @param favoriteActions 收藏动作执行器（断网直接报错）
 * @param modes 分段配置（label 资源 + mode 值），经构造透传给基类（基类依赖其完成加载）
 */
open class FilteredRankingViewModel(
    private val pixivRepository: PixivRepository,
    private val favoriteActions: FavoriteActions,
    modes: List<RankingModeInfo>,
) : RankingPagedViewModel<Illust>(modes) {

    /**
     * 段数据首载：拉取指定 mode（可选历史日期）的插画榜单第一页，翻页走 next_url。
     *
     * @param paged 该段分页状态
     * @param mode 分段 mode 值（如 day / week / day_ai）
     * @param date 榜单日期（yyyy-MM-dd），null = 最新榜
     * @return 无返回值
     */
    override suspend fun loadInitialFor(paged: PagedState<Illust>, mode: String, date: String?) {
        paged.loadInitial(
            fetch = { pixivRepository.api.getRanking(mode, date) },
            fetchNext = { pixivRepository.api.getNextIllusts(it) },
        )
    }

    /**
     * 收藏 / 取消收藏插画（成功/失败发通知）。
     *
     * @param illustId 插画 id
     * @param nowFavorite 当前是否已收藏（true = 即将取消，false = 即将收藏）
     * @return 无返回值
     */
    fun toggleIllustFavorite(illustId: Long, nowFavorite: Boolean) =
        toggleFavoriteNotified(
            nowFavorite,
            CoreR.string.core_msg_bookmarked,
            CoreR.string.core_msg_unbookmarked,
            CoreR.string.core_msg_action_failed,
        ) { favoriteActions.toggleIllustFavorite(illustId, it) }
}

/**
 * 壁纸榜 / 年代榜共用 ViewModel：日/周/月三段常规排行数据，由 UI 按页传过滤谓词
 * （壁纸榜按横屏高分辨率、年代榜不过滤 + 年代快捷 chips）。
 *
 * @param pixivRepository 网络仓库
 * @param favoriteActions 收藏动作执行器
 */
@HiltViewModel
class PeriodRankingViewModel @Inject constructor(
    pixivRepository: PixivRepository,
    favoriteActions: FavoriteActions,
) : FilteredRankingViewModel(pixivRepository, favoriteActions, PERIOD_MODES)

/**
 * AI 榜 ViewModel：pixiv 官方 AI 生成日榜（`mode=day_ai`），服务端已过滤出 AI 作品，
 * 客户端不再按 illust_ai_type 过滤（常规日/周/月榜不收录 AI 作品，过滤恒为空）。
 *
 * @param pixivRepository 网络仓库
 * @param favoriteActions 收藏动作执行器
 */
@HiltViewModel
class AiRankingViewModel @Inject constructor(
    pixivRepository: PixivRepository,
    favoriteActions: FavoriteActions,
) : FilteredRankingViewModel(pixivRepository, favoriteActions, AI_MODES)
