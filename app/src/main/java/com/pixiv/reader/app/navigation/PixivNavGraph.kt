package com.pixiv.reader.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.pixiv.reader.feature.auth.AuthRoute
import com.pixiv.reader.feature.illust.IllustDetailRoute
import com.pixiv.reader.feature.novel.NovelDetailRoute
import com.pixiv.reader.feature.reader.ReaderRoute
import com.pixiv.reader.feature.viewer.ViewerRoute

const val ROUTE_AUTH = "auth"
const val ROUTE_MAIN = "main"
const val ROUTE_ILLUST = "illust/{illustId}"
const val ROUTE_VIEWER = "viewer/{illustId}?page={page}"
const val ROUTE_NOVEL = "novel/{novelId}"
const val ROUTE_READER = "reader/{novelId}"

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
                onOpenReader = { id ->
                    navController.navigate("reader/$id")
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
                    navController.navigate("novel/$id")
                },
            )
        }
    }
}
