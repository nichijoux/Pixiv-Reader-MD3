package com.pixiv.reader.feature.novel.state

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.Novel
import com.pixiv.api.model.NovelSeriesDetail
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.core.network.session.SeriesCoverCache
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 小说系列详情页 ViewModel：系列信息 + 分册列表（`/v2/novel/series` 分页）。
 * 系列封面（第一册 medium）走 [SeriesCoverCache]，与用户主页系列列表共享缓存。
 */
@HiltViewModel
class NovelSeriesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
    private val seriesCoverCache: SeriesCoverCache,
) : ViewModel() {

    private val seriesId: Long = savedStateHandle.get<Long>("seriesId") ?: 0L

    private val _detail = MutableStateFlow<NovelSeriesDetail?>(null)
    val detail: StateFlow<NovelSeriesDetail?> = _detail.asStateFlow()

    /** 系列第一册封面 URL（系列无独立封面，用首册 `image_urls` 兜底；null 表示无可用图）。 */
    private val _firstNovelCover = MutableStateFlow<String?>(null)
    val firstNovelCover: StateFlow<String?> = _firstNovelCover.asStateFlow()

    val paged = PagedState<Novel>()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            paged.loadInitial(
                fetch = {
                    pixivRepository.api.getNovelSeries(seriesId).also { resp ->
                        _detail.value = resp.novel_series_detail
                        // 走进程级缓存：用户主页列表已取过封面则零请求
                        _firstNovelCover.value = seriesCoverCache.getOrFetch(seriesId) {
                            resp.novel_series_first_novel?.image_urls?.medium
                                ?: resp.novel_series_first_novel?.image_urls?.square_medium
                        }
                    }
                },
                fetchNext = { pixivRepository.api.getNextNovelSeriesDetail(it) },
            )
        }
    }

    fun loadMore() {
        viewModelScope.launch { paged.loadMore() }
    }

    /** 收藏 / 取消收藏小说（nowFavorite 为目标状态，由组件回调）。 */
    fun toggleNovelFavorite(novelId: Long, nowFavorite: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (nowFavorite) pixivRepository.api.bookmarkNovel(novelId, "public", emptyList())
                else pixivRepository.api.unbookmarkNovel(novelId)
            }
        }
    }
}
