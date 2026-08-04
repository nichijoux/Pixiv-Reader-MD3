package com.pixiv.reader.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixivapi.model.Illust
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 排行榜模式（对应 pixiv ranking mode 参数）。 */
enum class RankingMode(val value: String, val label: String) {
    DAY("day", "每日"),
    WEEK("week", "每周"),
    MONTH("month", "每月"),
    ROOKIE("week_rookie", "新人"),
    ORIGINAL("week_original", "原创"),
    DAY_MALE("day_male", "男性向"),
    DAY_FEMALE("day_female", "女性向"),
}

/**
 * 排行榜 ViewModel：按模式（每日/每周/每月/新人/原创/男/女向）切换并分页加载插画。
 * 切换模式时重新拉取第一页；触底调用 loadMore 加载下一页。
 */
@HiltViewModel
class RankingViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
) : ViewModel() {

    private val _mode = MutableStateFlow(RankingMode.DAY)
    val mode: StateFlow<RankingMode> = _mode.asStateFlow()

    val paged = PagedState<Illust>()

    init {
        load()
    }

    /** 切换排行模式：相同模式忽略，否则更新模式并重新加载。 */
    fun selectMode(mode: RankingMode) {
        if (_mode.value == mode) return
        _mode.value = mode
        load()
    }

    /** 加载更多（触底时由 UI 调用）。 */
    fun loadMore() {
        viewModelScope.launch { paged.loadMore() }
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            paged.loadInitial(
                fetch = { pixivRepository.api.getRanking(_mode.value.value) },
                fetchNext = { pixivRepository.api.getNextIllusts(it) },
            )
        }
    }
}
