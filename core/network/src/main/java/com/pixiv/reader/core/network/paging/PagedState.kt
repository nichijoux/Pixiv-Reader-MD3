package com.pixiv.reader.core.network.paging

import com.example.pixivapi.Pageable
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

    private var next: String? = null
    private var initialFetch: (suspend () -> Pageable<T>)? = null
    private var nextFetch: (suspend (String) -> Pageable<T>)? = null

    suspend fun loadInitial(
        fetch: suspend () -> Pageable<T>,
        fetchNext: suspend (String) -> Pageable<T>,
    ) {
        if (_isLoading.value) return
        initialFetch = fetch
        nextFetch = fetchNext
        _isLoading.value = true
        _error.value = null
        try {
            val page = fetch()
            _items.value = page.items
            next = page.nextPageUrl
            _hasMore.value = page.nextPageUrl != null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _error.value = e.message ?: "加载失败"
            _hasMore.value = false
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun loadMore() {
        val url = next ?: return
        if (_isLoading.value || _isLoadingMore.value) return
        val fetcher = nextFetch ?: return
        _isLoadingMore.value = true
        try {
            val page = fetcher(url)
            _items.value = _items.value + page.items
            next = page.nextPageUrl
            _hasMore.value = page.nextPageUrl != null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _error.value = e.message ?: "加载失败"
        } finally {
            _isLoadingMore.value = false
        }
    }

    /** 清空并重新加载 */
    fun reset() {
        next = null
        _items.value = emptyList()
        _error.value = null
        _hasMore.value = true
    }

    fun setError(message: String) {
        _error.value = message
    }
}
