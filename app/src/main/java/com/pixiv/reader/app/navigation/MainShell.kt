package com.pixiv.reader.app.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.pixiv.reader.app.R
import com.pixiv.reader.core.ui.component.layout.AdaptiveNavItem
import com.pixiv.reader.core.ui.component.layout.AdaptiveNavScaffold
import com.pixiv.reader.feature.discover.ui.DiscoverRoute
import com.pixiv.reader.feature.follow.ui.FollowRoute
import com.pixiv.reader.feature.home.HomeRoute
import com.pixiv.reader.feature.manga.MangaRoute
import com.pixiv.reader.feature.novel.ui.NovelDetailPane
import com.pixiv.reader.feature.novel.ui.NovelRoute
import com.pixiv.reader.feature.novel.ui.NovelSeriesPane
import com.pixiv.reader.feature.novel.state.NovelSeriesViewModel
import com.pixiv.reader.feature.user.ui.MeRoute


/** 首页搜索框 ↔ 发现页搜索栏的共享元素 key（hero container transform）。 */
private const val SHARED_SEARCH_BAR_KEY = "search_bar"

/** 底部导航五项：首页 / 作品 / 小说 / 关注 / 我的（发现页由首页搜索框等入口进入，不在底栏）。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun rememberTabs(): List<AdaptiveNavItem> = listOf(
    AdaptiveNavItem("home_tab", stringResource(R.string.main_tab_home), Icons.Filled.Home),
    AdaptiveNavItem("manga_tab", stringResource(R.string.main_tab_manga), Icons.Filled.Collections),
    AdaptiveNavItem(
        "novel_tab",
        stringResource(R.string.main_tab_novel),
        Icons.AutoMirrored.Filled.MenuBook
    ),
    AdaptiveNavItem("follow_tab", stringResource(R.string.main_tab_follow), Icons.Filled.Favorite),
    AdaptiveNavItem("me_tab", stringResource(R.string.main_tab_me), Icons.Filled.Person),
)

/**
 * 登录后的主壳。
 * 自适应导航：手机 = 底部 NavigationBar；平板 = 左侧 NavigationRail。
 *
 * 内层 Tab 的跨 Tab 搜索：NovelRoute 标签点击 → pendingSearch 缓存关键词 →
 * 切到 discover_tab 后 DiscoverRoute 以 initialQuery 消费（一次性）。
 * 顶层路由 `main?search={search}` 进入时同样走 pendingSearch 通道。
 *
 * @param onOpenIllust 打开作品详情（由外层导航处理，底部导航自动隐藏）
 */
@Composable
fun MainShell(
    onLogout: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenWatchlist: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenBlocked: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenMangaRanking: () -> Unit,
    onOpenIllustRanking: () -> Unit,
    onOpenNovelRanking: () -> Unit,
    /** 打开全屏查看器（pane 内图片点击；参数为作品 id + 页码） */
    onOpenViewer: (Long, Int) -> Unit,
    /** 打开小说阅读器（小说 pane「开始阅读」入口） */
    onOpenReader: (Long) -> Unit,
    /** 打开封面/头像全屏大图（系列 pane 封面点击） */
    onOpenCover: (String) -> Unit,
    initialSearch: String? = null,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabs = rememberTabs()
    // 待搜索关键词（小说 Tab / 顶层路由标签点击 → 切到发现页搜索）
    var pendingSearch by remember { mutableStateOf<String?>(null) }

    // 统一 Tab 导航：清栈到 start（保存被弹出的状态）+ launchSingleTop + restoreState。
    // 所有「跳到某 Tab」的导航都必须走这里——否则（如 launchSingleTop 不带 popUpTo）会把目标
    // 压栈到当前 Tab 之上形成非标准栈，再切回原 Tab 时同一 navigate 内 popUpTo(saveState) 弹出
    // 并立即 restoreState 恢复同 destination，触发 Navigation 状态恢复异常（点原 Tab 不跳转）。
    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // 顶层路由带 search 参数进入：切到发现页并搜索
    LaunchedEffect(initialSearch) {
        if (!initialSearch.isNullOrBlank()) {
            pendingSearch = initialSearch
            navigateToTab("discover_tab")
        }
    }

    // hero 过渡：首页搜索框 → 发现页搜索栏共享元素（sharedBounds container transform）。
    // 旧注释记录 Compose 1.7.x 冷启动首帧可能抛 SharedBoundsNode「Uninitialized LayoutCoordinates」
    // 闪退而弃用；依赖升级（BOM 2026.08）后已修复，重新启用
    SharedTransitionLayout {
        AdaptiveNavScaffold(
            items = tabs,
            selectedRoute = currentRoute,
            onSelect = { route -> navigateToTab(route) },
            // 发现页全屏化：隐藏底栏（由首页搜索框进入，返回走系统返回键）
            showBottomBar = currentRoute != "discover_tab",
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "home_tab",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                // 首页：退出淡出（hero 元素由 sharedBounds 接管，浮于容器过渡之上）
                composable(
                    route = "home_tab",
                    exitTransition = { fadeOut(animationSpec = tween(160)) },
                ) {
                    HomeRoute(
                        onOpenSearch = {
                            navigateToTab("discover_tab")
                        },
                        onSearchTag = { tag ->
                            pendingSearch = tag
                            navigateToTab("discover_tab")
                        },
                        onOpenIllust = onOpenIllust,
                        onOpenUser = onOpenUser,
                        onOpenNotifications = onOpenNotifications,
                        onOpenViewer = onOpenViewer,
                        // hero：首页搜索框共享元素修饰（与发现页搜索栏同 key）
                        modifier = with(this@SharedTransitionLayout) {
                            Modifier.sharedBounds(
                                rememberSharedContentState(key = SHARED_SEARCH_BAR_KEY),
                                animatedVisibilityScope = this@composable,
                            )
                        },
                    )
                }
                composable("follow_tab") {
                    FollowRoute(
                        onOpenIllust = onOpenIllust,
                        onOpenNovel = onOpenNovel,
                        onOpenUser = onOpenUser,
                        onOpenSeries = onOpenSeries,
                        onOpenViewer = onOpenViewer,
                        // 平板 pane：小说卡点击 → 注入 feature:novel 的小说详情 pane
                        // （feature 间禁止依赖，关注页经此槽位复用；作品 pane 在 core:ui，关注页直用）
                        novelDetailPane = { selectedId, novelVm, commentVm, onOpenSeries ->
                            NovelDetailPane(
                                selectedId = selectedId,
                                placeholder = stringResource(
                                    com.pixiv.reader.feature.novel.R.string.novel_ranking_preview_placeholder
                                ),
                                onOpenReader = onOpenReader,
                                onOpenUser = onOpenUser,
                                // 「查看完整系列」由宿主分流（pane 内切换系列 pane / 全屏）
                                onOpenSeries = onOpenSeries,
                                commentVm = commentVm,
                                viewModel = novelVm,
                            )
                        },
                        // 平板 pane：系列卡（小说卡系列标题）点击 → 注入 feature:novel 的小说系列 pane
                        //（槽位签名不暴露 feature:novel 类型，VM 由本槽位内 hiltViewModel 创建）
                        seriesDetailPane = { selectedId, onOpenNovel, onOpenSeries ->
                            val seriesVm: NovelSeriesViewModel = hiltViewModel()
                            NovelSeriesPane(
                                selectedId = selectedId,
                                placeholder = stringResource(
                                    com.pixiv.reader.feature.novel.R.string.novel_series_pane_placeholder
                                ),
                                onOpenNovel = onOpenNovel,
                                onOpenSeries = onOpenSeries,
                                onOpenUser = onOpenUser,
                                onOpenCover = onOpenCover,
                                onSearchTag = { tag ->
                                    pendingSearch = tag
                                    navigateToTab("discover_tab")
                                },
                                viewModel = seriesVm,
                            )
                        },
                    )
                }
                // 发现页：跨 Tab 标签 / main?search 深链进入。
                // 容器动画由 hero 共享元素接管（首页搜索框 → 搜索栏 morph），页面本体仅淡入淡出
                composable(
                    route = "discover_tab",
                    enterTransition = { fadeIn(tween(220)) },
                    exitTransition = { fadeOut(tween(180)) },
                    popEnterTransition = { fadeIn(tween(180)) },
                    popExitTransition = { fadeOut(tween(220)) },
                ) {
                    DiscoverRoute(
                        onOpenIllust = onOpenIllust,
                        onOpenNovel = onOpenNovel,
                        onOpenUser = onOpenUser,
                        onOpenSeries = onOpenSeries,
                        initialQuery = pendingSearch?.also { pendingSearch = null },
                        // hero：发现页搜索栏共享元素修饰（与首页搜索框同 key）
                        modifier = with(this@SharedTransitionLayout) {
                            Modifier.sharedBounds(
                                rememberSharedContentState(key = SHARED_SEARCH_BAR_KEY),
                                animatedVisibilityScope = this@composable,
                            )
                        },
                    )
                }
                composable("manga_tab") {
                    MangaRoute(
                        onOpenIllust = onOpenIllust,
                        onOpenMangaRanking = onOpenMangaRanking,
                        onOpenIllustRanking = onOpenIllustRanking,
                        onOpenUser = onOpenUser,
                        onOpenViewer = onOpenViewer,
                    )
                }
                composable("novel_tab") {
                    NovelRoute(
                        onOpenNovel = onOpenNovel,
                        onOpenUser = onOpenUser,
                        onOpenNovelRanking = onOpenNovelRanking,
                        onOpenSeries = onOpenSeries,
                        onSearchTag = { tag ->
                            pendingSearch = tag
                            navigateToTab("discover_tab")
                        },
                        onOpenReader = onOpenReader,
                        onOpenCover = onOpenCover,
                    )
                }
                composable("me_tab") {
                    MeRoute(
                        onLogout = onLogout,
                        onOpenHistory = onOpenHistory,
                        onOpenBookmarks = onOpenBookmarks,
                        onOpenWatchlist = onOpenWatchlist,
                        onOpenBlocked = onOpenBlocked,
                        onOpenDownloads = onOpenDownloads,
                        onOpenUser = onOpenUser,
                    )
                }
            }
        }
    }
}
