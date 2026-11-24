package com.pixiv.reader.feature.manga

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.Illust
import com.pixiv.api.model.NovelSeriesDetail
import com.pixiv.reader.core.common.R as CoreR
import com.pixiv.reader.core.common.ToggleUiState
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.message.MessageViewModel
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 漫画系列详情（`v1/illust/series`）：系列详情（复用 NovelSeriesDetail，含 watchlist_added）+ 系列内作品分页。
 */
@HiltViewModel
class MangaSeriesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
    private val favoriteActions: FavoriteActions,
) : MessageViewModel() {

    /** 路由参数，固定。 */
    private val seriesId: Long = savedStateHandle.get<Long>("seriesId") ?: 0L

    private val _detail = MutableStateFlow<NovelSeriesDetail?>(null)
    val detail: StateFlow<NovelSeriesDetail?> = _detail.asStateFlow()

    /** PagedState 累积，触底加载。 */
    val paged = PagedState<Illust>(keySelector = { it.id })

    /** 由 detail.watchlist_added 初始化，[toggleWatchlist] 驱动转移。 */
    private val _watchlistState = MutableStateFlow(ToggleUiState.OFF)
    val watchlistState: StateFlow<ToggleUiState> = _watchlistState.asStateFlow()

    init {
        // 路由必有 seriesId；无参构造（=0）不预载
        if (seriesId > 0L) load()
    }

    fun load() {
        viewModelScope.launch {
            paged.loadInitial(
                fetch = {
                    pixivRepository.api.getIllustSeries(seriesId).also { resp ->
                        _detail.value = resp.illust_series_detail
                        // 追更态以服务端返回为准（重试 / 重新加载时同步刷新）
                        _watchlistState.value =
                            if (resp.illust_series_detail?.watchlist_added == true) ToggleUiState.ON
                            else ToggleUiState.OFF
                    }
                },
                fetchNext = { pixivRepository.api.getNextIllustSeries(it) },
            )
        }
    }

    fun loadMore() {
        viewModelScope.launch { paged.loadMore() }
    }

    /**
     * [ToggleUiState] 状态机：进行中保留旧文案禁用防连点，失败回滚；断网自动入队待同步。
     */
    fun toggleWatchlist() {
        runToggle(
            _watchlistState,
            CoreR.string.core_msg_watching_added,
            CoreR.string.core_msg_watching_removed,
        ) { favoriteActions.toggleMangaWatchlist(seriesId, it) }
    }
}
