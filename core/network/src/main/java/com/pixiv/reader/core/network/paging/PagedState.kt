package com.pixiv.reader.core.network.paging

import com.pixiv.api.Pageable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 通用游标分页状态（基于 pixiv next_url）。
 *
 * 用法（ViewModel 持有）：
 * ```
 * class XViewModel : ViewModel() {
 *     val paged = PagedState<Illust>()
 *     fun load() = viewModelScope.launch {
 *         paged.loadInitial(
 *             fetch = { api.getRecommendedIllusts(true) },
 *             fetchNext = { url -> api.getNextIllusts(url) },
 *         )
 *     }
 *     fun loadMore() = viewModelScope.launch { paged.loadMore() }
 * }
 * ```
 *
 * @param keySelector 可选去重键。相同的非空键保留首次出现项，空键项全部保留。
 */
class PagedState<T>(private val keySelector: ((T) -> Any?)? = null) {

    private val _items = MutableStateFlow<List<T>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _isLoadingMore = MutableStateFlow(false)
    private val _hasMore = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)
    private val _isStale = MutableStateFlow(false)

    val items: StateFlow<List<T>> = _items.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 当前内容是否来自快照预填（秒开场景；后台刷新成功后自动清除）。 */
    val isStale: StateFlow<Boolean> = _isStale.asStateFlow()

    /** 记录分页 fetch 函数（供 loadMore 复用 next_url 游标）。 */
    private var next: String? = null
    private var initialFetch: (suspend () -> Pageable<T>)? = null
    private var nextFetch: (suspend (String) -> Pageable<T>)? = null

    /**
     * 加载代次：[reset] 自增使旧代次作废——旧请求完成时丢弃结果且不复位 isLoading，
     * 防止下拉刷新（reset + 重新 loadInitial）期间旧请求返回覆盖新数据。
     */
    private var generation = 0

    /**
     * 首次加载：拉取第一页并缓存 fetch 函数。
     * 加载中重复调用直接忽略；失败时置 error 并停用加载更多（hasMore=false）。
     *
     * @param fetch 首页请求（返回第一页数据 + next_url 游标）
     * @param fetchNext 后续页请求（入参为上一页返回的游标 URL）
     * @return 无返回值（结果经 [items]/[isLoading]/[error] 等状态流暴露）
     */
    suspend fun loadInitial(
        fetch: suspend () -> Pageable<T>,
        fetchNext: suspend (String) -> Pageable<T>,
    ) {
        if (_isLoading.value) return
        val gen = generation
        initialFetch = fetch
        nextFetch = fetchNext
        _isLoading.value = true
        _error.value = null
        try {
            val page = fetch()
            if (gen != generation) return
            // 首页始终替换快照或旧分页，去重仅针对本次返回的数据。
            _items.value = mergePage(emptyList(), page.items)
            next = page.nextPageUrl
            _hasMore.value = page.nextPageUrl != null
            // 首页加载成功 = 快照内容已被服务端数据替换
            _isStale.value = false
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (gen != generation) return
            _error.value = e.message
            _hasMore.value = false
        } finally {
            // 过期代次不复位 isLoading（新代次的加载由新调用自己管理）
            if (gen == generation) _isLoading.value = false
        }
    }

    /**
     * 加载下一页：使用上次返回的 next_url 游标追加数据。
     * 无游标 / 加载中直接忽略；失败仅报错不清空已有数据。
     *
     * @return 无返回值（结果经 [items]/[isLoadingMore]/[error] 等状态流暴露）
     */
    suspend fun loadMore() {
        val url = next ?: return
        if (_isLoading.value || _isLoadingMore.value) return
        val gen = generation
        val fetcher = nextFetch ?: return
        _isLoadingMore.value = true
        // 启动续页前清除旧分页错误，避免失败状态残留阻塞按钮交互或触发异常状态。
        _error.value = null
        try {
            val page = fetcher(url)
            // reset 后（新搜索）旧代次结果必须丢弃：否则旧查询第 2 页会混入新结果
            if (gen != generation) return
            _items.value = mergePage(_items.value, page.items)
            next = page.nextPageUrl
            _hasMore.value = page.nextPageUrl != null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (gen != generation) return
            _error.value = e.message
        } finally {
            // 过期代次不复位 isLoadingMore（新代次的标志由 reset 清空/自身管理）
            if (gen == generation) _isLoadingMore.value = false
        }
    }

    /**
     * 合并本页条目并保留已有列表和页内首次出现顺序。
     *
     * @param existing 当前已展示的条目
     * @param incoming 本页待合并的条目
     * @return 合并后的条目列表
     */
    private fun mergePage(existing: List<T>, incoming: List<T>): List<T> {
        val selectKey = keySelector ?: return if (existing.isEmpty()) incoming else existing + incoming
        // 先登记已有非空键，避免续页覆盖或重排已展示条目。
        val seen = existing.mapNotNullTo(HashSet<Any>(), selectKey)
        return existing + incoming.filter { item ->
            selectKey(item)?.let(seen::add) ?: true
        }
    }

    /**
     * 清空并重新加载（下次 loadInitial 生效；游标与列表归零）。
     * 同时复位 isLoading 并作废旧代次——保证「下拉刷新」（reset + loadInitial）
     * 在任意状态下（含首载未完成）都真正重新拉取，而不是被幂等忽略。
     */
    fun reset() {
        generation++
        next = null
        _items.value = emptyList()
        _error.value = null
        _hasMore.value = true
        _isLoading.value = false
        _isStale.value = false
        // 在途 loadMore 属于旧代次：清标志让新查询的 loadMore 不被旧请求阻塞
        _isLoadingMore.value = false
    }

    /**
     * 快照预填（首页秒开）：冷启动时用上次保存的第一页内容立即填充列表，
     * 后续 loadInitial 成功后无缝替换并清除 stale 标记；失败时快照内容保留可继续浏览
     * （loadInitial 失败不清空 items）。已有内容时忽略（避免覆盖活动中的分页）。
     *
     * @param items 快照内容（上次成功加载的第一页）
     * @param nextUrl 快照保存的分页游标（触底加载续传位置）
     * @return 无返回值
     */
    fun restoreSnapshot(items: List<T>, nextUrl: String?) {
        if (_items.value.isNotEmpty()) return
        // 老快照可能保存了重复键，首次展示前沿用首页去重规则。
        _items.value = mergePage(emptyList(), items)
        next = nextUrl
        _hasMore.value = nextUrl != null
        _isStale.value = items.isNotEmpty()
    }
}
