package com.pixiv.reader.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pixiv.reader.app.R
import com.pixiv.reader.core.common.model.TagType
import com.pixiv.reader.feature.watchlist.WatchlistSegment
import com.pixiv.reader.core.ui.component.layout.AdaptiveNavItem
import com.pixiv.reader.core.ui.component.layout.AdaptiveNavScaffold
import com.pixiv.reader.core.ui.component.detail.heroBoundsModifier
import com.pixiv.reader.core.ui.theme.SettingAccentBlue
import com.pixiv.reader.core.ui.theme.SettingAccentGreen
import com.pixiv.reader.core.ui.theme.SettingAccentPink
import com.pixiv.reader.core.ui.theme.SettingAccentTeal
import com.pixiv.reader.core.ui.theme.SettingAccentViolet
import com.pixiv.reader.core.ui.theme.pixivTabFadeIn
import com.pixiv.reader.core.ui.theme.pixivTabFadeOut
import com.pixiv.reader.feature.discover.ui.DiscoverRoute
import com.pixiv.reader.feature.follow.ui.FollowRoute
import com.pixiv.reader.feature.home.HomeRoute
import com.pixiv.reader.feature.manga.MangaRoute
import com.pixiv.reader.feature.novel.ui.NovelRoute
import com.pixiv.reader.feature.user.ui.MeRoute


private const val SHARED_SEARCH_BAR_KEY = "search_bar"

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
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabs = rememberTabs()
    // 载荷留在壳层直到目标条目接收，initialSearchConsumed 防返回时重复触发
    var pendingSearch by remember { mutableStateOf<SearchLaunch?>(null) }
    var initialSearchConsumed by rememberSaveable(initialSearch, initialSearchTagType) { mutableStateOf(false) }

    // 重复点击当前 Tab 递增计数触发滚回顶部；页面侧仅响应变化，防首次进入误滚
    val tabReselectKeys = remember { mutableStateMapOf<String, Int>() }

    // 必须统一 popUpTo(start, saveState) + singleTop + restoreState，
    // 否则形成非标准栈后切回原 Tab 会触发同 destination 恢复异常导致点击失效
    fun navigateToTab(route: String) {
        if (currentRoute == route) {
            tabReselectKeys[route] = (tabReselectKeys[route] ?: 0) + 1
            return
        }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun launchTagSearch(tag: String, tagType: TagType = TagType.ILLUST) {
        pendingSearch = SearchLaunch(tag, tagType)
        navigateToTab("discover_tab")
    }

    // main?search 深链进入时 discover_tab 为单条目栈，按返回直接离开本 main 实例回到来源页
    BackHandler(enabled = initialSearch != null && currentRoute == "discover_tab") {
        onExitSearchSession()
    }

    // transition lambda 非组合上下文，须在组合期提前捕获 motionScheme
    val motionScheme = MaterialTheme.motionScheme
    AdaptiveNavScaffold(
        items = tabs,
        selectedRoute = currentRoute,
        onSelect = { route -> navigateToTab(route) },
        // 发现页隐藏底栏
        showBottomBar = currentRoute != "discover_tab",
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (initialSearch?.isNotBlank() == true) "discover_tab" else "home_tab",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            // Tab 严禁位移：首页↔发现页搜索框 sharedBounds hero 依赖容器纯 fade
            enterTransition = { pixivTabFadeIn(motionScheme) },
            exitTransition = { pixivTabFadeOut(motionScheme) },
            popEnterTransition = { pixivTabFadeIn(motionScheme) },
            popExitTransition = { pixivTabFadeOut(motionScheme) },
        ) {
            composable(
                route = "home_tab",
                exitTransition = { pixivTabFadeOut(motionScheme) },
            ) {
                HomeRoute(
                    reselectKey = tabReselectKeys["home_tab"] ?: 0,
                    onOpenSearch = {
                        navigateToTab("discover_tab")
                    },
                    onSearchTag = { tag -> launchTagSearch(tag) },
                    onOpenIllust = onOpenIllust,
                    onOpenUser = onOpenUser,
                    onOpenNotifications = onOpenNotifications,
                    onOpenViewer = onOpenViewer,
                    modifier = Modifier.heroBoundsModifier(SHARED_SEARCH_BAR_KEY, this@composable),
                )
            }
            composable("follow_tab") {
                FollowRoute(
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
            }
            composable(
                route = "discover_tab",
                enterTransition = { pixivTabFadeIn(motionScheme) },
                exitTransition = { pixivTabFadeOut(motionScheme) },
                popEnterTransition = { pixivTabFadeIn(motionScheme) },
                popExitTransition = { pixivTabFadeOut(motionScheme) },
            ) { searchEntry ->
                val pending = pendingSearch
                val launch = pending ?: initialSearch?.takeIf { !initialSearchConsumed && it.isNotBlank() }
                    ?.let { SearchLaunch(it, tagType = initialSearchTagType ?: TagType.ILLUST) }
                var deliveredSearch by remember(searchEntry) { mutableStateOf<SearchLaunch?>(null) }
                LaunchedEffect(launch, backStackEntry) {
                    // 仅向当前条目交付一次，清壳层载荷后保留 deliveredSearch 直到条目退出组合
                    if (launch != null && navController.currentBackStackEntry == searchEntry) {
                        deliveredSearch = launch
                        initialSearchConsumed = true
                        if (pendingSearch === pending) pendingSearch = null
                    }
                }
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
                    initialQuery = deliveredSearch?.query,
                    initialTagType = deliveredSearch?.tagType,
                    modifier = Modifier.heroBoundsModifier(SHARED_SEARCH_BAR_KEY, this@composable),
                )
            }
            composable("manga_tab") {
                MangaRoute(
                    reselectKey = tabReselectKeys["manga_tab"] ?: 0,
                    onSearchTag = { tag -> launchTagSearch(tag) },
                    onOpenIllust = onOpenIllust,
                    onOpenMangaRanking = onOpenMangaRanking,
                    onOpenIllustRanking = onOpenIllustRanking,
                    onOpenWatchlist = { onOpenWatchlist(WatchlistSegment.MANGA) },
                    onOpenUser = onOpenUser,
                    onOpenViewer = onOpenViewer,
                )
            }
            composable("novel_tab") {
                NovelRoute(
                    reselectKey = tabReselectKeys["novel_tab"] ?: 0,
                    onOpenNovel = onOpenNovel,
                    onOpenUser = onOpenUser,
                    onOpenNovelRanking = onOpenNovelRanking,
                    onOpenSeries = onOpenSeries,
                    onSearchTag = { tag -> launchTagSearch(tag, tagType = TagType.NOVEL) },
                    onOpenReader = onOpenReader,
                    onOpenCover = onOpenCover,
                )
            }
            composable("me_tab") {
                MeRoute(
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
    }
}
