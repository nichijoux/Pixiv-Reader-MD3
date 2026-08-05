package com.pixiv.reader.feature.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.common.RankingModeInfo
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * 漫画排行榜 ViewModel：5 段榜单（日/周/月/新人/R18）滑动切换，`GET /v1/illust/ranking?mode=` 游标分页。
 *
 * 每段**独立** [PagedState]（[stateFor] 惰性创建并缓存，数据驻留 VM——滑动切回/配置变更不丢）；
 * 段首次进入时才加载（[onPageSelected] 幂等），失败可 [retry]，触底 [loadMore]。
 *
 * 分段 mode（周/月/新人/R18 为通用 mode，可能混入插画——pixiv 漫画专属榜仅 `day_manga`）：
 * - 日榜 `day_manga` / 周榜 `week` / 月榜 `month` / 新人 `week_rookie` / R18 `day_r18`
 */
@HiltViewModel
class MangaRankingViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
) : ViewModel() {

    val modes = listOf(
        RankingModeInfo(R.string.manga_ranking_day, "day_manga"),
        RankingModeInfo(R.string.manga_ranking_week, "week"),
        RankingModeInfo(R.string.manga_ranking_month, "month"),
        RankingModeInfo(R.string.manga_ranking_rookie, "week_rookie"),
        RankingModeInfo(R.string.manga_ranking_r18, "day_r18"),
    )

    /** 各段独立分页状态：mode → PagedState，首次访问时创建并驻留。 */
    private val pages = mutableMapOf<String, PagedState<Illust>>()

    /** 已触发过首次加载的段（防止滑动切回重复请求）。 */
    private val initialized = mutableSetOf<String>()

    init {
        ensureLoaded(modes.first().value)
    }

    /** 返回某段的分页状态（惰性创建，每次调用返回同一实例）。 */
    fun stateFor(value: String): PagedState<Illust> = pages.getOrPut(value) { PagedState() }

    /** 滑动/点 Tab 切到某段：仅首次进入才加载（数据已驻留则不重复请求）。 */
    fun onPageSelected(value: String) = ensureLoaded(value)

    /** 某段加载失败重试（始终重拉该段第一页）。 */
    fun retry(value: String) {
        initialized += value
        viewModelScope.launch { loadInitialFor(stateFor(value), value) }
    }

    /** 某段触底加载下一页。 */
    fun loadMore(value: String) {
        viewModelScope.launch { stateFor(value).loadMore() }
    }

    private fun ensureLoaded(value: String) {
        if (!initialized.add(value)) return
        viewModelScope.launch { loadInitialFor(stateFor(value), value) }
    }

    private suspend fun loadInitialFor(paged: PagedState<Illust>, mode: String) {
        paged.loadInitial(
            fetch = { pixivRepository.api.getRanking(mode) },
            fetchNext = { pixivRepository.api.getNextIllusts(it) },
        )
    }
}
