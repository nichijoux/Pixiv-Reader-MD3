package com.pixiv.reader.feature.discover.state

import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.Article
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * pixivision 特辑 ViewModel：官方特辑文章列表（`v1/spotlight/articles`，category=all）。
 * 文章详情为外部网页（article_url），由调用方跳系统浏览器打开。
 */
@HiltViewModel
class PixivisionViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
) : androidx.lifecycle.ViewModel() {

    /** 特辑文章分页（游标 next_url）。 */
    val paged = PagedState<Article>()

    init {
        load()
    }

    /** 首次加载 / 失败重试。 */
    fun load() {
        viewModelScope.launch {
            paged.loadInitial(
                fetch = { pixivRepository.api.getArticles("all") },
                fetchNext = { pixivRepository.api.getNextArticles(it) },
            )
        }
    }

    /** 触底加载更多。 */
    fun loadMore() {
        viewModelScope.launch { paged.loadMore() }
    }
}
