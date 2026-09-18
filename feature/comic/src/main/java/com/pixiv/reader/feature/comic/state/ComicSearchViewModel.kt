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
    val resultsPaged = PagedState<ComicWork>()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** 当前生效关键词（新搜索进行中时防旧词触底续页）。 */
    private var activeKeyword: String? = null

    /** 下一次触底要取的页号（闭包计数，随 reset 重排）。 */
    private var nextPage = 1

    /**
     * 发起搜索（空词忽略；同词重复提交忽略）。
     *
     * @param keyword 关键词
     */
    fun search(keyword: String) {
        val kw = keyword.trim()
        if (kw.isEmpty() || kw == activeKeyword) return
        activeKeyword = kw
        _query.value = kw
        nextPage = 1
        viewModelScope.launch {
            resultsPaged.reset()
            resultsPaged.loadInitial(
                fetch = { searchPage(kw, 1) },
                fetchNext = {
                    // 游标 URL 仅作非空标记，页号由本地计数器推进
                    searchPage(kw, ++nextPage)
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
