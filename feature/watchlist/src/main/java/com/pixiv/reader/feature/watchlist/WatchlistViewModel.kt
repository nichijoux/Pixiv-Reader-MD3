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
 * 追更 ViewModel：小说 / 漫画系列追更列表（分页）。
 *
 * 「类型 × 分页」缓存策略（与排行榜同款）：每类型独立 [PagedState]（`pages.getOrPut`），
 * 首次选中该类型才发请求；滑动切回已加载类型不重复请求、无过渡动画。
 * 行内取消追更：novel/manga 各自端点删除后重载当前类型列表（简单可靠）。
 *
 * 漫画瀑布流封面：watchlist 列表项不带封面，逐个经 [SeriesDetailLoader]（进程缓存 + in-flight 去重）
 * 拉 `v1/illust/series` 详情补齐，与漫画系列详情页共享缓存。
 */
@HiltViewModel
class WatchlistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
    private val seriesDetailLoader: SeriesDetailLoader,
    private val favoriteActions: FavoriteActions,
) : MessageViewModel() {

    companion object {
        /** 类型常量（路由参数 watchlist?type=）。 */
        const val TYPE_NOVEL = "novel"
        const val TYPE_MANGA = "manga"
    }

    /** 初始类型（路由参数解析；缺省小说）。 */
    private val initialSegment: WatchlistSegment =
        WatchlistSegment.parse(savedStateHandle.get<String>("type"))

    /** 各类型独立分页状态（切分段不重复请求）。 */
    private val pages = mutableMapOf<String, PagedState<WatchlistSeries>>()

    /** 已发起过首载的类型集合（防重复请求）。 */
    private val loadedTypes = mutableSetOf<String>()

    /** 当前选中类型（内部沿用路由字符串值，与 pages/loadedTypes 键一致）。 */
    private val _type = MutableStateFlow(initialSegment.routeValue)
    val type: StateFlow<String> = _type.asStateFlow()

    /** 漫画系列封面（seriesId → 封面 URL；列表项不带封面，异步补齐驱动瀑布流刷新）。 */
    private val _mangaCovers = MutableStateFlow<Map<Long, String>>(emptyMap())
    val mangaCovers: StateFlow<Map<Long, String>> = _mangaCovers.asStateFlow()

    /** 小说系列详情（seriesId → 封面/简介/完结/字数/更新时间；SeriesDetailCache 与小说 Tab 追更页签/用户页共享）。 */
    private val _novelInfos = MutableStateFlow<Map<Long, SeriesDetailInfo>>(emptyMap())
    val novelInfos: StateFlow<Map<Long, SeriesDetailInfo>> = _novelInfos.asStateFlow()

    init {
        ensureLoaded(initialSegment.routeValue)
    }

    /**
     * 取某类型的分页状态（无则创建；UI 按当前类型 collect）。
     *
     * @param type 类型常量（[TYPE_NOVEL] / [TYPE_MANGA]）
     * @return 该类型的独立分页状态
     */
    fun stateFor(type: String): PagedState<WatchlistSeries> = pages.getOrPut(type) { PagedState() }

    /**
     * 切换类型分段（首次选中触发首载，已加载类型直接复用缓存）。
     *
     * @param type 目标类型常量
     */
    fun selectType(type: String) {
        if (type == _type.value) return
        _type.value = type
        ensureLoaded(type)
    }

    /** 首次加载 / 失败重试（重新拉第一页并清空旧数据）。 */
    fun retry(type: String) {
        loadedTypes -= type
        ensureLoaded(type)
    }

    /** 触底加载更多（当前类型）。 */
    fun loadMore(type: String) {
        viewModelScope.launch { stateFor(type).loadMore() }
    }

    /**
     * 行内取消追更（经 FavoriteActions 统一收口，断网自动入队；成功后重载当前类型列表，
     * 失败经消息通道提示）。
     *
     * @param series 待取消追更的系列
     */
    fun removeWatchlist(series: WatchlistSeries) {
        val type = _type.value
        viewModelScope.launch {
            val toggle = if (type == TYPE_MANGA) favoriteActions::toggleMangaWatchlist
            else favoriteActions::toggleNovelWatchlist
            toggle(series.id, false)
                .onSuccess {
                    // 重载当前类型（简单可靠：服务端已删除，重拉即为最新列表）
                    retry(type)
                }
                .onFailure {
                    sendMessage(UiMessage(CoreR.string.core_msg_action_failed, listOf(it.message ?: "")))
                }
        }
    }

    /** 类型未首载时发起首载（幂等：已载类型跳过）。 */
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
     * 为漫画追更列表批量补齐封面（[SeriesDetailLoader] 批量管线：缓存回填 → 缺失逐个取，
     * `Semaphore(4)` 限并发；与漫画系列详情页共享缓存）。
     * 已缓存条目也要**同步回填** [_mangaCovers]——VM 随页面销毁重建后其本地流为空，
     * 缓存命中 ≠ 无需回填，否则二次进入封面全空白。
     *
     * @param series 当前漫画追更列表（封面缺失的逐个补）
     * @return 无返回值；封面就绪经 [mangaCovers] 流驱动 UI 刷新
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
     * 为小说追更列表批量取系列详情（[SeriesDetailLoader] 批量管线；
     * 与小说 Tab 追更页签 / 用户页系列列表同一缓存）。
     * 已缓存条目先同步回填 [_novelInfos]——VM 随页面销毁重建后其本地流为空，缓存命中
     * 路径也必须把数据送进 UI 流（否则返回后二次进入封面/简介全空白），仅真正缺失的走网络。
     * 隐藏（masked）系列的 `getNovelSeries` 可能抛异常——失败项留兜底展示不中断整批。
     *
     * @param series 当前小说追更列表（缺详情的逐个补）
     * @return 无返回值；详情就绪经 [novelInfos] 流驱动 UI 刷新
     */
    fun loadNovelInfos(series: List<WatchlistSeries>) {
        viewModelScope.launch {
            seriesDetailLoader.backfillInfos(
                target = _novelInfos,
                ids = series.map { it.id },
            ) { id -> fetchNovelSeriesDetail(id) }
        }
    }

    /** 取单个小说系列详情（首分册封面 + 简介 + 完结态 + 总字数 + 最近更新时间；失败抛给调用方捕获）。 */
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

/**
 * 追更列表分段（路由参数 watchlist?type= 的类型化表达）。
 *
 * @property routeValue 路由 query 值（也是 VM 内部 pages/loadedTypes 的键）
 */
enum class WatchlistSegment(val routeValue: String) {
    NOVEL("novel"),
    MANGA("manga");

    companion object {
        /**
         * 从路由/存储的原始字符串解析。
         *
         * @param raw 原始值（null 或未知值回退 [NOVEL]）
         * @return 解析结果（永不失败）
         */
        fun parse(raw: String?): WatchlistSegment =
            if (raw == MANGA.routeValue) MANGA else NOVEL
    }
}
