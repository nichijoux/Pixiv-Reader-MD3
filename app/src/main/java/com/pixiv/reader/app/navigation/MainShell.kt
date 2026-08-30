package com.pixiv.reader.app.navigation

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
import com.pixiv.reader.app.R
import com.pixiv.reader.core.ui.component.layout.AdaptiveNavItem
import com.pixiv.reader.core.ui.component.layout.AdaptiveNavScaffold
import com.pixiv.reader.feature.discover.ui.DiscoverRoute
import com.pixiv.reader.feature.follow.ui.FollowRoute
import com.pixiv.reader.feature.home.HomeRoute
import com.pixiv.reader.feature.manga.MangaRoute
import com.pixiv.reader.feature.novel.ui.NovelRoute
import com.pixiv.reader.feature.user.ui.MeRoute


/** 底部导航五项：首页 / 作品 / 小说 / 关注 / 我的（发现页由首页搜索框等入口进入，不在底栏）。 */
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

    // 首页搜索框与发现页搜索栏使用普通淡入淡出过渡。
    // 不使用 SharedTransitionLayout/sharedElement：Compose 1.7.x 在登录态冷启动首帧仍可能
    // 抛出 SharedBoundsNode「Uninitialized LayoutCoordinates」，导致应用直接闪退。
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
            // 首页：退出淡出
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
                )
            }
            composable("follow_tab") {
                FollowRoute(
                    onOpenIllust = onOpenIllust,
                    onOpenNovel = onOpenNovel,
                    onOpenUser = onOpenUser,
                    onOpenSeries = onOpenSeries,
                )
            }
            // 发现页：跨 Tab 标签 / main?search 深链进入
            composable(
                route = "discover_tab",
                enterTransition = { fadeIn(animationSpec = tween(220)) },
                exitTransition = { fadeOut(animationSpec = tween(180)) },
            ) {
                DiscoverRoute(
                    onOpenIllust = onOpenIllust,
                    onOpenNovel = onOpenNovel,
                    onOpenUser = onOpenUser,
                    onOpenSeries = onOpenSeries,
                    initialQuery = pendingSearch?.also { pendingSearch = null },
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
