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
 */
class PagedState<T> {

    private val _items = MutableStateFlow<List<T>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _isLoadingMore = MutableStateFlow(false)
    private val _hasMore = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)

    val items: StateFlow<List<T>> = _items.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()

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
            _items.value = page.items
            next = page.nextPageUrl
            _hasMore.value = page.nextPageUrl != null
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
     */
    suspend fun loadMore() {
        val url = next ?: return
        if (_isLoading.value || _isLoadingMore.value) return
        val gen = generation
        val fetcher = nextFetch ?: return
        _isLoadingMore.value = true
        try {
            val page = fetcher(url)
            // reset 后（新搜索）旧代次结果必须丢弃：否则旧查询第 2 页会混入新结果
            if (gen != generation) return
            _items.value = _items.value + page.items
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
        // 在途 loadMore 属于旧代次：清标志让新查询的 loadMore 不被旧请求阻塞
        _isLoadingMore.value = false
    }

    /** 手动注入错误（如分页失败后由调用方补充文案）。 */
    fun setError(message: String) {
        _error.value = message
    }
}
