package com.pixiv.reader.feature.bookmark

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.BookmarkTag
import com.pixiv.api.model.Illust
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 收藏夹类型：插画 / 小说。 */
enum class BookmarkType(@StringRes val labelRes: Int) {
    ILLUST(R.string.bookmark_type_illust),
    NOVEL(R.string.bookmark_type_novel),
}

/**
 * 我的收藏 ViewModel：插画/小说收藏列表 + 标签筛选 + 分页。
 * 数据源为当前登录用户（restrict=public）。
 * 路由参数支持 `?type=illust|novel&tag=xxx` 从收藏标签页跳入并预选中。
 */
@HiltViewModel
class BookmarkViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
    private val favoriteActions: FavoriteActions,
) : ViewModel() {

    private val uid: Long = pixivRepository.pixivApi.session.loggedInUid

    private val _type = MutableStateFlow(BookmarkType.ILLUST)
    val type: StateFlow<BookmarkType> = _type.asStateFlow()

    private val _tags = MutableStateFlow<List<BookmarkTag>>(emptyList())
    val tags: StateFlow<List<BookmarkTag>> = _tags.asStateFlow()

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    private val _message = Channel<UiMessage>(Channel.BUFFERED)
    val message = _message.receiveAsFlow()

    val illustPaged = PagedState<Illust>()
    val novelPaged = PagedState<Novel>()

    init {
        val initType = when (savedStateHandle.get<String>("type")) {
            "novel" -> BookmarkType.NOVEL
            else -> BookmarkType.ILLUST
        }
        _type.value = initType
        _selectedTag.value = savedStateHandle.get<String>("tag")
        loadTags()
        loadList(initType)
    }

    fun selectType(type: BookmarkType) {
        if (_type.value == type) return
        _type.value = type
        _selectedTag.value = null
        loadTags()
        loadList(type)
    }

    fun selectTag(tag: String?) {
        if (_selectedTag.value == tag) return
        _selectedTag.value = tag
        loadList(_type.value)
    }

    private fun loadTags() {
        viewModelScope.launch {
            runCatching {
                when (_type.value) {
                    BookmarkType.ILLUST -> pixivRepository.api.getIllustBookmarkTags(uid, "public").tags
                    BookmarkType.NOVEL -> pixivRepository.api.getNovelBookmarkTags(uid, "public").tags
                }
            }.onSuccess { _tags.value = it }
        }
    }

    private fun loadList(type: BookmarkType) {
        viewModelScope.launch {
            when (type) {
                BookmarkType.ILLUST -> illustPaged.loadInitial(
                    fetch = { pixivRepository.api.getUserBookmarkedIllusts(uid, "public", _selectedTag.value) },
                    fetchNext = { pixivRepository.api.getNextIllusts(it) },
                )
                BookmarkType.NOVEL -> novelPaged.loadInitial(
                    fetch = { pixivRepository.api.getUserBookmarkedNovels(uid, "public", _selectedTag.value) },
                    fetchNext = { pixivRepository.api.getNextNovels(it) },
                )
            }
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            when (_type.value) {
                BookmarkType.ILLUST -> illustPaged.loadMore()
                BookmarkType.NOVEL -> novelPaged.loadMore()
            }
        }
    }

    /** 收藏 / 取消收藏小说（nowFavorite 为目标状态，由组件回调；取消后刷新当前列表）。 */
    fun toggleNovelFavorite(novelId: Long, nowFavorite: Boolean) {
        viewModelScope.launch {
            favoriteActions.toggleNovelFavorite(novelId, nowFavorite)
                .onSuccess {
                    if (!nowFavorite) viewModelScope.launch { loadList(_type.value) }
                }
        }
    }
}
