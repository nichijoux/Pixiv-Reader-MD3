package com.pixiv.reader.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.enableSavedStateHandles
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import com.pixiv.reader.app.R
import com.pixiv.reader.core.common.model.TagType
import com.pixiv.reader.core.ui.component.layout.AdaptiveNavItem
import com.pixiv.reader.core.ui.component.layout.AdaptiveNavScaffold
import com.pixiv.reader.core.ui.theme.SettingAccentBlue
import com.pixiv.reader.core.ui.theme.SettingAccentGreen
import com.pixiv.reader.core.ui.theme.SettingAccentPink
import com.pixiv.reader.core.ui.theme.SettingAccentTeal
import com.pixiv.reader.core.ui.theme.SettingAccentViolet
import com.pixiv.reader.feature.discover.ui.DiscoverRoute
import com.pixiv.reader.feature.follow.ui.FollowRoute
import com.pixiv.reader.feature.home.HomeRoute
import com.pixiv.reader.feature.manga.MangaRoute
import com.pixiv.reader.feature.novel.ui.NovelRoute
import com.pixiv.reader.feature.user.ui.MeRoute
import com.pixiv.reader.feature.watchlist.WatchlistSegment


/**
 * 随 MainShell 所在的顶层 backStackEntry 存活：隔离 5 个常驻 Tab 各自的 ViewModelStore
 * （防止平板详情 pane 的 IllustViewModel / NovelViewModel / CommentListViewModel 跨 Tab 串用），
 * 旋转不丢失状态，MainShell 离栈时统一清空。
 */
internal class MainShellTabStoreViewModel : ViewModel() {
    private val stores = mutableMapOf<String, ViewModelStore>()

    fun storeFor(route: String): ViewModelStore = stores.getOrPut(route) { ViewModelStore() }

    override fun onCleared() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }
}

/**
 * 单个常驻 Tab 的独立宿主（ViewModelStore + SavedStateRegistry + 受控 Lifecycle）。
 * 非激活态将 Lifecycle 降到 CREATED：自动暂停隐藏页的 collectAsStateWithLifecycle 与 BackHandler，
 * 同时保留整棵 Compose 树与滚动状态，切回毫秒级恢复零重建。
 */
private class TabScopeOwner(
    override val viewModelStore: ViewModelStore,
    private val parentFactoryOwner: HasDefaultViewModelProviderFactory,
) : ViewModelStoreOwner,
    HasDefaultViewModelProviderFactory,
    SavedStateRegistryOwner,
    LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    init {
        savedStateController.performAttach()
        enableSavedStateHandles()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = parentFactoryOwner.defaultViewModelProviderFactory

    override val defaultViewModelCreationExtras: CreationExtras
        get() = MutableCreationExtras(parentFactoryOwner.defaultViewModelCreationExtras).apply {
            set(SAVED_STATE_REGISTRY_OWNER_KEY, this@TabScopeOwner)
            set(VIEW_MODEL_STORE_OWNER_KEY, this@TabScopeOwner)
        }

    fun moveTo(state: Lifecycle.State) {
        if (lifecycleRegistry.currentState != state &&
            lifecycleRegistry.currentState != Lifecycle.State.DESTROYED
        ) {
            lifecycleRegistry.currentState = state
        }
    }
}

@Composable
private fun rememberTabs(): List<AdaptiveNavItem> = listOf(
    AdaptiveNavItem(
        "home_tab",
        stringResource(R.string.main_tab_home),
        Icons.Filled.Home,
        accent = SettingAccentBlue,
    ),
    AdaptiveNavItem(
        "manga_tab",
        stringResource(R.string.main_tab_manga),
        Icons.Filled.Collections,
        accent = SettingAccentViolet,
    ),
    AdaptiveNavItem(
        "novel_tab",
        stringResource(R.string.main_tab_novel),
        Icons.AutoMirrored.Filled.MenuBook,
        accent = SettingAccentTeal,
    ),
    AdaptiveNavItem(
        "follow_tab",
        stringResource(R.string.main_tab_follow),
        Icons.Filled.Favorite,
        accent = SettingAccentPink,
    ),
    AdaptiveNavItem(
        "me_tab",
        stringResource(R.string.main_tab_me),
        Icons.Filled.Person,
        accent = SettingAccentGreen,
    ),
)

/** 跨 Tab 搜索载荷，由 DiscoverRoute 一次性消费。 */
data class SearchLaunch(val query: String, val tagType: TagType = TagType.ILLUST)

/** 登录后主壳：跨 Tab 搜索经 pendingSearch 单次消费，深链进入时以 discover_tab 为单条目栈。 */
@Composable
fun MainShell(
    onLogout: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenWatchlist: (WatchlistSegment) -> Unit,
    onOpenReadLater: () -> Unit,
    onOpenPixivision: () -> Unit,
    onOpenUserRanking: () -> Unit,
    onOpenAiRanking: () -> Unit,
    onOpenEraRanking: () -> Unit,
    onOpenWallpaperRanking: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenBlocked: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenMangaRanking: () -> Unit,
    onOpenIllustRanking: () -> Unit,
    onOpenNovelRanking: () -> Unit,
    onOpenFanbox: () -> Unit,
    onOpenFanboxWeb: (String, String) -> Unit,
    onOpenComic: () -> Unit,
    onOpenViewer: (Long, Int) -> Unit,
    onOpenReader: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
    onExitSearchSession: () -> Unit,
    initialSearch: String? = null,
    initialSearchTagType: TagType? = null,
) {
    val tabs = rememberTabs()
    val hasInitialSearch = initialSearch?.isNotBlank() == true
    var activeTab by rememberSaveable { mutableStateOf("home_tab") }
    var discoverOpen by rememberSaveable { mutableStateOf(hasInitialSearch) }
    // 5 个主 Tab 按首次访问懒加载后常驻保活，切 Tab 零销毁零重建（旋转后自动补入当前 activeTab）
    val visitedTabs = remember {
        mutableStateListOf<String>().apply {
            if (!discoverOpen) add(activeTab)
        }
    }
    LaunchedEffect(activeTab, discoverOpen) {
        if (!discoverOpen && activeTab !in visitedTabs) {
            visitedTabs.add(activeTab)
        }
    }
    var activeSearch by remember(initialSearch, initialSearchTagType) {
        mutableStateOf(
            initialSearch?.takeIf { it.isNotBlank() }?.let { query ->
                SearchLaunch(query, initialSearchTagType ?: TagType.ILLUST)
            },
        )
    }

    // 重复点击当前 Tab 递增计数触发滚回顶部；页面侧仅响应变化，防首次进入误滚
    val tabReselectKeys = remember { mutableStateMapOf<String, Int>() }

    fun navigateToTab(route: String) {
        if (route == "discover_tab") {
            discoverOpen = true
            return
        }
        if (!discoverOpen && activeTab == route) {
            tabReselectKeys[route] = (tabReselectKeys[route] ?: 0) + 1
            return
        }
        if (route !in visitedTabs) {
            visitedTabs.add(route)
        }
        activeTab = route
        discoverOpen = false
        activeSearch = null
    }

    fun launchTagSearch(tag: String, tagType: TagType = TagType.ILLUST) {
        activeSearch = SearchLaunch(tag, tagType)
        discoverOpen = true
    }

    // 搜索层打开时优先收起搜索（深链会话直接离开本 main 实例）；非首页主 Tab 返回先回首页
    BackHandler(enabled = discoverOpen) {
        if (hasInitialSearch) {
            onExitSearchSession()
        } else {
            discoverOpen = false
            activeSearch = null
            if (activeTab !in visitedTabs) visitedTabs.add(activeTab)
        }
    }
    BackHandler(enabled = !discoverOpen && activeTab != "home_tab") {
        navigateToTab("home_tab")
    }

    val motionScheme = MaterialTheme.motionScheme
    val tabOrder = remember(tabs) {
        tabs.mapIndexed { index, item -> item.route to index }.toMap()
    }
    val tabStoreVm: MainShellTabStoreViewModel = viewModel()

    AdaptiveNavScaffold(
        items = tabs,
        selectedRoute = if (discoverOpen) "discover_tab" else activeTab,
        onSelect = { route -> navigateToTab(route) },
        // 发现页隐藏底栏
        showBottomBar = !discoverOpen,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            KeepAliveTabContainer(
                visitedTabs = visitedTabs,
                activeTab = activeTab,
                tabOrder = tabOrder,
                isShellActive = !discoverOpen,
                tabStoreVm = tabStoreVm,
                modifier = Modifier.fillMaxSize(),
            ) { route ->
                when (route) {
                    "home_tab" -> HomeRoute(
                        reselectKey = tabReselectKeys["home_tab"] ?: 0,
                        onOpenSearch = { navigateToTab("discover_tab") },
                        onSearchTag = { tag -> launchTagSearch(tag) },
                        onOpenIllust = onOpenIllust,
                        onOpenUser = onOpenUser,
                        onOpenNotifications = onOpenNotifications,
                        onOpenViewer = onOpenViewer,
                    )

                    "manga_tab" -> MangaRoute(
                        reselectKey = tabReselectKeys["manga_tab"] ?: 0,
                        onSearchTag = { tag -> launchTagSearch(tag) },
                        onOpenIllust = onOpenIllust,
                        onOpenMangaRanking = onOpenMangaRanking,
                        onOpenIllustRanking = onOpenIllustRanking,
                        onOpenWatchlist = { onOpenWatchlist(WatchlistSegment.MANGA) },
                        onOpenUser = onOpenUser,
                        onOpenViewer = onOpenViewer,
                    )

                    "novel_tab" -> NovelRoute(
                        reselectKey = tabReselectKeys["novel_tab"] ?: 0,
                        onOpenNovel = onOpenNovel,
                        onOpenUser = onOpenUser,
                        onOpenNovelRanking = onOpenNovelRanking,
                        onOpenSeries = onOpenSeries,
                        onSearchTag = { tag -> launchTagSearch(tag, tagType = TagType.NOVEL) },
                        onOpenReader = onOpenReader,
                        onOpenCover = onOpenCover,
                    )

                    "follow_tab" -> FollowRoute(
                        reselectKey = tabReselectKeys["follow_tab"] ?: 0,
                        onOpenIllust = onOpenIllust,
                        onOpenNovel = onOpenNovel,
                        onOpenUser = onOpenUser,
                        onOpenSeries = onOpenSeries,
                        onOpenViewer = onOpenViewer,
                        onSearchTag = { tag -> launchTagSearch(tag, tagType = TagType.NOVEL) },
                        // feature 间禁止互依，小说与系列 pane 由 app 层槽位注入
                        novelDetailPane = { selectedId, novelVm, commentVm, onOpenSeries ->
                            NovelDetailPaneSlot(
                                selectedId = selectedId,
                                novelVm = novelVm,
                                commentVm = commentVm,
                                onOpenSeries = onOpenSeries,
                                onOpenReader = onOpenReader,
                                onOpenUser = onOpenUser,
                                onSearchTag = { tag -> launchTagSearch(tag, tagType = TagType.NOVEL) },
                            )
                        },
                        seriesDetailPane = { selectedId, onOpenNovel, onOpenSeries ->
                            NovelSeriesPaneSlot(
                                selectedId = selectedId,
                                onOpenNovel = onOpenNovel,
                                onOpenSeries = onOpenSeries,
                                onOpenUser = onOpenUser,
                                onOpenCover = onOpenCover,
                                onSearchTag = { tag -> launchTagSearch(tag, tagType = TagType.NOVEL) },
                            )
                        },
                    )

                    "me_tab" -> MeRoute(
                        reselectKey = tabReselectKeys["me_tab"] ?: 0,
                        onLogout = onLogout,
                        onOpenHistory = onOpenHistory,
                        onOpenBookmarks = onOpenBookmarks,
                        onOpenWatchlist = { onOpenWatchlist(WatchlistSegment.NOVEL) },
                        onOpenReadLater = onOpenReadLater,
                        onOpenPixivision = onOpenPixivision,
                        onOpenBlocked = onOpenBlocked,
                        onOpenDownloads = onOpenDownloads,
                        onOpenUser = onOpenUser,
                        onOpenFanbox = onOpenFanbox,
                        onOpenFanboxWeb = onOpenFanboxWeb,
                        onOpenComic = onOpenComic,
                    )
                }
            }

            // 发现/搜索页叠加在常驻主壳上方：打开与退出均不销毁底部 5 个 Tab
            AnimatedVisibility(
                visible = discoverOpen,
                enter = slideInVertically(motionScheme.defaultEffectsSpec<IntOffset>()) { it / 24 } +
                    fadeIn(motionScheme.defaultEffectsSpec()),
                exit = slideOutVertically(motionScheme.fastEffectsSpec<IntOffset>()) { it / 24 } +
                    fadeOut(motionScheme.fastEffectsSpec()),
            ) {
                TabScopedHost(
                    route = "discover_tab",
                    isActive = discoverOpen,
                    tabStoreVm = tabStoreVm,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxSize()
                            // 拦截空白处点击避免穿透到下方常驻主 Tab
                            .pointerInput(Unit) {},
                    ) {
                        DiscoverRoute(
                            onOpenIllust = onOpenIllust,
                            onOpenNovel = onOpenNovel,
                            onOpenUser = onOpenUser,
                            onOpenSeries = onOpenSeries,
                            onOpenPixivision = onOpenPixivision,
                            onOpenUserRanking = onOpenUserRanking,
                            onOpenAiRanking = onOpenAiRanking,
                            onOpenEraRanking = onOpenEraRanking,
                            onOpenWallpaperRanking = onOpenWallpaperRanking,
                            initialQuery = activeSearch?.query,
                            initialTagType = activeSearch?.tagType,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 5 个主 Tab 懒加载保活容器：
 * - 通过自定义 Layout 仅测量并放置当前页与正在退场的上一页，隐藏页跳过 measure/place（零绘制、零触控泄漏）；
 * - 切 Tab 过渡仅在绘制阶段读 graphicsLayer，全程零重组、零子树销毁，与底栏滑块同向微移。
 */
@Composable
private fun KeepAliveTabContainer(
    visitedTabs: List<String>,
    activeTab: String,
    tabOrder: Map<String, Int>,
    isShellActive: Boolean,
    tabStoreVm: MainShellTabStoreViewModel,
    modifier: Modifier = Modifier,
    tabContent: @Composable (route: String) -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    var lastActiveTab by remember { mutableStateOf(activeTab) }
    var outgoingTab by remember { mutableStateOf<String?>(null) }
    var slideDirection by remember { mutableIntStateOf(1) }
    // 代数锁：切 Tab 当帧同步推进 transitionGeneration，确保首帧绘制直接读 p=0f，
    // 消除「第 1 帧新页满不透明闪现 → 第 2 帧 LaunchedEffect 拉回 0 再滑入」的抖动。
    var transitionGeneration by remember { mutableIntStateOf(0) }
    var animatingGeneration by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(1f) }

    if (activeTab != lastActiveTab) {
        val from = lastActiveTab
        val fromIdx = tabOrder[from] ?: 0
        val toIdx = tabOrder[activeTab] ?: 0
        slideDirection = if (toIdx >= fromIdx) 1 else -1
        outgoingTab = from
        lastActiveTab = activeTab
        transitionGeneration++
    }

    LaunchedEffect(transitionGeneration) {
        if (transitionGeneration == 0) return@LaunchedEffect
        progress.snapTo(0f)
        animatingGeneration = transitionGeneration
        progress.animateTo(
            targetValue = 1f,
            animationSpec = motionScheme.defaultEffectsSpec(),
        )
        outgoingTab = null
    }

    Layout(
        modifier = modifier,
        content = {
            visitedTabs.forEach { route ->
                key(route) {
                    val isCurrent = route == activeTab
                    TabScopedHost(
                        route = route,
                        isActive = isShellActive && isCurrent,
                        tabStoreVm = tabStoreVm,
                    ) {
                        Box(
                            modifier = Modifier
                                .layoutId(route)
                                .fillMaxSize()
                                .graphicsLayer {
                                    // outgoingTab / progress 仅在绘制期读取，动画起止零触发子树重组
                                    val outgoing = outgoingTab
                                    val p = if (animatingGeneration == transitionGeneration) {
                                        progress.value
                                    } else {
                                        0f
                                    }
                                    when {
                                        isCurrent && outgoing != null -> {
                                            translationX = size.width * 0.04f * slideDirection * (1f - p)
                                            alpha = p.coerceIn(0f, 1f)
                                        }

                                        outgoing == route -> {
                                            translationX = -size.width * 0.03f * slideDirection * p
                                            alpha = (1f - p).coerceIn(0f, 1f)
                                        }

                                        else -> {
                                            translationX = 0f
                                            alpha = 1f
                                        }
                                    }
                                },
                        ) {
                            tabContent(route)
                        }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val childConstraints = Constraints.fixed(width, height)
        val activePlaceable = measurables
            .firstOrNull { it.layoutId == activeTab }
            ?.measure(childConstraints)
        val outgoingRoute = outgoingTab?.takeIf { it != activeTab }
        val outgoingPlaceable = outgoingRoute?.let { out ->
            measurables.firstOrNull { it.layoutId == out }?.measure(childConstraints)
        }
        layout(width, height) {
            outgoingPlaceable?.placeRelative(0, 0, zIndex = 0f)
            activePlaceable?.placeRelative(0, 0, zIndex = 1f)
        }
    }
}

@Composable
private fun TabScopedHost(
    route: String,
    isActive: Boolean,
    tabStoreVm: MainShellTabStoreViewModel,
    content: @Composable () -> Unit,
) {
    val parentVmOwner = checkNotNull(LocalViewModelStoreOwner.current)
    val parentFactoryOwner = parentVmOwner as HasDefaultViewModelProviderFactory
    val parentLifecycle = LocalLifecycleOwner.current.lifecycle
    val owner = remember(route, tabStoreVm, parentFactoryOwner) {
        TabScopeOwner(
            viewModelStore = tabStoreVm.storeFor(route),
            parentFactoryOwner = parentFactoryOwner,
        )
    }

    fun syncState() {
        val parentState = parentLifecycle.currentState
        if (parentState == Lifecycle.State.DESTROYED) {
            owner.moveTo(Lifecycle.State.DESTROYED)
            return
        }
        val target = if (isActive) {
            parentState
        } else {
            minOf(parentState, Lifecycle.State.CREATED)
        }
        if (target.isAtLeast(Lifecycle.State.CREATED)) {
            owner.moveTo(target)
        }
    }

    DisposableEffect(owner, parentLifecycle, isActive) {
        syncState()
        val observer = LifecycleEventObserver { _, _ -> syncState() }
        parentLifecycle.addObserver(observer)
        onDispose {
            parentLifecycle.removeObserver(observer)
        }
    }
    DisposableEffect(owner) {
        onDispose {
            owner.moveTo(Lifecycle.State.DESTROYED)
        }
    }

    CompositionLocalProvider(
        LocalViewModelStoreOwner provides owner,
        LocalLifecycleOwner provides owner,
        LocalSavedStateRegistryOwner provides owner,
        content = content,
    )
}
