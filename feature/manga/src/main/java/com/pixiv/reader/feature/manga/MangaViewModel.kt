package com.pixiv.reader.feature.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixivapi.model.Illust
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * 漫画 Tab ViewModel：推荐漫画瀑布流（`GET /v1/manga/recommended` 游标分页）。
 * 收藏/取消收藏由瀑布流卡片回调。
 */
@HiltViewModel
class MangaViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
) : ViewModel() {

    val recommendPaged = PagedState<Illust>()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            recommendPaged.loadInitial(
                fetch = { pixivRepository.api.getRecommendedManga() },
                fetchNext = { pixivRepository.api.getNextIllusts(it) },
            )
        }
    }

    fun loadMore() {
        viewModelScope.launch { recommendPaged.loadMore() }
    }

    fun refresh() = load()

    /** 收藏 / 取消收藏插画（nowFavorite 为目标状态，由组件回调）。 */
    fun toggleIllustFavorite(illustId: Long, nowFavorite: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (nowFavorite) pixivRepository.api.bookmarkIllust(illustId, "public", emptyList())
                else pixivRepository.api.unbookmarkIllust(illustId)
            }
        }
    }
}