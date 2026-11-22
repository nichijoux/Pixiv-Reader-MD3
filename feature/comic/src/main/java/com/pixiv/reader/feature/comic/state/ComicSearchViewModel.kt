package com.pixiv.reader.feature.comic.state

import androidx.lifecycle.viewModelScope
import com.pixiv.api.Pageable
import com.pixiv.api.model.ComicWork
import com.pixiv.reader.core.common.loadFailureMessage
import com.pixiv.reader.core.network.comic.ComicRepository
import com.pixiv.reader.core.network.message.MessageViewModel
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.feature.comic.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * COMIC 搜索 ViewModel：search/v2 是**页号翻页**（无 next_url 游标），适配
 * [PagedState] 的做法是给每页结果合成一个非空「游标」占位串（仅判空、不真实
 * 寻址），后续页由闭包内的页号计数器驱动；返回不足一整页即视为到底。
 */
@HiltViewModel
class ComicSearchViewModel @Inject constructor(
    private val comicRepository: ComicRepository,
) : MessageViewModel() {

    /** 搜索结果分页（关键词变更时 reset 重建）。 */
    val resultsPaged = PagedState<ComicWork>(keySelector = { it.id })

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** 当前生效关键词（新搜索进行中时防旧词触底续页）。 */
    private var activeKeyword: String? = null

    /** 下一次触底要取的页号（成功取得该页后才推进，避免重试跳过失败页）。 */
    private var nextPage = 1

    private var searchJob: Job? = null

    /**
     * 发起搜索（空词忽略；同词成功且无错误时忽略，失败或新词则取消在途搜索并重查）。
     *
     * @param keyword 关键词
     * @return 无返回值
     */
    fun search(keyword: String) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        if (kw == activeKeyword && resultsPaged.error.value == null && searchJob?.isActive != true) {
            return
        }
        searchJob?.cancel()
        activeKeyword = kw
        _query.value = kw
        nextPage = 1
        searchJob = viewModelScope.launch {
            resultsPaged.reset()
            resultsPaged.loadInitial(
                fetch = { searchPage(kw, 1) },
                fetchNext = {
                    val targetPage = nextPage + 1
                    searchPage(kw, targetPage).also {
                        // 仅在请求成功后推进页号，防止失败重试跳过该页。
                        nextPage = targetPage
                    }
                },
            )
        }
    }

    /**
     * 重试当前关键词的首屏搜索。
     *
     * @return 无返回值
     */
    fun retry() {
        val kw = activeKeyword ?: _query.value.trim()
        if (kw.isEmpty()) return
        searchJob?.cancel()
        nextPage = 1
        searchJob = viewModelScope.launch {
            resultsPaged.reset()
            resultsPaged.loadInitial(
                fetch = { searchPage(kw, 1) },
                fetchNext = {
                    val targetPage = nextPage + 1
                    searchPage(kw, targetPage).also {
                        nextPage = targetPage
                    }
                },
            )
        }
    }

    /** 触底加载下一页。 */
    fun loadMore() {
        viewModelScope.launch { resultsPaged.loadMore() }
    }

    /**
     * 取一页搜索结果并合成 Pageable（满整页才给游标，不足一页即到底）。
     *
     * @param keyword 关键词
     * @param page 页号（从 1 起）
     * @return 页数据（items + 占位游标）
     */
    private suspend fun searchPage(keyword: String, page: Int): Pageable<ComicWork> {
        val works = try {
            comicRepository.api.searchWorks(keyword, page).data?.officialWorks.orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            trySendMessage(loadFailureMessage(e, R.string.comic_load_failed_reason, R.string.comic_load_failed))
            throw e
        }
        val cursor = if (works.size >= SEARCH_PAGE_SIZE) "page:${page + 1}" else null
        return object : Pageable<ComicWork> {
            override val items: List<ComicWork> = works
            override val nextPageUrl: String? = cursor
        }
    }

    companion object {
        /** 服务端每页固定条数（实测 30）。 */
        private const val SEARCH_PAGE_SIZE = 30
    }
}
