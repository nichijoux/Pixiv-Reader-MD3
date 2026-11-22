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

/** 收藏夹类型：插画 / 小说。 */
enum class BookmarkType(@param:StringRes val labelRes: Int) {
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

    /**
     * 切换收藏类型，重新加载该类型的标签与列表。
     *
     * @param type 新选中的收藏类型
     * @return 无返回值
     */
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

    /**
     * 拉取当前类型的收藏标签，切换时取消上一个类型的请求。
     *
     * @return 无返回值
     */
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
     * 按当前标签重载指定收藏列表首页。
     *
     * @param type 要加载的收藏类型
     * @param preserveItems 收藏动作完成后是否尽量保留现有卡片直到新数据返回
     * @return 无返回值
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

    /**
     * 重试当前标签的收藏首页，忽略非当前页和请求进行中的重复操作。
     *
     * @param type 触发重试的收藏类型
     * @return 无返回值
     */
    fun retry(type: BookmarkType) {
        if (_type.value != type) return
        val paged = if (type == BookmarkType.ILLUST) illustPaged else novelPaged
        // 与触底加载分开，首屏失败时重新建立游标。
        if (!paged.isLoading.value) loadList(type)
    }

    /**
     * 加载当前收藏页的下一页。
     *
     * @param type 触发触底的收藏类型
     * @return 无返回值
     */
    fun loadMore(type: BookmarkType) {
        if (_type.value != type) return
        viewModelScope.launch {
            when (type) {
                BookmarkType.ILLUST -> illustPaged.loadMore()
                BookmarkType.NOVEL -> novelPaged.loadMore()
            }
        }
    }

    /**
     * 切换插画的收藏状态。
     *
     * @param illustId 插画 ID
     * @param nowFavorite 目标收藏状态
     * @return 无返回值
     */
    fun toggleIllustFavorite(illustId: Long, nowFavorite: Boolean) =
        toggleFavorite(BookmarkType.ILLUST, illustId, nowFavorite)

    /**
     * 切换小说的收藏状态。
     *
     * @param novelId 小说 ID
     * @param nowFavorite 目标收藏状态
     * @return 无返回值
     */
    fun toggleNovelFavorite(novelId: Long, nowFavorite: Boolean) =
        toggleFavorite(BookmarkType.NOVEL, novelId, nowFavorite)

    /**
     * 调整作品收藏状态，成功后同步列表，失败时提示并恢复卡片原状态。
     *
     * @param type 作品所属收藏类型
     * @param id 作品 ID
     * @param nowFavorite 目标收藏状态
     * @return 无返回值
     */
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
