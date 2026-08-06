package com.pixiv.reader.app.navigation

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.pixiv.reader.core.novel.LocalReaderStore
import com.pixiv.reader.core.ui.component.FullscreenImageRoute
import com.pixiv.reader.feature.auth.AuthRoute
import com.pixiv.reader.feature.bookmark.BookmarkRoute
import com.pixiv.reader.feature.comments.CommentListRoute
import com.pixiv.reader.feature.illust.IllustDetailRoute
import com.pixiv.reader.feature.manga.MangaRankingRoute
import com.pixiv.reader.feature.novel.NovelDetailRoute
import com.pixiv.reader.feature.novel.NovelRankingRoute
import com.pixiv.reader.feature.novel.NovelSeriesRoute
import com.pixiv.reader.feature.reader.ReaderRoute
import com.pixiv.reader.feature.user.BlockedRoute
import com.pixiv.reader.feature.user.DownloadsRoute
import com.pixiv.reader.feature.user.HistoryRoute
import com.pixiv.reader.feature.user.TagsRoute
import com.pixiv.reader.feature.user.UserBookmarksRoute
import com.pixiv.reader.feature.user.UserFollowingRoute
import com.pixiv.reader.feature.user.UserRoute
import com.pixiv.reader.feature.viewer.ViewerRoute
import com.pixiv.reader.feature.watchlist.WatchlistRoute

/** 登录页（未登录时的导航起点）。 */
const val ROUTE_AUTH = "auth"
/**
 * 主壳（底部导航五 Tab）。
 * 可通过 `main?search={search}` 携带搜索词跨 Tab 直达发现页搜索。
 */
const val ROUTE_MAIN = "main"
/** 插画详情（全屏路由，隐藏底部导航）。 */
const val ROUTE_ILLUST = "illust/{illustId}"
/** 全屏查看器（多图翻页），可选 page 参数指定起始页。 */
const val ROUTE_VIEWER = "viewer/{illustId}?page={page}"
/** 小说详情（沉浸式 banner + 系列目录 + 操作）。评论走通用页 ROUTE_COMMENTS。 */
const val ROUTE_NOVEL = "novel/{novelId}"
/** 通用评论列表页（novel / illust 共用）：comments/{type}/{targetId}，type ∈ novel|illust。 */
const val ROUTE_COMMENTS = "comments/{type}/{targetId}"
/** 小说阅读器（在线 / 离线缓存共用同一路由）。 */
const val ROUTE_READER = "reader/{novelId}"
/** 用户主页（插画 / 小说 / 收藏 Tab）。 */
const val ROUTE_USER = "user/{userId}"
/** 用户公开收藏（该用户公开收藏的插画，从用户主页统计格进入）。 */
const val ROUTE_USER_BOOKMARKS = "user_bookmarks/{userId}"
/** 用户关注列表（该用户关注的作者，从用户主页统计格进入）。 */
const val ROUTE_USER_FOLLOWING = "user_following/{userId}"
/** 小说系列详情（系列信息 + 分册列表）。 */
const val ROUTE_NOVEL_SERIES = "novel_series/{seriesId}"
/** 浏览历史（三类：插画 / 小说 / 作者）。 */
const val ROUTE_HISTORY = "history"
/** 收藏列表（插画 / 小说），可带 type + tag 过滤。 */
const val ROUTE_BOOKMARKS = "bookmarks"
/** 追更小说列表。 */
const val ROUTE_WATCHLIST = "watchlist"
/** 屏蔽名单（屏蔽标签按卡片分组展示）。 */
const val ROUTE_BLOCKED = "blocked"
/** 下载管理（图片 / 小说 / 本地文件三类，支持删除）。 */
const val ROUTE_DOWNLOADS = "downloads"
/** 收藏标签管理（点击标签跳收藏列表）。 */
const val ROUTE_TAGS = "tags"
/** 漫画排行榜（全屏页，从漫画 Tab 顶部入口进入）。 */
const val ROUTE_MANGA_RANKING = "manga_ranking"
/** 小说排行榜（全屏页，从小说 Tab 推荐页顶部入口进入）。 */
const val ROUTE_NOVEL_RANKING = "novel_ranking"
/** 全屏图片查看（URL 直入，如小说封面大图），全屏路由隐藏底部导航。 */
const val ROUTE_IMAGE_PREVIEW = "image_preview?url={url}&title={title}"
/**
 * 本地文件阅读（TXT / EPUB 解析后直接渲染，跳过网络）。
 * novelId 对应 LocalReaderStore 的存储键，正文经 `LocalReaderStore.consume()` 单次取走。
 */
const val ROUTE_LOCAL_READER = "local_reader/{novelId}"

/**
 * 应用根导航。
 * 未登录 → auth（登录页）；已登录 → main（底部导航主壳）。
 * illust/viewer 为全屏路由（隐藏底部导航）；其余为全屏页路由。
 * 深链 scheme: `pixiv://`（illust/novel/reader/user 支持，OAuth 回调走 `pixiv://account/login`）。
 * 导航约定：顶层路由回调统一在此接线；内层 Tab 无法直达顶层路由，必须经 MainShell 回调上抛。
 */
@Composable
fun PixivNavGraph(
    isLoggedIn: Boolean,
    onLogout: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    // 嵌套 NavHost（外层 main 含 MainShell 内层 Tab）：内层 back handler 到 start（home_tab）后让位外层，
    // 外层若再 pop startDestination 会栈空白屏。此处：外层当前为 startDestination 时系统返回直接退出 app。
    // 注意：不能在 NavHost 组合前访问 navController.graph（会 IllegalStateException），故用 route pattern 判断。
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val startRoutePattern = if (isLoggedIn) "main?search={search}" else ROUTE_AUTH
    val activity = LocalContext.current as? Activity
    BackHandler(enabled = currentRoute == startRoutePattern) {
        activity?.finish()
    }

    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) ROUTE_MAIN else ROUTE_AUTH,
    ) {
        composable(ROUTE_AUTH) {
            AuthRoute(
                onLoginSuccess = {
                    // 登录成功后清空登录页栈，避免返回键回到登录页
                    navController.navigate(ROUTE_MAIN) {
                        popUpTo(ROUTE_AUTH) { inclusive = true }
                    }
                },
            )
        }
        // 主壳：携带 search 参数跨 Tab 搜索（发现页初始查询词）
        composable(
            route = "main?search={search}",
            arguments = listOf(
                navArgument("search") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { backStackEntry ->
            val initialSearch = backStackEntry.arguments?.getString("search")
            MainShell(
                onLogout = {
                    onLogout()
                    // 登出：清空主壳栈回到登录页
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
                onOpenCover = { url ->
                    navController.navigate("image_preview?url=${Uri.encode(url)}")
                },
                onOpenUser = { userId ->
                    navController.navigate("user/$userId")
                },
                onOpenSeries = { seriesId ->
                    navController.navigate("novel_series/$seriesId")
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
                onOpenMangaRanking = {
                    navController.navigate(ROUTE_MANGA_RANKING)
                },
                onOpenNovelRanking = {
                    navController.navigate(ROUTE_NOVEL_RANKING)
                },
                initialSearch = initialSearch,
            )
        }
        // 插画详情：全屏路由（隐藏底部导航），支持 pixiv://illust/{id} 深链
        composable(
            route = ROUTE_ILLUST,
            arguments = listOf(navArgument("illustId") { type = NavType.LongType }),
            deepLinks = listOf(navDeepLink { uriPattern = "pixiv://illust/{illustId}" }),
        ) { backStackEntry ->
            val illustId = backStackEntry.arguments?.getLong("illustId") ?: 0L
            IllustDetailRoute(
                onBack = { navController.safeBack() },
                onOpenViewer = { id, page ->
                    // 打开全屏查看器并定位到指定页
                    navController.navigate("viewer/$id?page=$page")
                },
                onOpenUser = { userId ->
                    navController.navigate("user/$userId")
                },
                onOpenComments = { id ->
                    // 评论独立页（通用）：illust 类型
                    navController.navigate("comments/illust/$id")
                },
            )
        }
        // 全屏查看器：page 可选，缺省从第 0 页开始
        composable(
            route = ROUTE_VIEWER,
            arguments = listOf(
                navArgument("illustId") { type = NavType.LongType },
                navArgument("page") { type = NavType.IntType; defaultValue = 0 },
            ),
        ) {
            ViewerRoute(onBack = { navController.safeBack() })
        }
        // 全屏图片查看（URL 直入）：小说封面等单图全屏，title 可选
        composable(
            route = ROUTE_IMAGE_PREVIEW,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("title") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { backStackEntry ->
            FullscreenImageRoute(
                url = backStackEntry.arguments?.getString("url"),
                title = backStackEntry.arguments?.getString("title").orEmpty(),
                onBack = { navController.safeBack() },
            )
        }
        // 小说详情：支持 pixiv://novel/{id} 深链；系列分册点击打开对应详情
        composable(
            route = ROUTE_NOVEL,
            arguments = listOf(navArgument("novelId") { type = NavType.LongType }),
            deepLinks = listOf(navDeepLink { uriPattern = "pixiv://novel/{novelId}" }),
        ) { backStackEntry ->
            val novelId = backStackEntry.arguments?.getLong("novelId") ?: 0L
            NovelDetailRoute(
                novelId = novelId,
                onBack = { navController.safeBack() },
                onOpenNovel = { id ->
                    // 系列目录：点击打开该分册的小说详情
                    navController.navigate("novel/$id")
                },
                onOpenReader = { id ->
                    navController.navigate("reader/$id")
                },
                onOpenUser = { userId ->
                    navController.navigate("user/$userId")
                },
                onOpenSeries = { seriesId ->
                    // 系列目录底部：查看完整系列页
                    navController.navigate("novel_series/$seriesId")
                },
                onOpenComments = { id ->
                    // 评论独立页（通用）：novel 类型
                    navController.navigate("comments/novel/$id")
                },
            )
        }
        // 通用评论列表页：novel / illust 共用，PagedState 分页 + 回复
        composable(
            route = ROUTE_COMMENTS,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("targetId") { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: "novel"
            val targetId = backStackEntry.arguments?.getLong("targetId") ?: 0L
            CommentListRoute(
                type = type,
                targetId = targetId,
                onBack = { navController.safeBack() },
                onOpenUser = { userId ->
                    navController.navigate("user/$userId")
                },
            )
        }
        // 小说阅读器：支持 pixiv://reader/{id} 深链；系列目录点击其他分册直接换读
        composable(
            route = ROUTE_READER,
            arguments = listOf(navArgument("novelId") { type = NavType.LongType }),
            deepLinks = listOf(navDeepLink { uriPattern = "pixiv://reader/{novelId}" }),
        ) { backStackEntry ->
            val novelId = backStackEntry.arguments?.getLong("novelId") ?: 0L
            ReaderRoute(
                novelId = novelId,
                onBack = { navController.safeBack() },
                onOpenNovel = { id ->
                    // 系列目录：点击其他分册直接打开该本阅读器
                    navController.navigate("reader/$id")
                },
            )
        }
        // 用户主页：支持 pixiv://user/{id} 深链；标签点击跳 main 搜索
        composable(
            route = ROUTE_USER,
            arguments = listOf(navArgument("userId") { type = NavType.LongType }),
            deepLinks = listOf(navDeepLink { uriPattern = "pixiv://user/{userId}" }),
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getLong("userId") ?: 0L
            UserRoute(
                userId = userId,
                onBack = { navController.safeBack() },
                onOpenIllust = { illustId ->
                    navController.navigate("illust/$illustId")
                },
                onOpenNovel = { novelId ->
                    navController.navigate("novel/$novelId")
                },
                onOpenCover = { url ->
                    navController.navigate("image_preview?url=${Uri.encode(url)}")
                },
                onOpenUser = { target ->
                    navController.navigate("user/$target")
                },
                onSearchTag = { tag ->
                    navController.navigate("main?search=${Uri.encode(tag)}") {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onOpenSeries = { seriesId ->
                    navController.navigate("novel_series/$seriesId")
                },
                onOpenUserBookmarks = {
                    navController.navigate("user_bookmarks/$userId")
                },
                onOpenUserFollowing = {
                    navController.navigate("user_following/$userId")
                },
            )
        }
        // 用户公开收藏：该用户公开收藏的插画（瀑布流），从用户主页统计格进入
        composable(
            route = ROUTE_USER_BOOKMARKS,
            arguments = listOf(navArgument("userId") { type = NavType.LongType }),
        ) {
            UserBookmarksRoute(
                onBack = { navController.safeBack() },
                onOpenIllust = { illustId ->
                    navController.navigate("illust/$illustId")
                },
                onOpenUser = { target ->
                    navController.navigate("user/$target")
                },
            )
        }
        // 用户关注列表：该用户关注的作者，从用户主页统计格进入
        composable(
            route = ROUTE_USER_FOLLOWING,
            arguments = listOf(navArgument("userId") { type = NavType.LongType }),
        ) {
            UserFollowingRoute(
                onBack = { navController.safeBack() },
                onOpenUser = { target ->
                    navController.navigate("user/$target")
                },
            )
        }
        // 小说系列详情：系列信息 + 分册列表
        composable(
            route = ROUTE_NOVEL_SERIES,
            arguments = listOf(navArgument("seriesId") { type = NavType.LongType }),
        ) {
            NovelSeriesRoute(
                onBack = { navController.safeBack() },
                onOpenNovel = { novelId ->
                    navController.navigate("novel/$novelId")
                },
                onOpenCover = { url ->
                    navController.navigate("image_preview?url=${Uri.encode(url)}")
                },
                onOpenUser = { target ->
                    navController.navigate("user/$target")
                },
                onSearchTag = { tag ->
                    navController.navigate("main?search=${Uri.encode(tag)}") {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onOpenSeries = { seriesId ->
                    navController.navigate("novel_series/$seriesId")
                },
            )
        }
        // 浏览历史：三类（插画/小说/作者），点击对应卡片跳详情
        composable(ROUTE_HISTORY) {
            HistoryRoute(
                onBack = { navController.safeBack() },
                onOpenIllust = { illustId ->
                    navController.navigate("illust/$illustId")
                },
                onOpenNovel = { novelId ->
                    navController.navigate("novel/$novelId")
                },
                onOpenCover = { url ->
                    navController.navigate("image_preview?url=${Uri.encode(url)}")
                },
                onOpenUser = { userId ->
                    navController.navigate("user/$userId")
                },
                onOpenSeries = { seriesId ->
                    navController.navigate("novel_series/$seriesId")
                },
            )
        }
        // 收藏列表：type（插画/小说）+ tag 可选过滤；标签点击跳 main 搜索
        composable(
            route = "bookmarks?type={type}&tag={tag}",
            arguments = listOf(
                navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("tag") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) {
            BookmarkRoute(
                onBack = { navController.safeBack() },
                onOpenIllust = { illustId ->
                    navController.navigate("illust/$illustId")
                },
                onOpenNovel = { novelId ->
                    navController.navigate("novel/$novelId")
                },
                onOpenCover = { url ->
                    navController.navigate("image_preview?url=${Uri.encode(url)}")
                },
                onOpenUser = { target ->
                    navController.navigate("user/$target")
                },
                onOpenSeries = { seriesId ->
                    navController.navigate("novel_series/$seriesId")
                },
                onSearchTag = { tag ->
                    navController.navigate("main?search=${Uri.encode(tag)}") {
                        popUpTo(ROUTE_MAIN) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        // 追更小说列表
        composable(ROUTE_WATCHLIST) {
            WatchlistRoute(
                onBack = { navController.safeBack() },
                onOpenNovel = { novelId ->
                    navController.navigate("novel/$novelId")
                },
            )
        }
        // 屏蔽名单
        composable(ROUTE_BLOCKED) {
            BlockedRoute(
                onBack = { navController.safeBack() },
            )
        }
        // 下载管理：三类（图片/小说/本地文件）；本地文件走 local_reader 路由
        composable(ROUTE_DOWNLOADS) {
            DownloadsRoute(
                onBack = { navController.safeBack() },
                onOpenIllust = { illustId ->
                    navController.navigate("illust/$illustId")
                },
                onOpenNovel = { novelId ->
                    navController.navigate("novel/$novelId")
                },
                onOpenCover = { url ->
                    navController.navigate("image_preview?url=${Uri.encode(url)}")
                },
                onOpenReader = { novelId ->
                    navController.navigate("reader/$novelId")
                },
                onOpenLocalReader = { novelId ->
                    navController.navigate("local_reader/$novelId")
                },
            )
        }
        // 本地文件阅读：正文经 LocalReaderStore.consume() 单次取走（仅一次，避免重复消费）
        composable(
            route = ROUTE_LOCAL_READER,
            arguments = listOf(navArgument("novelId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val novelId = backStackEntry.arguments?.getLong("novelId") ?: 0L
            val local = LocalReaderStore.consume()
            ReaderRoute(
                novelId = novelId,
                onBack = { navController.safeBack() },
                onOpenNovel = { id ->
                    navController.navigate("reader/$id")
                },
                localDocument = local?.first,
                localTitle = local?.second,
            )
        }
        // 收藏标签管理：点击标签带 type+tag 跳收藏列表
        composable(ROUTE_TAGS) {
            TagsRoute(
                onBack = { navController.safeBack() },
                onOpenTag = { type, tag ->
                    navController.navigate(
                        "bookmarks?type=$type&tag=${Uri.encode(tag)}",
                    )
                },
            )
        }
        // 漫画排行榜（全屏页）：点击排名行打开插画/漫画详情
        composable(ROUTE_MANGA_RANKING) {
            MangaRankingRoute(
                onBack = { navController.safeBack() },
                onOpenIllust = { illustId ->
                    navController.navigate("illust/$illustId")
                },
            )
        }
        // 小说排行榜（全屏页）：条目用 NovelCard（封面→全屏大图、作者→主页、收藏、标签）
        composable(ROUTE_NOVEL_RANKING) {
            NovelRankingRoute(
                onBack = { navController.safeBack() },
                onOpenNovel = { novelId ->
                    navController.navigate("novel/$novelId")
                },
                onOpenCover = { url ->
                    navController.navigate("image_preview?url=${Uri.encode(url)}")
                },
                onOpenUser = { userId ->
                    navController.navigate("user/$userId")
                },
                onSearchTag = {
                    // 排行榜页标签跳转暂不接入（顶层路由无法直达 MainShell 内 Tab，后续再处理）
                },
                onOpenSeries = { seriesId ->
                    navController.navigate("novel_series/$seriesId")
                },
            )
        }
    }
}

/**
 * 安全返回：栈只剩 startDestination 时静默忽略。
 *
 * BackHandler 只拦截系统返回键，拦不住页面按钮的 onBack → navigateUp。快速操作场景
 * （如 pop 过渡动画中，上层不可点击区域穿透命中下层仍在退出的返回按钮）会触发第二次
 * navigateUp，此时外层栈已只剩 startDestination（main），若照常 pop 会清空 start 导致
 * 空栈 + 过渡竞态（表现为页面重新进入刷新 / 返回桌面）。故栈底时一律忽略，不 pop 也不退出。
 */
private fun NavHostController.safeBack() {
    if (previousBackStackEntry != null) {
        navigateUp()
    } else {
        Log.d("PixivNavGraph", "safeBack: 栈底忽略（穿透/误触/越界返回），避免空栈")
    }
}
