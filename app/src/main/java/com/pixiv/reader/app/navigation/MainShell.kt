package com.pixiv.reader.app.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pixiv.reader.core.ui.component.AdaptiveNavItem
import com.pixiv.reader.core.ui.component.AdaptiveNavScaffold
import com.pixiv.reader.feature.discover.DiscoverRoute
import com.pixiv.reader.feature.discover.RankingRoute
import com.pixiv.reader.feature.home.HomeRoute
import com.pixiv.reader.feature.novel.NovelRoute
import com.pixiv.reader.feature.user.MeRoute

/** 底部导航五项：首页 / 发现 / 排行 / 小说 / 我的。 */
private val TABS = listOf(
    AdaptiveNavItem("home_tab", "首页", Icons.Filled.Home),
    AdaptiveNavItem("discover_tab", "发现", Icons.Filled.Explore),
    AdaptiveNavItem("ranking_tab", "排行", Icons.Filled.Leaderboard),
    AdaptiveNavItem("novel_tab", "小说", Icons.AutoMirrored.Filled.MenuBook),
    AdaptiveNavItem("me_tab", "我的", Icons.Filled.Person),
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
    onOpenReader: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenWatchlist: () -> Unit,
    onOpenBlocked: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenSettings: () -> Unit,
    initialSearch: String? = null,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // 待搜索关键词（小说 Tab / 顶层路由标签点击 → 切到发现页搜索）
    var pendingSearch by remember { mutableStateOf<String?>(null) }

    // 顶层路由带 search 参数进入：切到发现页并搜索
    LaunchedEffect(initialSearch) {
        if (!initialSearch.isNullOrBlank()) {
            pendingSearch = initialSearch
            navController.navigate("discover_tab") { launchSingleTop = true }
        }
    }

    AdaptiveNavScaffold(
        items = TABS,
        selectedRoute = currentRoute,
        onSelect = { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home_tab",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable("home_tab") {
                HomeRoute(
                    onOpenSearch = {
                        navController.navigate("discover_tab") { launchSingleTop = true }
                    },
                    onOpenIllust = onOpenIllust,
                )
            }
            composable("discover_tab") {
                DiscoverRoute(
                    onOpenIllust = onOpenIllust,
                    onOpenNovel = onOpenNovel,
                    onOpenReader = onOpenReader,
                    onOpenUser = onOpenUser,
                    initialQuery = pendingSearch?.also { pendingSearch = null },
                )
            }
            composable("ranking_tab") { RankingRoute(onOpenIllust = onOpenIllust) }
            composable("novel_tab") {
                NovelRoute(
                    onOpenNovel = onOpenNovel,
                    onOpenReader = onOpenReader,
                    onOpenUser = onOpenUser,
                    onSearchTag = { tag ->
                        pendingSearch = tag
                        navController.navigate("discover_tab") { launchSingleTop = true }
                    },
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
                    onOpenTags = onOpenTags,
                    onOpenSettings = onOpenSettings,
                    onOpenUser = onOpenUser,
                )
            }
        }
    }
}
