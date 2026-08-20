package com.pixiv.reader.feature.follow.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.Illust
import com.pixiv.api.model.Novel
import com.pixiv.api.model.UserPreview
import com.pixiv.reader.core.common.FollowSortMode
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
 * 各模式内多流轮询推进（全部模式 illust/novel 交替；单用户模式
 * illust→manga→novel 循环），新页更旧、稳定落在时间线尾部。
 */
@HiltViewModel
class FollowViewModel @Inject constructor(
    private val pixivRepository: PixivRepository,
    sessionRepository: SessionRepository,
    private val userPreferences: UserPreferences,
    private val favoriteActions: FavoriteActions,
) : ViewModel() {

    private val loggedInUid: Long = sessionRepository.session.loggedInUid

    /** 左列：关注用户列表。 */
    val usersPaged = PagedState<UserPreview>()

    /** 关注页左列排序设置（我的页-浏览设置可改）。 */
    val followSortMode: StateFlow<FollowSortMode> =
        userPreferences.followSortMode.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), FollowSortMode.FOLLOW_TIME,
        )

    /** 左列用户（按设置排序；分页追加后自动重排）。 */
    val users: StateFlow<List<UserPreview>> = combine(usersPaged.items, followSortMode) { list, mode ->
        FollowUserSorter.sort(list, mode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 全部模式：关注新作品流。 */
    val illustPaged = PagedState<Illust>()
    val novelPaged = PagedState<Novel>()

    /** 单用户模式：该用户全部作品（插画 / 漫画 / 小说）。 */
    val userIllustPaged = PagedState<Illust>()
    val userMangaPaged = PagedState<Illust>()
    val userNovelPaged = PagedState<Novel>()

    /** 当前选中的用户 ID（null = 全部）。 */
    private val _selectedUserId = MutableStateFlow<Long?>(null)
    val selectedUserId: StateFlow<Long?> = _selectedUserId.asStateFlow()

    /** 当前正在加载作品的用户（幂等：重复点击同用户不重载）。 */
    private var currentUserWorksId: Long? = null

    /**
     * 内容加载中（首载 / 切用户重载时显示骨架）。
     * 手动标志：由 [loadAll]/[loadUserWorks]/[retry] 的 coroutineScope 统一置位——
     * 避免 combine 首发射 false 导致 UI 闪现「暂无动态」（竞态）。
     */
    private val _contentLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _contentLoading.asStateFlow()

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

    /** 首屏加载：关注用户 + 全部模式两流（并行，全部结束后清除加载态）。 */
    fun loadAll() {
        if (loggedInUid <= 0L) return // 未登录态防御（正常流程登录后才可见本页）
        viewModelScope.launch {
            _contentLoading.value = true
            coroutineScope {
                launch { runCatching { usersPaged.loadInitial(
                    fetch = { pixivRepository.api.getFollowingUsers(loggedInUid, "public", null) },
                    fetchNext = { pixivRepository.api.getNextUsers(it) },
                ) } }
                launch { runCatching { illustPaged.loadInitial(
                    fetch = { pixivRepository.api.getFollowingIllusts("all") },
                    fetchNext = { pixivRepository.api.getNextIllusts(it) },
                ) } }
                launch { runCatching { novelPaged.loadInitial(
                    fetch = { pixivRepository.api.getFollowingNovels("all") },
                    fetchNext = { pixivRepository.api.getNextNovels(it) },
                ) } }
            }
            _contentLoading.value = false
        }
    }

    /** 重试：仅重新加载当前模式失败的流（空列表的流）。 */
    fun retry() {
        val uid = _selectedUserId.value
        if (uid == null) {
            viewModelScope.launch {
                _contentLoading.value = true
                coroutineScope {
                    if (illustPaged.items.value.isEmpty() && illustPaged.error.value != null) {
                        launch { runCatching { illustPaged.loadInitial(
                            fetch = { pixivRepository.api.getFollowingIllusts("all") },
                            fetchNext = { pixivRepository.api.getNextIllusts(it) },
                        ) } }
                    }
                    if (novelPaged.items.value.isEmpty() && novelPaged.error.value != null) {
                        launch { runCatching { novelPaged.loadInitial(
                            fetch = { pixivRepository.api.getFollowingNovels("all") },
                            fetchNext = { pixivRepository.api.getNextNovels(it) },
                        ) } }
                    }
                }
                _contentLoading.value = false
            }
        } else {
            // 强制重载：currentUserWorksId 幂等短路会拦掉重试，先清掉
            currentUserWorksId = null
            loadUserWorks(uid)
        }
    }

    /** 选中左列用户（null = 全部；选中用户时加载其全部作品）。 */
    fun selectUser(userId: Long?) {
        _selectedUserId.value = userId
        if (userId != null) {
            loadUserWorks(userId)
        } else {
            // 切回「全部」允许下次再选同用户时重载（刷新最新作品）
            currentUserWorksId = null
        }
    }

    /**
     * 加载指定用户的全部作品（插画 + 漫画 + 小说，并行；同用户幂等）。
     * 代次 [userWorksGeneration]：快速切用户时，过期代次的响应落地后立即清空，
     * 防止旧用户数据覆盖当前选中用户。
     */
    private fun loadUserWorks(userId: Long) {
        if (currentUserWorksId == userId) return
        currentUserWorksId = userId
        val generation = ++userWorksGeneration
        listOf(userIllustPaged, userMangaPaged, userNovelPaged).forEach { it.reset() }
        viewModelScope.launch {
            _contentLoading.value = true
            coroutineScope {
                launch {
                    runCatching {
                        userIllustPaged.loadInitial(
                            fetch = { pixivRepository.api.getUserIllusts(userId, "illust") },
                            fetchNext = { pixivRepository.api.getNextIllusts(it) },
                        )
                    }
                    if (generation != userWorksGeneration) userIllustPaged.reset()
                }
                launch {
                    runCatching {
                        userMangaPaged.loadInitial(
                            fetch = { pixivRepository.api.getUserIllusts(userId, "manga") },
                            fetchNext = { pixivRepository.api.getNextIllusts(it) },
                        )
                    }
                    if (generation != userWorksGeneration) userMangaPaged.reset()
                }
                launch {
                    runCatching {
                        userNovelPaged.loadInitial(
                            fetch = { pixivRepository.api.getUserNovels(userId) },
                            fetchNext = { pixivRepository.api.getNextNovels(it) },
                        )
                    }
                    if (generation != userWorksGeneration) userNovelPaged.reset()
                }
            }
            // 仅当前代次收尾时清除加载态（过期代次不得提前清，避免骨架闪现空态）
            if (generation == userWorksGeneration) _contentLoading.value = false
        }
    }

    private var userWorksGeneration = 0L

    /** 左列触底：加载更多关注用户。 */
    fun loadMoreUsers() {
        if (!usersPaged.isLoadingMore.value && usersPaged.hasMore.value) {
            viewModelScope.launch { usersPaged.loadMore() }
        }
    }

    /**
     * 混合流触底：当前模式多流轮询推进下一页（全部：illust/novel 交替；
     * 单用户：illust→manga→novel 循环；均到底后无操作）。
     */
    fun loadMoreFeed() {
        if (isLoadingMore.value) return
        val uid = _selectedUserId.value
        if (uid == null) {
            val first = if (loadMoreSide) illustPaged else novelPaged
            val second = if (loadMoreSide) novelPaged else illustPaged
            viewModelScope.launch {
                if (first.hasMore.value) first.loadMore()
                else if (second.hasMore.value) second.loadMore()
            }
            loadMoreSide = !loadMoreSide
        } else {
            val candidates = listOf(userIllustPaged, userMangaPaged, userNovelPaged)
            viewModelScope.launch {
                val side = (userLoadSide + candidates.size) % candidates.size
                for (i in 0 until candidates.size) {
                    val paged = candidates[(side + i) % candidates.size]
                    if (paged.hasMore.value) {
                        paged.loadMore()
                        break
                    }
                }
            }
            userLoadSide++
        }
    }

    private var loadMoreSide = false
    private var userLoadSide = 0

    /** 收藏 / 取消收藏插画（nowFavorite 为目标状态，由组件回调）。 */
    fun toggleIllustFavorite(illustId: Long, nowFavorite: Boolean) {
        viewModelScope.launch {
            favoriteActions.toggleIllustFavorite(illustId, nowFavorite)
        }
    }

    /** 收藏 / 取消收藏小说。 */
    fun toggleNovelFavorite(novelId: Long, nowFavorite: Boolean) {
        viewModelScope.launch {
            favoriteActions.toggleNovelFavorite(novelId, nowFavorite)
        }
    }
}
