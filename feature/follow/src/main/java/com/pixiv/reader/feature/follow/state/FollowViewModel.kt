package com.pixiv.reader.feature.follow.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.api.Pageable
import com.pixiv.api.model.Illust
import com.pixiv.api.model.Novel
import com.pixiv.api.model.UserPreview
import com.pixiv.reader.core.common.config.FollowSortMode
import com.pixiv.reader.core.datastore.UserPreferences
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.core.network.session.SessionRepository
import com.pixiv.reader.feature.follow.data.FollowFeedItem
import com.pixiv.reader.feature.follow.data.FollowFeedMerger
import com.pixiv.reader.feature.follow.data.FollowType
import com.pixiv.reader.feature.follow.data.FollowUserSorter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 关注页 ViewModel：左列关注用户 + 右列混合动态流。单用户模式走该用户全部作品三流，
 * 而非在关注新作品流里过滤——官方 follow 流只含最近发布过新作品的用户，长期未更新作者必然为空。
 */
@HiltViewModel
class FollowViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
    sessionRepository: SessionRepository,
    userPreferences: UserPreferences,
    private val favoriteActions: FavoriteActions,
) : ViewModel() {

    private val loggedInUid: Long = sessionRepository.session.loggedInUid

    // 跨页重复 ID 会让列表撞 key 崩溃；用户预览无 user 时返回 null（不参与去重）
    val usersPaged = PagedState<UserPreview>(keySelector = { it.user?.id })

    /** 与「我的」页浏览设置共用同一偏好。 */
    val followSortMode: StateFlow<FollowSortMode> =
        userPreferences.followSortMode.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), FollowSortMode.FOLLOW_TIME,
        )

    val users: StateFlow<List<UserPreview>> =
        combine(usersPaged.items, followSortMode) { list, mode ->
            FollowUserSorter.sort(list, mode)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val illustPaged = PagedState<Illust>(keySelector = { it.id })
    val novelPaged = PagedState<Novel>(keySelector = { it.id })

    val userIllustPaged = PagedState<Illust>(keySelector = { it.id })
    val userMangaPaged = PagedState<Illust>(keySelector = { it.id })
    val userNovelPaged = PagedState<Novel>(keySelector = { it.id })

    /** null = 全部模式。 */
    private val _selectedUserId = MutableStateFlow<Long?>(null)
    val selectedUserId: StateFlow<Long?> = _selectedUserId.asStateFlow()

    // 首页/续页请求成对传入 loadInitial，多个调用点复用收敛为属性避免漂移
    private val fetchFollowUsers: suspend () -> Pageable<UserPreview> =
        { pixivRepository.api.getFollowingUsers(loggedInUid, "public", null) }

    private val fetchFollowUsersNext: suspend (String) -> Pageable<UserPreview> =
        { pixivRepository.api.getNextUsers(it) }

    private val fetchFollowIllust: suspend () -> Pageable<Illust> =
        { pixivRepository.api.getFollowingIllusts("all") }

    private val fetchFollowIllustNext: suspend (String) -> Pageable<Illust> =
        { pixivRepository.api.getNextIllusts(it) }

    private val fetchFollowNovel: suspend () -> Pageable<Novel> =
        { pixivRepository.api.getFollowingNovels("all") }

    private val fetchFollowNovelNext: suspend (String) -> Pageable<Novel> =
        { pixivRepository.api.getNextNovels(it) }

    /** 单用户请求代次：过期代次不得收起当前画师的骨架。 */
    private var userWorksGeneration = 0L

    /** 两种模式分别记录，防止后台关注流完成时关掉当前画师的骨架。 */
    private val globalContentLoading = MutableStateFlow(true)
    private val userContentLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = combine(
        globalContentLoading, userContentLoading, _selectedUserId,
    ) { global, user, uid -> if (uid == null) global else user }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val globalLoadingMore = combine(
        illustPaged.isLoadingMore, novelPaged.isLoadingMore,
    ) { a, b -> a || b }
    private val userLoadingMore = combine(
        userIllustPaged.isLoadingMore, userMangaPaged.isLoadingMore, userNovelPaged.isLoadingMore,
    ) { a, b, c -> a || b || c }
    val isLoadingMore: StateFlow<Boolean> = combine(
        globalLoadingMore, userLoadingMore, _selectedUserId,
    ) { g, u, uid -> if (uid == null) g else u }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val feedTypesWithMore: StateFlow<Set<FollowType>> = combine(
        combine(illustPaged.hasMore, novelPaged.hasMore, ::typesWithMore),
        combine(userIllustPaged.hasMore, userMangaPaged.hasMore, userNovelPaged.hasMore) { illust, manga, novel ->
            typesWithMore(illust || manga, novel)
        },
        _selectedUserId,
    ) { global, user, uid -> if (uid == null) global else user }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private fun typesWithMore(illust: Boolean, novel: Boolean): Set<FollowType> = buildSet {
        // 混合段只需任一上游尚未到底。
        if (illust || novel) add(FollowType.ALL)
        if (illust) add(FollowType.ILLUST)
        if (novel) add(FollowType.NOVEL)
    }

    private val globalFeedError = combine(
        illustPaged.error, novelPaged.error,
    ) { a, b -> a != null || b != null }
    private val userFeedError = combine(
        userIllustPaged.error, userMangaPaged.error, userNovelPaged.error,
    ) { a, b, c -> a != null || b != null || c != null }
    val feedError: StateFlow<Boolean> = combine(
        globalFeedError, userFeedError, _selectedUserId,
    ) { g, u, uid -> if (uid == null) g else u }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val globalPool = combine(illustPaged.items, novelPaged.items) { i, n -> i to n }
    private val userPool = combine(
        userIllustPaged.items, userMangaPaged.items, userNovelPaged.items,
    ) { i, m, n -> Triple(i, m, n) }

    /** 每段独立数据流，各页只 collect 自己那段（滑动切回不重复请求）。 */
    val allItems: StateFlow<List<FollowFeedItem>> = itemsOf(FollowType.ALL)
    val novelItems: StateFlow<List<FollowFeedItem>> = itemsOf(FollowType.NOVEL)
    val illustItems: StateFlow<List<FollowFeedItem>> = itemsOf(FollowType.ILLUST)

    private fun itemsOf(type: FollowType): StateFlow<List<FollowFeedItem>> =
        combine(globalPool, userPool, _selectedUserId) { (gi, gn), (ui, um, un), uid ->
            val illusts: List<Illust> = if (uid == null) gi else ui + um
            val novels: List<Novel> = if (uid == null) gn else un
            // uid 过滤对两种模式均成立：全部模式不过滤；单用户模式数据即该用户作品（一致性校验）
            FollowFeedMerger.filter(FollowFeedMerger.merge(illusts, novels), type, uid)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadAll()
    }

    /** 并行加载左列关注用户与全部模式的插画/小说流。 */
    fun loadAll() {
        if (loggedInUid <= 0L) return // 未登录态防御（正常流程登录后才可见本页）
        viewModelScope.launch {
            globalContentLoading.value = true
            try {
                coroutineScope {
                    // 分页容器分别记录失败并向上传递取消。
                    launch { usersPaged.loadInitial(fetchFollowUsers, fetchFollowUsersNext) }
                    launch { illustPaged.loadInitial(fetchFollowIllust, fetchFollowIllustNext) }
                    launch { novelPaged.loadInitial(fetchFollowNovel, fetchFollowNovelNext) }
                }
            } finally {
                globalContentLoading.value = false
            }
        }
    }

    /** 全部模式只重试失败的空列表，避免清掉已到达的数据。 */
    fun retry() {
        val uid = _selectedUserId.value
        if (uid == null) {
            if (globalContentLoading.value) return
            viewModelScope.launch {
                globalContentLoading.value = true
                try {
                    coroutineScope {
                        if (usersPaged.items.value.isEmpty() && usersPaged.error.value != null) {
                            launch { usersPaged.loadInitial(fetchFollowUsers, fetchFollowUsersNext) }
                        }
                        if (illustPaged.items.value.isEmpty() && illustPaged.error.value != null) {
                            launch { illustPaged.loadInitial(fetchFollowIllust, fetchFollowIllustNext) }
                        }
                        if (novelPaged.items.value.isEmpty() && novelPaged.error.value != null) {
                            launch { novelPaged.loadInitial(fetchFollowNovel, fetchFollowNovelNext) }
                        }
                    }
                } finally {
                    globalContentLoading.value = false
                }
            }
        } else {
            if (userContentLoading.value) return
            viewModelScope.launch { loadUserWorksInternal(uid) }
        }
    }

    fun selectUser(userId: Long?) {
        if (_selectedUserId.value == userId) return
        // 切换用户后立即开始新代次，或在切回全部时作废单用户请求。
        _selectedUserId.value = userId
        if (userId != null) {
            viewModelScope.launch { loadUserWorksInternal(userId) }
        } else {
            userWorksGeneration++
            userContentLoading.value = false
            resetUserWorks()
        }
    }

    /** 三流并行拉首页：保证用户有作品就一定看得到。 */
    private suspend fun loadUserWorksInternal(userId: Long) {
        if (_selectedUserId.value != userId) return
        // reset 使分页容器在写入前丢弃旧首页与旧续页。
        val generation = ++userWorksGeneration
        userContentLoading.value = true
        resetUserWorks()
        try {
            coroutineScope {
                // 立即进入 loadInitial 记录分页代次，再挂起等待接口返回。
                launch(start = CoroutineStart.UNDISPATCHED) {
                    userIllustPaged.loadInitial(
                        fetch = { pixivRepository.api.getUserIllusts(userId, "illust") },
                        fetchNext = { pixivRepository.api.getNextIllusts(it) },
                    )
                }
                launch(start = CoroutineStart.UNDISPATCHED) {
                    userMangaPaged.loadInitial(
                        fetch = { pixivRepository.api.getUserIllusts(userId, "manga") },
                        fetchNext = { pixivRepository.api.getNextIllusts(it) },
                    )
                }
                launch(start = CoroutineStart.UNDISPATCHED) {
                    userNovelPaged.loadInitial(
                        fetch = { pixivRepository.api.getUserNovels(userId) },
                        fetchNext = { pixivRepository.api.getNextNovels(it) },
                    )
                }
            }
        } finally {
            // 过期代次不得关闭当前画师的骨架。
            if (generation == userWorksGeneration) userContentLoading.value = false
        }
    }

    private fun resetUserWorks() {
        userIllustPaged.reset()
        userMangaPaged.reset()
        userNovelPaged.reset()
    }

    /** 按当前模式重拉第一页，完成后才复位指示（防重入）；左列关注用户列表不随动。 */
    fun pullRefresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val uid = _selectedUserId.value
                if (uid == null) {
                    globalContentLoading.value = true
                    try {
                        coroutineScope {
                            launch {
                                illustPaged.reset()
                                illustPaged.loadInitial(fetchFollowIllust, fetchFollowIllustNext)
                            }
                            launch {
                                novelPaged.reset()
                                novelPaged.loadInitial(fetchFollowNovel, fetchFollowNovelNext)
                            }
                        }
                    } finally {
                        globalContentLoading.value = false
                    }
                } else {
                    loadUserWorksInternal(uid)
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun loadMoreUsers() {
        if (!usersPaged.isLoadingMore.value && usersPaged.hasMore.value) {
            viewModelScope.launch { usersPaged.loadMore() }
        }
    }

    /** 只推进当前类型段的上游；混合段按上次实际推进的来源轮询，保证各上游都能续页。 */
    fun loadMoreFeed(type: FollowType) {
        viewModelScope.launch {
            val singleUser = _selectedUserId.value != null
            val candidates: List<PagedState<*>> = when (type) {
                FollowType.ALL ->
                    if (singleUser) listOf(userIllustPaged, userMangaPaged, userNovelPaged)
                    else listOf(illustPaged, novelPaged)
                FollowType.NOVEL -> listOf(if (singleUser) userNovelPaged else novelPaged)
                FollowType.ILLUST ->
                    if (singleUser) listOf(userIllustPaged, userMangaPaged)
                    else listOf(illustPaged)
            }

            // 点到混合段时若共享来源仍在翻页，等其完成以免并发重复拉取。
            if (candidates.any { it.isLoadingMore.value }) return@launch
            val key = singleUser to type
            val start = nextFeedSource[key] ?: 0
            val index = candidates.indices
                .map { (start + it) % candidates.size }
                .firstOrNull { candidates[it].hasMore.value && !candidates[it].isLoading.value }
                ?: return@launch
            nextFeedSource[key] = (index + 1) % candidates.size
            candidates[index].loadMore()
        }
    }

    private val nextFeedSource = mutableMapOf<Pair<Boolean, FollowType>, Int>()

    /** nowFavorite 是目标状态（组件回调传入），无需先读本地缓存。 */
    fun toggleIllustFavorite(illustId: Long, nowFavorite: Boolean) =
        favoriteActions.toggleIllustFavoriteSilent(viewModelScope, illustId, nowFavorite)

    fun toggleNovelFavorite(novelId: Long, nowFavorite: Boolean) =
        favoriteActions.toggleNovelFavoriteSilent(viewModelScope, novelId, nowFavorite)
}
