package com.pixiv.reader.feature.watchlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.WatchlistSeries
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.common.R as CoreR
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.message.MessageViewModel
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.core.network.session.SeriesDetailInfo
import com.pixiv.reader.core.network.session.SeriesDetailLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 「类型 × 分页」缓存与排行榜同款：每类型独立 [PagedState]，首次选中才请求，切回不重复请求。
 * 列表项不带封面，漫画封面 / 小说详情逐个经 [SeriesDetailLoader] 补齐，与系列详情页共享缓存。
 */
@HiltViewModel
class WatchlistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
    private val seriesDetailLoader: SeriesDetailLoader,
    private val favoriteActions: FavoriteActions,
) : MessageViewModel() {

    companion object {
        /** 路由参数 `watchlist?type=` 的取值。 */
        const val TYPE_NOVEL = "novel"
        const val TYPE_MANGA = "manga"
    }

    /** 缺省小说。 */
    private val initialSegment: WatchlistSegment =
        WatchlistSegment.parse(savedStateHandle.get<String>("type"))

    private val pages = mutableMapOf<String, PagedState<WatchlistSeries>>()

    private val loadedTypes = mutableSetOf<String>()

    /** 沿用路由字符串值，与 pages/loadedTypes 键一致。 */
    private val _type = MutableStateFlow(initialSegment.routeValue)
    val type: StateFlow<String> = _type.asStateFlow()

    /** 异步补齐的封面驱动瀑布流刷新。 */
    private val _mangaCovers = MutableStateFlow<Map<Long, String>>(emptyMap())
    val mangaCovers: StateFlow<Map<Long, String>> = _mangaCovers.asStateFlow()

    /** 与小说 Tab 追更页签 / 用户页系列列表共享同一缓存。 */
    private val _novelInfos = MutableStateFlow<Map<Long, SeriesDetailInfo>>(emptyMap())
    val novelInfos: StateFlow<Map<Long, SeriesDetailInfo>> = _novelInfos.asStateFlow()

    init {
        ensureLoaded(initialSegment.routeValue)
    }

    fun stateFor(type: String): PagedState<WatchlistSeries> =
        pages.getOrPut(type) { PagedState(keySelector = { it.id }) }

    fun selectType(type: String) {
        if (type == _type.value) return
        _type.value = type
        ensureLoaded(type)
    }

    fun retry(type: String) {
        loadedTypes -= type
        ensureLoaded(type)
    }

    fun loadMore(type: String) {
        viewModelScope.launch { stateFor(type).loadMore() }
    }

    /**
     * 行内取消追更（经 [FavoriteActions] 统一收口，断网自动入队），成功后重载当前类型列表。
     */
    fun removeWatchlist(series: WatchlistSeries) {
        val type = _type.value
        viewModelScope.launch {
            val toggle = if (type == TYPE_MANGA) favoriteActions::toggleMangaWatchlist
            else favoriteActions::toggleNovelWatchlist
            toggle(series.id, false)
                .onSuccess {
                    retry(type)
                }
                .onFailure {
                    sendMessage(UiMessage(CoreR.string.core_msg_action_failed, listOf(it.message ?: "")))
                }
        }
    }

    private fun ensureLoaded(type: String) {
        if (type in loadedTypes) return
        loadedTypes += type
        viewModelScope.launch {
            stateFor(type).loadInitial(
                fetch = {
                    if (type == TYPE_MANGA) pixivRepository.api.getWatchlistManga()
                    else pixivRepository.api.getWatchlistNovel()
                },
                fetchNext = { pixivRepository.api.getNextWatchlist(it) },
            )
        }
    }

    /**
     * 已缓存条目也必须同步回填 [_mangaCovers]：VM 重建后本地流为空，缓存命中 ≠ 无需回填，
     * 否则二次进入封面全空白。并发度与详情页一致（Semaphore 4，与漫画系列详情页共享缓存）。
     */
    fun loadMangaCovers(series: List<WatchlistSeries>) {
        viewModelScope.launch {
            seriesDetailLoader.backfillInto(
                target = _mangaCovers,
                ids = series.map { it.id },
                select = { info -> info.coverUrl?.takeIf { it.isNotBlank() } },
            ) { id ->
                pixivRepository.api.getIllustSeries(id).let { resp ->
                    SeriesDetailInfo(
                        coverUrl = resp.illust_series_first_illust?.image_urls?.medium
                            ?: resp.illust_series_first_illust?.image_urls?.square_medium,
                        caption = resp.illust_series_detail?.caption,
                        isConcluded = resp.illust_series_detail?.is_concluded,
                        updatedAt = resp.illust_series_latest_illust?.create_date,
                    )
                }
            }
        }
    }

    /**
     * 同上：已缓存条目先同步回填 [_novelInfos]（VM 重建后本地流为空），仅真正缺失的走网络。
     * 隐藏（masked）系列 getNovelSeries 可能抛异常——失败项留兜底展示，不中断整批。
     */
    fun loadNovelInfos(series: List<WatchlistSeries>) {
        viewModelScope.launch {
            seriesDetailLoader.backfillInfos(
                target = _novelInfos,
                ids = series.map { it.id },
            ) { id -> fetchNovelSeriesDetail(id) }
        }
    }

    private suspend fun fetchNovelSeriesDetail(seriesId: Long): SeriesDetailInfo {
        val resp = pixivRepository.api.getNovelSeries(seriesId)
        return SeriesDetailInfo(
            coverUrl = resp.novel_series_first_novel?.image_urls?.medium
                ?: resp.novel_series_first_novel?.image_urls?.square_medium,
            caption = resp.novel_series_detail?.caption,
            isConcluded = resp.novel_series_detail?.is_concluded,
            totalChars = resp.novel_series_detail?.total_character_count ?: 0,
            updatedAt = resp.novel_series_latest_novel?.create_date,
        )
    }
}

enum class WatchlistSegment(val routeValue: String) {
    NOVEL("novel"),
    MANGA("manga");

    companion object {
        fun parse(raw: String?): WatchlistSegment =
            if (raw == MANGA.routeValue) MANGA else NOVEL
    }
}
