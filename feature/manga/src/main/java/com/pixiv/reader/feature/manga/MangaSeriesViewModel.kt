package com.pixiv.reader.feature.manga

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.Illust
import com.pixiv.api.model.NovelSeriesDetail
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.common.R as CoreR
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
 * 漫画系列详情 ViewModel（`v1/illust/series`）：
 * 系列详情（复用 NovelSeriesDetail 类型，含 `watchlist_added` 追更态）+ 系列内作品分页。
 * 追更 toggle 走 v1/watchlist/manga add/remove（与追更页漫画分段同一端点）。
 */
@HiltViewModel
class MangaSeriesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
) : MessageViewModel() {

    /** 系列路由参数 id（固定）。 */
    private val seriesId: Long = savedStateHandle.get<Long>("seriesId") ?: 0L

    /** 系列详情（标题/简介/作者/追更态/总话数）。 */
    private val _detail = MutableStateFlow<NovelSeriesDetail?>(null)
    val detail: StateFlow<NovelSeriesDetail?> = _detail.asStateFlow()

    /** 系列内作品分页（PagedState 累积，触底加载）。 */
    val paged = PagedState<Illust>()

    /** 是否已追更（detail.watchlist_added / 乐观翻转）。 */
    private val _isWatchlisted = MutableStateFlow(false)
    val isWatchlisted: StateFlow<Boolean> = _isWatchlisted.asStateFlow()

    /** 追更请求进行中（防连点）。 */
    private val _isWatchlisting = MutableStateFlow(false)
    val isWatchlisting: StateFlow<Boolean> = _isWatchlisting.asStateFlow()

    init {
        // 路由必有 seriesId；无参构造（=0）不预载
        if (seriesId > 0L) load()
    }

    /** 首次加载 / 失败重试：详情 + 系列内作品第一页。 */
    fun load() {
        viewModelScope.launch {
            paged.loadInitial(
                fetch = {
                    pixivRepository.api.getIllustSeries(seriesId).also { resp ->
                        _detail.value = resp.illust_series_detail
                        _isWatchlisted.value = resp.illust_series_detail?.watchlist_added == true
                    }
                },
                fetchNext = { pixivRepository.api.getNextIllustSeries(it) },
            )
        }
    }

    /** 触底加载更多系列内作品。 */
    fun loadMore() {
        viewModelScope.launch { paged.loadMore() }
    }

    /** 追更 / 取消追更（乐观翻转 + 防连点；成功经消息通道提示）。 */
    fun toggleWatchlist() {
        if (_isWatchlisting.value) return
        viewModelScope.launch {
            _isWatchlisting.value = true
            val current = _isWatchlisted.value
            runCatching {
                if (current) pixivRepository.api.removeWatchlistManga(seriesId)
                else pixivRepository.api.addWatchlistManga(seriesId)
            }.onSuccess {
                _isWatchlisted.value = !current
                sendMessage(
                    if (!current) UiMessage(CoreR.string.core_msg_watching_added)
                    else UiMessage(CoreR.string.core_msg_watching_removed)
                )
            }.onFailure {
                sendMessage(UiMessage(CoreR.string.core_msg_action_failed, listOf(it.message ?: "")))
            }
            _isWatchlisting.value = false
        }
    }
}
