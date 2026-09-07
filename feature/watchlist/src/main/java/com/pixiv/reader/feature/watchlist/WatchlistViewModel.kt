package com.pixiv.reader.feature.watchlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.WatchlistSeries
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 追更 ViewModel：小说 / 漫画系列追更列表（分页）。
 *
 * 「类型 × 分页」缓存策略（与排行榜同款）：每类型独立 [PagedState]（`pages.getOrPut`），
 * 首次选中该类型才发请求；滑动切回已加载类型不重复请求、无过渡动画。
 * 行内取消追更：novel/manga 各自端点删除后重载当前类型列表（简单可靠）。
 */
@HiltViewModel
class WatchlistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
) : ViewModel() {

    companion object {
        /** 类型常量（路由参数 watchlist?type=）。 */
        const val TYPE_NOVEL = "novel"
        const val TYPE_MANGA = "manga"
    }

    /** 初始类型（路由参数；缺省小说）。 */
    private val initialType: String =
        savedStateHandle.get<String>("type")?.takeIf { it == TYPE_MANGA } ?: TYPE_NOVEL

    /** 各类型独立分页状态（切分段不重复请求）。 */
    private val pages = mutableMapOf<String, PagedState<WatchlistSeries>>()

    /** 已发起过首载的类型集合（防重复请求）。 */
    private val loadedTypes = mutableSetOf<String>()

    /** 当前选中类型。 */
    private val _type = MutableStateFlow(initialType)
    val type: StateFlow<String> = _type.asStateFlow()

    init {
        ensureLoaded(initialType)
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
     * 行内取消追更（按当前类型分流端点；成功后重载当前类型列表）。
     *
     * @param series 待取消追更的系列
     */
    fun removeWatchlist(series: WatchlistSeries) {
        val type = _type.value
        viewModelScope.launch {
            runCatching {
                if (type == TYPE_MANGA) pixivRepository.api.removeWatchlistManga(series.id)
                else pixivRepository.api.removeWatchlistNovel(series.id)
            }.onSuccess {
                // 重载当前类型（简单可靠：服务端已删除，重拉即为最新列表）
                retry(type)
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
}
