package com.pixiv.reader.core.network.paging

import com.pixiv.api.Pageable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 通用游标分页状态（基于 pixiv next_url）。
 * keySelector：可选去重键，相同非空键保留首次出现项，空键项全部保留。
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

    private var next: String? = null
    private var initialFetch: (suspend () -> Pageable<T>)? = null
    private var nextFetch: (suspend (String) -> Pageable<T>)? = null

    /**
     * 加载代次：[reset] 自增使旧代次作废——旧请求完成时丢弃结果且不复位 isLoading，
     * 防止下拉刷新（reset + 重新 loadInitial）期间旧请求返回覆盖新数据。
     */
    private var generation = 0

    /** 加载中重复调用直接忽略；失败置 error 并停用加载更多。 */
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

    /** 用上次返回的 next_url 追加；失败仅报错，不清空已有数据。 */
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
     * 合并本页条目，保留已有列表与页内首次出现顺序。
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
     * 复位游标与列表并作废旧代次：使 reset + loadInitial 在任意状态（含首载未完成）都真正重拉，
     * 而不是被幂等忽略。
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
     * 快照预填（冷启动秒开）：已有内容时忽略以免覆盖活动中的分页；loadInitial 成功后清除 stale。
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
