package com.pixiv.reader.feature.bookmark

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.BookmarkTag
import com.pixiv.api.model.Illust
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.common.MessageType
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.message.MessageViewModel
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.pixiv.reader.core.common.R as CoreR

enum class BookmarkType(@param:StringRes val labelRes: Int) {
    ILLUST(R.string.bookmark_type_illust),
    NOVEL(R.string.bookmark_type_novel),
}

/**
 * 收藏列表：插画/小说 + 标签筛选 + 分页，数据源为当前登录用户（restrict=public）。
 * 路由参数 `?type=illust|novel&tag=xxx` 从收藏标签页跳入时可预选中。
 */
@HiltViewModel
class BookmarkViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
    private val favoriteActions: FavoriteActions,
) : MessageViewModel() {

    private val uid: Long = pixivRepository.pixivApi.session.loggedInUid

    private val _type = MutableStateFlow(BookmarkType.ILLUST)
    val type: StateFlow<BookmarkType> = _type.asStateFlow()

    private val _tags = MutableStateFlow<List<BookmarkTag>>(emptyList())
    val tags: StateFlow<List<BookmarkTag>> = _tags.asStateFlow()

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    // 跨页重复作品 ID 会让瀑布流撞 key 崩溃，按 id 去重
    val illustPaged = PagedState<Illust>(keySelector = { it.id })
    val novelPaged = PagedState<Novel>(keySelector = { it.id })

    private var tagsJob: Job? = null
    private val pendingFavorites = mutableSetOf<Pair<BookmarkType, Long>>()

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
        // 旧类型的标签不能用于新列表的查询。
        _type.value = type
        _selectedTag.value = null
        _tags.value = emptyList()
        loadTags()
        loadList(type)
    }

    fun selectTag(tag: String?) {
        if (_selectedTag.value == tag) return
        _selectedTag.value = tag
        loadList(_type.value)
    }

    private fun loadTags() {
        // 保存请求所属的类型，旧请求完成时不得覆盖新标签。
        tagsJob?.cancel()
        val type = _type.value
        tagsJob = viewModelScope.launch {
            try {
                val tags = when (type) {
                    BookmarkType.ILLUST -> pixivRepository.api.getIllustBookmarkTags(uid, "public").tags
                    BookmarkType.NOVEL -> pixivRepository.api.getNovelBookmarkTags(uid, "public").tags
                }
                ensureActive()
                _tags.value = tags
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 标签请求失败不阻断收藏列表的独立加载。
            }
        }
    }

    /**
     * preserveItems：收藏动作完成后保留现有卡片，直到新数据返回（切标签/切类型时强制 reset）。
     */
    private fun loadList(type: BookmarkType, preserveItems: Boolean = false) {
        val tag = _selectedTag.value
        viewModelScope.launch {
            // 切标签或与在途请求冲突时作废旧游标与旧结果。
            val paged = if (type == BookmarkType.ILLUST) illustPaged else novelPaged
            if (!preserveItems || paged.isLoading.value || paged.isLoadingMore.value) paged.reset()
            when (type) {
                BookmarkType.ILLUST -> {
                    illustPaged.loadInitial(
                        fetch = { pixivRepository.api.getUserBookmarkedIllusts(uid, "public", tag) },
                        fetchNext = { pixivRepository.api.getNextIllusts(it) },
                    )
                }
                BookmarkType.NOVEL -> {
                    novelPaged.loadInitial(
                        fetch = { pixivRepository.api.getUserBookmarkedNovels(uid, "public", tag) },
                        fetchNext = { pixivRepository.api.getNextNovels(it) },
                    )
                }
            }
        }
    }

    fun retry(type: BookmarkType) {
        if (_type.value != type) return
        val paged = if (type == BookmarkType.ILLUST) illustPaged else novelPaged
        // 与触底加载分开，首屏失败时重新建立游标。
        if (!paged.isLoading.value) loadList(type)
    }

    fun loadMore(type: BookmarkType) {
        if (_type.value != type) return
        viewModelScope.launch {
            when (type) {
                BookmarkType.ILLUST -> illustPaged.loadMore()
                BookmarkType.NOVEL -> novelPaged.loadMore()
            }
        }
    }

    fun toggleIllustFavorite(illustId: Long, nowFavorite: Boolean) =
        toggleFavorite(BookmarkType.ILLUST, illustId, nowFavorite)

    fun toggleNovelFavorite(novelId: Long, nowFavorite: Boolean) =
        toggleFavorite(BookmarkType.NOVEL, novelId, nowFavorite)

    private fun toggleFavorite(type: BookmarkType, id: Long, nowFavorite: Boolean) {
        // 同一作品的收藏请求串行化，避免多个结果反向覆盖。
        val pending = type to id
        if (!pendingFavorites.add(pending)) return
        viewModelScope.launch {
            try {
                val result = when (type) {
                    BookmarkType.ILLUST -> favoriteActions.toggleIllustFavorite(id, nowFavorite)
                    BookmarkType.NOVEL -> favoriteActions.toggleNovelFavorite(id, nowFavorite)
                }
                result.onSuccess {
                    // 只重载当前展示的对应类型，切回其他类型时会从服务端重新取数。
                    if (_type.value == type) loadList(type, preserveItems = true)
                }.onFailure {
                    // 收藏动作的乐观状态已由 FavoriteActions 自动回滚，这里只补发错误提示。
                    trySendMessage(
                        UiMessage(CoreR.string.core_msg_action_failed, listOf(it.message.orEmpty()), MessageType.ERROR),
                    )
                }
            } finally {
                // 失败或协程取消后也允许再次操作。
                pendingFavorites.remove(pending)
            }
        }
    }
}
