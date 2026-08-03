package com.pixiv.reader.app.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.pixiv.reader.feature.auth.AuthRoute
import com.pixiv.reader.feature.bookmark.BookmarkRoute
import com.pixiv.reader.feature.illust.IllustDetailRoute
import com.pixiv.reader.feature.novel.NovelDetailRoute
import com.pixiv.reader.feature.reader.ReaderRoute
import com.pixiv.reader.feature.settings.SettingsRoute
import com.pixiv.reader.feature.user.BlockedRoute
import com.pixiv.reader.feature.user.DownloadsRoute
import com.pixiv.reader.feature.user.HistoryRoute
import com.pixiv.reader.feature.user.TagsRoute
import com.pixiv.reader.feature.user.UserRoute
import com.pixiv.reader.feature.viewer.ViewerRoute
import com.pixiv.reader.feature.watchlist.WatchlistRoute

const val ROUTE_AUTH = "auth"
const val ROUTE_MAIN = "main"
const val ROUTE_ILLUST = "illust/{illustId}"
const val ROUTE_VIEWER = "viewer/{illustId}?page={page}"
const val ROUTE_NOVEL = "novel/{novelId}"
const val ROUTE_READER = "reader/{novelId}"
const val ROUTE_USER = "user/{userId}"
const val ROUTE_HISTORY = "history"
const val ROUTE_BOOKMARKS = "bookmarks"
const val ROUTE_WATCHLIST = "watchlist"
const val ROUTE_BLOCKED = "blocked"
const val ROUTE_DOWNLOADS = "downloads"
const val ROUTE_TAGS = "tags"
const val ROUTE_SETTINGS = "settings"

/**
 * 应用根导航。
 * 未登录 → auth（登录页）；已登录 → main（底部导航主壳）。
 * illust/viewer 为全屏路由（隐藏底部导航）。
 * OAuth 回调 scheme: pixiv://account/login
 */
@Composable
fun PixivNavGraph(
    isLoggedIn: Boolean,
    onLogout: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) ROUTE_MAIN else ROUTE_AUTH,
    ) {
        composable(ROUTE_AUTH) {
            AuthRoute(
                onLoginSuccess = {
                    navController.navigate(ROUTE_MAIN) {
                        popUpTo(ROUTE_AUTH) { inclusive = true }
                    }
                },
            )
        }
        composable(ROUTE_MAIN) {
            MainShell(
                onLogout = {
                    onLogout()
                    navController.navigate(ROUTE_AUTH) {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                    }
                },
                onOpenIllust = { illustId ->
                    navController.navigate("illust/$illustId")
                },
                onOpenNovel = { novelId ->
                    navController.navigate("novel/$novelId")
                },
                onOpenHistory = {
                    navController.navigate(ROUTE_HISTORY)
                },
                onOpenBookmarks = {
                    navController.navigate(ROUTE_BOOKMARKS)
                },
                onOpenWatchlist = {
                    navController.navigate(ROUTE_WATCHLIST)
                },
                onOpenBlocked = {
                    navController.navigate(ROUTE_BLOCKED)
                },
                onOpenDownloads = {
                    navController.navigate(ROUTE_DOWNLOADS)
                },
                onOpenTags = {
                    navController.navigate(ROUTE_TAGS)
                },
                onOpenSettings = {
                    navController.navigate(ROUTE_SETTINGS)
                },
            )
        }
        composable(
            route = ROUTE_ILLUST,
            arguments = listOf(navArgument("illustId") { type = NavType.LongType }),
            deepLinks = listOf(navDeepLink { uriPattern = "pixiv://illust/{illustId}" }),
        ) { backStackEntry ->
            val illustId = backStackEntry.arguments?.getLong("illustId") ?: 0L
            IllustDetailRoute(
                onBack = { navController.popBackStack() },
                onOpenViewer = { id, page ->
                    navController.navigate("viewer/$id?page=$page")
                },
                onOpenUser = { userId ->
                    navController.navigate("user/$userId")
                },
            )
        }
        composable(
            route = ROUTE_VIEWER,
            arguments = listOf(
                navArgument("illustId") { type = NavType.LongType },
                navArgument("page") { type = NavType.IntType; defaultValue = 0 },
            ),
        ) {
            ViewerRoute(onBack = { navController.popBackStack() })
        }
        composable(
            route = ROUTE_NOVEL,
            arguments = listOf(navArgument("novelId") { type = NavType.LongType }),
            deepLinks = listOf(navDeepLink { uriPattern = "pixiv://novel/{novelId}" }),
        ) { backStackEntry ->
            val novelId = backStackEntry.arguments?.getLong("novelId") ?: 0L
            NovelDetailRoute(
                novelId = novelId,
                onBack = { navController.popBackStack() },
                onOpenNovel = { id ->
                    // 系列分册：点击打开该分册的小说详情
                    navController.navigate("novel/$id")
                },
                onOpenReader = { id ->
                    navController.navigate("reader/$id")
                },
                onOpenUser = { userId ->
                    navController.navigate("user/$userId")
                },
            )
        }
        composable(
            route = ROUTE_READER,
            arguments = listOf(navArgument("novelId") { type = NavType.LongType }),
            deepLinks = listOf(navDeepLink { uriPattern = "pixiv://reader/{novelId}" }),
        ) { backStackEntry ->
            val novelId = backStackEntry.arguments?.getLong("novelId") ?: 0L
            ReaderRoute(
                novelId = novelId,
                onBack = { navController.popBackStack() },
                onOpenNovel = { id ->
                    // 系列目录：点击其他分册直接打开该本阅读器
                    navController.navigate("reader/$id")
                },
            )
        }
        composable(
            route = ROUTE_USER,
            arguments = listOf(navArgument("userId") { type = NavType.LongType }),
            deepLinks = listOf(navDeepLink { uriPattern = "pixiv://user/{userId}" }),
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getLong("userId") ?: 0L
            UserRoute(
                userId = userId,
                onBack = { navController.popBackStack() },
                onOpenIllust = { illustId ->
                    navController.navigate("illust/$illustId")
                },
                onOpenNovel = { novelId ->
                    navController.navigate("novel/$novelId")
                },
            )
        }
        composable(ROUTE_HISTORY) {
            HistoryRoute(
                onBack = { navController.popBackStack() },
                onOpenIllust = { illustId ->
                    navController.navigate("illust/$illustId")
                },
                onOpenNovel = { novelId ->
                    navController.navigate("novel/$novelId")
                },
                onOpenUser = { userId ->
                    navController.navigate("user/$userId")
                },
            )
        }
        composable(
            route = "bookmarks?type={type}&tag={tag}",
            arguments = listOf(
                navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("tag") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) {
            BookmarkRoute(
                onBack = { navController.popBackStack() },
                onOpenIllust = { illustId ->
                    navController.navigate("illust/$illustId")
                },
                onOpenNovel = { novelId ->
                    navController.navigate("novel/$novelId")
                },
            )
        }
        composable(ROUTE_WATCHLIST) {
            WatchlistRoute(
                onBack = { navController.popBackStack() },
                onOpenNovel = { novelId ->
                    navController.navigate("novel/$novelId")
                },
            )
        }
        composable(ROUTE_BLOCKED) {
            BlockedRoute(
                onBack = { navController.popBackStack() },
            )
        }
        composable(ROUTE_DOWNLOADS) {
            DownloadsRoute(
                onBack = { navController.popBackStack() },
            )
        }
        composable(ROUTE_TAGS) {
            TagsRoute(
                onBack = { navController.popBackStack() },
                onOpenTag = { type, tag ->
                    navController.navigate(
                        "bookmarks?type=$type&tag=${Uri.encode(tag)}",
                    )
                },
            )
        }
        composable(ROUTE_SETTINGS) {
            SettingsRoute(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
