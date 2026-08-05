package com.pixiv.reader.feature.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixivapi.model.Illust
import com.pixiv.reader.core.common.RankingModeInfo
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 漫画排行榜 ViewModel：5 段榜单（日/周/月/新人/R18）滑动切换，`GET /v1/illust/ranking?mode=` 游标分页。
 *
 * 分段 mode（周/月/新人/R18 为通用 mode，可能混入插画——pixiv 漫画专属榜仅 `day_manga`）：
 * - 日榜 `day_manga` / 周榜 `week` / 月榜 `month` / 新人 `week_rookie` / R18 `day_r18`
 * 切换分段时重新拉取第一页；触底调用 [loadMore]。
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

    private val _selectedValue = MutableStateFlow(modes.first().value)
    val selectedValue: StateFlow<String> = _selectedValue.asStateFlow()

    /**
     * 数据就绪版本：每次 [load] 的 `loadInitial` 完成后 +1。
     * 供 [com.pixiv.reader.core.ui.component.RankingList] 作为 [dataKey] 触发
     * 每段快照缓存与列表淡入过渡（切段后数据到位才更新，避免过渡时展示旧数据）。
     */
    private val _dataVersion = MutableStateFlow(0)
    val dataVersion: StateFlow<Int> = _dataVersion.asStateFlow()

    val paged = PagedState<Illust>()

    init {
        load()
    }

    /** 切换榜单分段：相同忽略，否则更新并重载。 */
    fun selectMode(value: String) {
        if (value == _selectedValue.value) return
        _selectedValue.value = value
        load()
    }

    fun loadMore() {
        viewModelScope.launch { paged.loadMore() }
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            paged.loadInitial(
                fetch = { pixivRepository.api.getRanking(_selectedValue.value) },
                fetchNext = { pixivRepository.api.getNextIllusts(it) },
            )
            // 数据就绪后递增版本（此时 items 已是新榜），供 RankingList 缓存快照 + 淡入过渡
            _dataVersion.update { it + 1 }
        }
    }
}