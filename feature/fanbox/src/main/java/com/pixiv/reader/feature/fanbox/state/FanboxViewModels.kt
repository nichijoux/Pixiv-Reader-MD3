package com.pixiv.reader.feature.fanbox.state

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.api.auth.SessionManager
import com.pixiv.api.model.FanboxCreator
import com.pixiv.api.model.FanboxPost
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * FANBOX 关注新帖流 ViewModel（`POST post.listFollowing`）。
 * 未登录（无 FANBOX cookie）时 [needsLogin] 为 true，UI 渲染内嵌登录门。
 */
@HiltViewModel
class FanboxHomeViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    /** 是否需要内嵌登录（无 FANBOX 登录态；登录页返回后重查）。 */
    private val _needsLogin = MutableStateFlow(
        sessionManager.fanboxCookie().contains("FANBOX_SESSIONID").not(),
    )
    val needsLogin: StateFlow<Boolean> = _needsLogin.asStateFlow()

    /** 关注创作者新帖分页（nextUrl = 末帖 id 游标）。 */
    val paged = PagedState<FanboxPost>()

    init {
        if (!_needsLogin.value) load()
    }

    /** 重新检查登录态（登录页返回后调用），已登录且未加载过则加载数据。 */
    fun recheckAndLoad() {
        val logged = sessionManager.fanboxCookie().contains("FANBOX_SESSIONID")
        _needsLogin.value = !logged
        if (logged && paged.items.value.isEmpty() && !paged.isLoading.value) load()
    }

    /** 首次加载 / 失败重试；翻页把响应 nextUrl（末帖 id）回传为 parentId。 */
    fun load() {
        viewModelScope.launch {
            paged.loadInitial(
                fetch = { pixivRepository.fanboxApi.postListFollowing() },
                fetchNext = { pixivRepository.fanboxApi.postListFollowing(parentId = it) },
            )
        }
    }

    /** 触底加载更多。 */
    fun loadMore() {
        viewModelScope.launch { paged.loadMore() }
    }
}

/**
 * FANBOX 关注创作者列表 ViewModel（`POST user.listFollowing`）。
 */
@HiltViewModel
class FanboxCreatorListViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    /** 是否需要内嵌登录。 */
    private val _needsLogin = MutableStateFlow(
        sessionManager.fanboxCookie().contains("FANBOX_SESSIONID").not(),
    )
    val needsLogin: StateFlow<Boolean> = _needsLogin.asStateFlow()

    /** 关注创作者分页。 */
    val paged = PagedState<FanboxCreator>()

    init {
        if (!_needsLogin.value) load()
    }

    /** 登录页返回后重查登录态并按需加载。 */
    fun recheckAndLoad() {
        val logged = sessionManager.fanboxCookie().contains("FANBOX_SESSIONID")
        _needsLogin.value = !logged
        if (logged && paged.items.value.isEmpty() && !paged.isLoading.value) load()
    }

    /** 首次加载 / 失败重试。 */
    fun load() {
        viewModelScope.launch {
            paged.loadInitial(
                fetch = { pixivRepository.fanboxApi.userFollowing() },
                fetchNext = { pixivRepository.fanboxApi.userFollowing() },
            )
        }
    }
}

/**
 * FANBOX 创作者帖子流 ViewModel（`POST post.listCreator`，creatorId 从路由参数读取）。
 */
@HiltViewModel
class FanboxCreatorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
) : ViewModel() {

    /** 创作者 id（路由参数）。 */
    private val creatorId: String = savedStateHandle.get<String>("creatorId").orEmpty()

    /** 帖子分页。 */
    val paged = PagedState<FanboxPost>()

    init {
        load()
    }

    /** 首次加载 / 失败重试。 */
    fun load() {
        viewModelScope.launch {
            paged.loadInitial(
                fetch = { pixivRepository.fanboxApi.postListCreator(creatorId = creatorId) },
                fetchNext = { pixivRepository.fanboxApi.postListCreator(creatorId = creatorId, parentId = it) },
            )
        }
    }

    /** 触底加载更多。 */
    fun loadMore() {
        viewModelScope.launch { paged.loadMore() }
    }
}

/**
 * FANBOX 帖子详情 ViewModel（`POST post.info`，postId 从路由参数读取）。
 */
@HiltViewModel
class FanboxPostViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
) : ViewModel() {

    /** 帖子 id（路由参数）。 */
    private val postId: String = savedStateHandle.get<String>("postId").orEmpty()

    private val _post = MutableStateFlow<FanboxPost?>(null)
    val post: StateFlow<FanboxPost?> = _post.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        load()
    }

    /** 加载帖子详情（失败重试同入口）。 */
    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching { pixivRepository.fanboxApi.postInfo(postId = postId) }
                .onSuccess { _post.value = it.body }
                .onFailure { _error.value = it.message.orEmpty().ifBlank { "error" } }
            _isLoading.value = false
        }
    }
}
