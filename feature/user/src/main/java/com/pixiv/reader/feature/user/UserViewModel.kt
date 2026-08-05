package com.pixiv.reader.feature.user

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.BlockSaveRequest
import com.pixiv.api.model.Illust
import com.pixiv.api.model.Novel
import com.pixiv.api.model.Profile
import com.pixiv.api.model.User
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.database.dao.BrowseHistoryDao
import com.pixiv.reader.core.database.entity.BrowseHistoryEntity
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

/** 用户主页作品分区。 */
enum class UserSection(@StringRes val labelRes: Int) {
    ILLUST(R.string.user_section_illust),
    MANGA(R.string.user_section_manga),
    NOVEL(R.string.user_section_novel),
}

/**
 * 用户主页 ViewModel：用户详情（统计 / 关注态）+ 分区作品列表（插画 / 漫画 / 小说）+ 关注 / 取关。
 */
@HiltViewModel
class UserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
    private val browseHistoryDao: BrowseHistoryDao,
) : ViewModel() {

    private val userId: Long = savedStateHandle.get<Long>("userId") ?: 0L

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error.asStateFlow()

    private val _isFollowed = MutableStateFlow(false)
    val isFollowed: StateFlow<Boolean> = _isFollowed.asStateFlow()

    private val _isFollowing = MutableStateFlow(false)
    val isFollowing: StateFlow<Boolean> = _isFollowing.asStateFlow()

    private val _section = MutableStateFlow(UserSection.ILLUST)
    val section: StateFlow<UserSection> = _section.asStateFlow()

    /** 是否已拉黑该用户（通过网页版用户详情 isBlocking 初始化） */
    private val _isBlocked = MutableStateFlow(false)
    val isBlocked: StateFlow<Boolean> = _isBlocked.asStateFlow()

    private val _isBlocking = MutableStateFlow(false)
    val isBlocking: StateFlow<Boolean> = _isBlocking.asStateFlow()

    private val _message = Channel<UiMessage>(Channel.BUFFERED)
    val message = _message.receiveAsFlow()

    val illustPaged = PagedState<Illust>()
    val mangaPaged = PagedState<Illust>()
    val novelPaged = PagedState<Novel>()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching { pixivRepository.api.getUserDetail(userId) }
                .onSuccess { resp ->
                    _user.value = resp.user
                    _profile.value = resp.profile
                    _isFollowed.value = resp.user?.is_followed == true
                    recordHistory(resp.user)
                    loadSection(_section.value)
                    loadBlockState()
                }
                .onFailure {
                    _error.value = UiMessage(R.string.user_load_failed, listOf(it.message ?: ""))
                }
            _isLoading.value = false
        }
    }

    /** 打开用户主页时写入浏览历史（先删旧记录避免重复）。 */
    private fun recordHistory(user: User?) {
        if (user == null) return
        viewModelScope.launch {
            runCatching {
                browseHistoryDao.deleteByTarget("user", user.id)
                browseHistoryDao.upsert(
                    BrowseHistoryEntity(
                        targetType = "user",
                        targetId = user.id,
                        title = user.name,
                        coverUrl = user.profile_image_urls?.best(),
                    ),
                )
            }
        }
    }

    /** 网页版用户详情含 isBlocking（我是否拉黑了对方），初始化拉黑态。 */
    private fun loadBlockState() {
        viewModelScope.launch {
            runCatching {
                pixivRepository.webApi.getWebUserDetail(userId).body?.isBlocking
            }.onSuccess { blocked ->
                if (blocked != null) _isBlocked.value = blocked
            }
        }
    }

    fun selectSection(section: UserSection) {
        if (_section.value == section) return
        _section.value = section
        if (!hasLoaded(section)) loadSection(section)
    }

    private fun hasLoaded(section: UserSection): Boolean = when (section) {
        UserSection.ILLUST -> illustPaged.items.value.isNotEmpty() || illustPaged.isLoading.value
        UserSection.MANGA -> mangaPaged.items.value.isNotEmpty() || mangaPaged.isLoading.value
        UserSection.NOVEL -> novelPaged.items.value.isNotEmpty() || novelPaged.isLoading.value
    }

    private fun loadSection(section: UserSection) {
        viewModelScope.launch {
            when (section) {
                UserSection.ILLUST -> illustPaged.loadInitial(
                    fetch = { pixivRepository.api.getUserIllusts(userId, "illust") },
                    fetchNext = { pixivRepository.api.getNextIllusts(it) },
                )
                UserSection.MANGA -> mangaPaged.loadInitial(
                    fetch = { pixivRepository.api.getUserIllusts(userId, "manga") },
                    fetchNext = { pixivRepository.api.getNextIllusts(it) },
                )
                UserSection.NOVEL -> novelPaged.loadInitial(
                    fetch = { pixivRepository.api.getUserNovels(userId) },
                    fetchNext = { pixivRepository.api.getNextNovels(it) },
                )
            }
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            when (_section.value) {
                UserSection.ILLUST -> illustPaged.loadMore()
                UserSection.MANGA -> mangaPaged.loadMore()
                UserSection.NOVEL -> novelPaged.loadMore()
            }
        }
    }

    /** 关注 / 取关（即时反馈）。 */
    fun toggleFollow() {
        if (_isFollowing.value) return
        viewModelScope.launch {
            _isFollowing.value = true
            val current = _isFollowed.value
            runCatching {
                if (current) pixivRepository.api.unfollowUser(userId)
                else pixivRepository.api.followUser(userId, "public")
            }.onSuccess {
                _isFollowed.value = !current
                _message.send(UiMessage(if (!current) R.string.user_followed else R.string.user_unfollowed))
            }.onFailure {
                _message.send(UiMessage(R.string.operation_failed, listOf(it.message ?: "")))
            }
            _isFollowing.value = false
        }
    }

    /** 拉黑 / 取消拉黑（网页接口 saveBlock，需要 CSRF token）。 */
    fun toggleBlock() {
        if (_isBlocking.value) return
        viewModelScope.launch {
            _isBlocking.value = true
            val token = csrfToken()
            if (token.isNullOrBlank()) {
                _message.send(UiMessage(R.string.user_csrf_unavailable))
                _isBlocking.value = false
                return@launch
            }
            val current = _isBlocked.value
            runCatching {
                pixivRepository.webApi.saveBlock(
                    token,
                    BlockSaveRequest(
                        user_id = userId.toString(),
                        action = if (current) "unblock" else "block",
                    ),
                )
            }.onSuccess {
                _isBlocked.value = !current
                _message.send(UiMessage(if (!current) R.string.user_blocked else R.string.user_unblocked_user))
            }.onFailure {
                _message.send(UiMessage(R.string.operation_failed, listOf(it.message ?: "")))
            }
            _isBlocking.value = false
        }
    }

    /** 从网页 Cookie 中解析 csrf_token（pixiv 网页写操作要求 x-csrf-token 头）。 */
    private fun csrfToken(): String? {
        return pixivRepository.pixivApi.session.cookie()
            .split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith("csrf_token=") }
            ?.substringAfter('=')
    }

    /** 收藏 / 取消收藏小说（nowFavorite 为目标状态，由组件回调）。 */
    fun toggleNovelFavorite(novelId: Long, nowFavorite: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (nowFavorite) pixivRepository.api.bookmarkNovel(novelId, "public", emptyList())
                else pixivRepository.api.unbookmarkNovel(novelId)
            }
        }
    }
}
