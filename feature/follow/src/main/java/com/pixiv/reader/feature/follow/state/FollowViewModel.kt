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
 * 关注页 ViewModel：左列关注用户列表 + 右列混合动态流。
 *
 * ## 数据源（两种模式）
 * - **全部模式（[selectedUserId] == null）**：关注**新作品**流——`v2/illust/follow` +
 *   `v1/novel/follow`（官方语义：仅返回最近发布过新作品的关注用户）
 * - **单用户模式**：该用户的**全部作品**——`v1/user/illusts?type=illust|manga` +
 *   `v1/user/novels`（三流并行，切用户时 reset 重载；保证用户有作品就一定能看到，
 *   而非只在关注新作品流里过滤——后者对长期未更新的作者必然为空）
 *
 * ## 混合与筛选
 * [FollowFeedMerger] 合并插画/漫画/小说流按 create_date 倒序；类型段由
 * HorizontalPager 页决定（ALL/NOVEL/ILLUST 三个独立 StateFlow，数据驻留 VM）。
 *
 * ## 触底加载
 * 只推进当前类型段的上游。混合段轮询所有上游，单用户插画段轮询插画与漫画。
 */
@HiltViewModel
class FollowViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
    sessionRepository: SessionRepository,
    userPreferences: UserPreferences,
    private val favoriteActions: FavoriteActions,
) : ViewModel() {

    private val loggedInUid: Long = sessionRepository.session.loggedInUid

    /** 左列：关注用户列表。 */
    // 跨页重复 ID 会让列表撞 key 崩溃；用户预览无 user 时返回 null（不参与去重）
    val usersPaged = PagedState<UserPreview>(keySelector = { it.user?.id })

    /** 关注页左列排序设置（我的页-浏览设置可改）。 */
    val followSortMode: StateFlow<FollowSortMode> =
        userPreferences.followSortMode.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), FollowSortMode.FOLLOW_TIME,
        )

    /** 左列用户（按设置排序；分页追加后自动重排）。 */
    val users: StateFlow<List<UserPreview>> =
        combine(usersPaged.items, followSortMode) { list, mode ->
            FollowUserSorter.sort(list, mode)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 全部模式：关注新作品流。 */
    val illustPaged = PagedState<Illust>(keySelector = { it.id })
    val novelPaged = PagedState<Novel>(keySelector = { it.id })

    /** 单用户模式：该用户全部作品（插画 / 漫画 / 小说）。 */
    val userIllustPaged = PagedState<Illust>(keySelector = { it.id })
    val userMangaPaged = PagedState<Illust>(keySelector = { it.id })
    val userNovelPaged = PagedState<Novel>(keySelector = { it.id })

    /** 当前选中的用户 ID（null = 全部）。 */
    private val _selectedUserId = MutableStateFlow<Long?>(null)
    val selectedUserId: StateFlow<Long?> = _selectedUserId.asStateFlow()

    // ── 全部模式三流请求 lambda（loadInitial 需同时传首页与后续页请求，
    //    loadAll/retry/pullRefresh 多处复用，收敛为属性避免复制粘贴漂移） ──

    /** 左列关注用户列表（首页请求）。 */
    private val fetchFollowUsers: suspend () -> Pageable<UserPreview> =
        { pixivRepository.api.getFollowingUsers(loggedInUid, "public", null) }

    /** 左列关注用户列表（后续页请求）。 */
    private val fetchFollowUsersNext: suspend (String) -> Pageable<UserPreview> =
        { pixivRepository.api.getNextUsers(it) }

    /** 关注新作品流——插画（首页请求）。 */
    private val fetchFollowIllust: suspend () -> Pageable<Illust> =
        { pixivRepository.api.getFollowingIllusts("all") }

    /** 关注新作品流——插画（后续页请求）。 */
    private val fetchFollowIllustNext: suspend (String) -> Pageable<Illust> =
        { pixivRepository.api.getNextIllusts(it) }

    /** 关注新作品流——小说（首页请求）。 */
    private val fetchFollowNovel: suspend () -> Pageable<Novel> =
        { pixivRepository.api.getFollowingNovels("all") }

    /** 关注新作品流——小说（后续页请求）。 */
    private val fetchFollowNovelNext: suspend (String) -> Pageable<Novel> =
        { pixivRepository.api.getNextNovels(it) }

    /** 单用户视图当前加载代次，用于保护骨架的收尾。 */
    private var userWorksGeneration = 0L

    /**
     * 内容加载中（首载 / 切用户重载时显示骨架）。
     * 两个模式分别记录，防止后台关注流完成时关闭当前画师的加载态。
     */
    private val globalContentLoading = MutableStateFlow(true)
    private val userContentLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = combine(
        globalContentLoading, userContentLoading, _selectedUserId,
    ) { global, user, uid -> if (uid == null) global else user }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 下拉刷新指示（PullToRefreshBox 用，按当前模式生效）。 */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** 触底加载中（当前模式各流任一加载下一页）。 */
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

    /** 当前模式下仍可续页的类型段，供各页控制触底项。 */
    val feedTypesWithMore: StateFlow<Set<FollowType>> = combine(
        combine(illustPaged.hasMore, novelPaged.hasMore, ::typesWithMore),
        combine(userIllustPaged.hasMore, userMangaPaged.hasMore, userNovelPaged.hasMore) { illust, manga, novel ->
            typesWithMore(illust || manga, novel)
        },
        _selectedUserId,
    ) { global, user, uid -> if (uid == null) global else user }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /**
     * 从插画与小说的游标状态派生可续页的类型段。
     *
     * @param illust 插画段是否还有插画或漫画可加载
     * @param novel 小说段是否还有小说可加载
     * @return 可继续分页的类型段
     */
    private fun typesWithMore(illust: Boolean, novel: Boolean): Set<FollowType> = buildSet {
        // 混合段只需任一上游尚未到底。
        if (illust || novel) add(FollowType.ALL)
        if (illust) add(FollowType.ILLUST)
        if (novel) add(FollowType.NOVEL)
    }

    /** 当前模式内容流任一请求出错（首屏失败，UI 显示重试）。 */
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

    /** 两个模式的内容池：全部模式（关注流）/ 单用户模式（该用户全部作品）。 */
    private val globalPool = combine(illustPaged.items, novelPaged.items) { i, n -> i to n }
    private val userPool = combine(
        userIllustPaged.items, userMangaPaged.items, userNovelPaged.items,
    ) { i, m, n -> Triple(i, m, n) }

    /** 三个类型段的展示列表（每页 collect 自己的流；单用户模式数据本身即该用户作品）。 */
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

    /**
     * 并行加载关注用户和全部模式的两个内容流。
     *
     * @return 无返回值
     */
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

    /**
     * 重试当前模式的首屏。全部模式只重试失败的空列表。
     *
     * @return 无返回值
     */
    fun retry() {
        val uid = _selectedUserId.value
        if (uid == null) {
            if (globalContentLoading.value) return
            viewModelScope.launch {
                globalContentLoading.value = true
                try {
                    coroutineScope {
                        // 左列关注用户失败时随首屏一并重试。
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

    /**
     * 切换左列当前选中的画师或返回全部关注动态。
     *
     * @param userId 选中的用户 ID，null 表示全部
     * @return 无返回值
     */
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

    /**
     * 开启单用户请求的新代次，并行请求该用户三个作品流的首页。
     *
     * @param userId 当前选中画师的 ID
     * @return 无返回值
     */
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

    /**
     * 清除单用户作品数据与游标，使在途分页全部失效。
     *
     * @return 无返回值
     */
    private fun resetUserWorks() {
        userIllustPaged.reset()
        userMangaPaged.reset()
        userNovelPaged.reset()
    }

    /**
     * 下拉刷新：按当前模式重拉第一页（全部模式：关注新作品 illust/novel 两流并行；
     * 单用户模式：该用户全部作品三流），全部完成后复位指示（防重入）。左列关注用户列表不随动。
     *
     * @return 无返回值
     */
    fun pullRefresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val uid = _selectedUserId.value
                if (uid == null) {
                    // 全部模式刷新只控制自身加载标志。
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
                    // 单用户模式直接开启新代次，等待完成再收起下拉刷新指示器。
                    loadUserWorksInternal(uid)
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** 左列触底：加载更多关注用户。 */
    fun loadMoreUsers() {
        if (!usersPaged.isLoadingMore.value && usersPaged.hasMore.value) {
            viewModelScope.launch { usersPaged.loadMore() }
        }
    }

    /**
     * 只推进当前类型段的分页，存在多个上游时按上次实际推进的来源轮询。
     *
     * @param type 触底的类型段
     * @return 无返回值
     */
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

    /** 收藏 / 取消收藏插画（nowFavorite 为目标状态，由组件回调）。 */
    fun toggleIllustFavorite(illustId: Long, nowFavorite: Boolean) =
        favoriteActions.toggleIllustFavoriteSilent(viewModelScope, illustId, nowFavorite)

    /** 收藏 / 取消收藏小说。 */
    fun toggleNovelFavorite(novelId: Long, nowFavorite: Boolean) =
        favoriteActions.toggleNovelFavoriteSilent(viewModelScope, novelId, nowFavorite)
}
