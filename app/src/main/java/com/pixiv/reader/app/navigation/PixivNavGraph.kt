package com.pixiv.reader.app.navigation

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.pixiv.reader.app.download.retryDownload
import com.pixiv.reader.core.novel.store.LocalReaderStore
import com.pixiv.reader.core.ui.component.layout.FullscreenImageRoute
import com.pixiv.reader.feature.auth.AuthRoute
import com.pixiv.reader.feature.bookmark.BookmarkRoute
import com.pixiv.reader.feature.comments.ui.CommentListRoute
import com.pixiv.reader.feature.illust.IllustDetailRoute
import com.pixiv.reader.feature.manga.IllustRankingRoute
import com.pixiv.reader.feature.manga.MangaRankingRoute
import com.pixiv.reader.feature.notification.NotificationGroupRoute
import com.pixiv.reader.feature.notification.NotificationRoute
import com.pixiv.reader.feature.novel.ui.NovelDetailPane
import com.pixiv.reader.feature.novel.ui.NovelDetailRoute
import com.pixiv.reader.feature.novel.ui.NovelRankingRoute
import com.pixiv.reader.feature.novel.ui.NovelSeriesPane
import com.pixiv.reader.feature.novel.ui.NovelSeriesRoute
import com.pixiv.reader.feature.novel.state.NovelSeriesViewModel
import com.pixiv.reader.feature.onboarding.ui.OnboardingRoute
import com.pixiv.reader.feature.reader.ui.ReaderRoute
import com.pixiv.reader.feature.user.ui.BlockedRoute
import com.pixiv.reader.feature.user.ui.DownloadsRoute
import com.pixiv.reader.feature.user.ui.HistoryRoute
import com.pixiv.reader.feature.user.ui.UserBookmarksRoute
import com.pixiv.reader.feature.user.ui.UserFollowingRoute
import com.pixiv.reader.feature.user.ui.UserRoute
import com.pixiv.reader.feature.viewer.ViewerRoute
import com.pixiv.reader.feature.watchlist.WatchlistRoute

/** 登录页（未登录时的导航起点）。 */
const val ROUTE_AUTH = "auth"

/** 首次启动引导页（onboardingComplete=false 时的导航起点；完成后跳 auth/main）。 */
const val ROUTE_ONBOARDING = "onboarding"

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

/** 小说阅读器（在线 / 离线缓存共用同一路由）。toEnd=true 时定位到文档末尾（系列上一章尾页进入）。 */
const val ROUTE_READER = "reader/{novelId}?toEnd={toEnd}"

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

/** 通知中心（收藏 / 关注 / 评论等消息流）。 */
const val ROUTE_NOTIFICATION = "notifications"

/** 通知分组子列表（view-more 拉取的组内通知），title 为可选组名。 */
const val ROUTE_NOTIFICATION_GROUP = "notification_group/{groupId}?title={title}"

/** 屏蔽名单（屏蔽标签按卡片分组展示）。 */
const val ROUTE_BLOCKED = "blocked"

/** 下载管理（图片 / 小说 / 本地文件三类，支持删除）。 */
const val ROUTE_DOWNLOADS = "downloads"

/** 漫画排行榜（全屏页，从漫画 Tab 顶部入口进入）。 */
const val ROUTE_MANGA_RANKING = "manga_ranking"

/** 插画排行榜（全屏页，从漫画 Tab 插画类型顶部入口进入）。 */
const val ROUTE_ILLUST_RANKING = "illust_ranking"

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
 * 未完成引导 → onboarding（首次启动引导页）；未登录 → auth（登录页）；已登录 → main（底部导航主壳）。
 * illust/viewer 为全屏路由（隐藏底部导航）；其余为全屏页路由。
 * 深链 scheme: `pixiv://`（illust/novel/reader/user 支持，OAuth 回调走 `pixiv://account/login`）。
 * 导航约定：顶层路由回调统一在此接线；内层 Tab 无法直达顶层路由，必须经 MainShell 回调上抛。
 *
 * @param onboardingComplete 首次引导是否已完成（启动时同步读取，完成后经 [onCompleteOnboarding] 写回）
 * @param onCompleteOnboarding 引导完成/跳过回调：调用方写 DataStore 标记（本函数负责随后跳转）
 */
@Composable
fun PixivNavGraph(
    isLoggedIn: Boolean,
    onboardingComplete: Boolean,
    onCompleteOnboarding: () -> Unit,
    onLogout: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    // 嵌套 NavHost（外层 main 含 MainShell 内层 Tab）：内层 back handler 到 start（home_tab）后让位外层，
    // 外层若再 pop startDestination 会栈空白屏。此处：外层当前为 startDestination 时系统返回直接退出 app。
    // 注意：不能在 NavHost 组合前访问 navController.graph（会 IllegalStateException），故用 route pattern 判断。
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val startRoutePattern =
        if (!onboardingComplete) ROUTE_ONBOARDING
        else if (isLoggedIn) "main?search={search}"
        else ROUTE_AUTH
    val activity = LocalContext.current as? Activity
    BackHandler(enabled = currentRoute == startRoutePattern) {
        activity?.finish()
    }

    NavHost(
        navController = navController,
        startDestination = when {
            !onboardingComplete -> ROUTE_ONBOARDING
            isLoggedIn -> ROUTE_MAIN
            else -> ROUTE_AUTH
        },
    ) {
        composable(ROUTE_ONBOARDING) {
            OnboardingRoute(
                onFinished = {
                    // 先写完成标记（失败不影响本次跳转，下次启动兜底重新引导）
                    onCompleteOnboarding()
                    navController.navigate(if (isLoggedIn) ROUTE_MAIN else ROUTE_AUTH) {
                        // 清空引导页栈，返回键不回到引导页
                        popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
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
                navArgument("search") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                },
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
                onOpenNotifications = {
                    navController.navigate(ROUTE_NOTIFICATION)
                },
                onOpenBlocked = {
                    navController.navigate(ROUTE_BLOCKED)
                },
                onOpenDownloads = {
                    navController.navigate(ROUTE_DOWNLOADS)
                },
                onOpenMangaRanking = {
                    navController.navigate(ROUTE_MANGA_RANKING)
                },
                onOpenIllustRanking = {
                    navController.navigate(ROUTE_ILLUST_RANKING)
                },
                onOpenNovelRanking = {
                    navController.navigate(ROUTE_NOVEL_RANKING)
                },
                onOpenViewer = { id, page ->
                    // 全屏查看器：Tab 内详情 pane 图片点击（定位到指定页）
                    navController.navigate("viewer/$id?page=$page")
                },
                onOpenReader = { id ->
                    // 小说阅读器：小说 pane「开始阅读」入口
                    navController.navigate("reader/$id")
                },
                onOpenCover = { url ->
                    // 封面/头像全屏大图（系列 pane 封面点击）
                    navController.navigate("image_preview?url=${Uri.encode(url)}")
                },
                initialSearch = initialSearch,
            )
        }
        // 插画详情：全屏路由（隐藏底部导航），支持 pixiv://illust/{id} 深链
        composable(
            route = ROUTE_ILLUST,
            arguments = listOf(navArgument("illustId") { type = NavType.LongType }),
            deepLinks = listOf(navDeepLink { uriPattern = "pixiv://illust/{illustId}" }),
        ) { _ ->
            IllustDetailRoute(
                onBack = { navController.safeBack() },
                onOpenIllust = { id ->
                    // 相关作品等：跳对应作品详情（叠栈，返回回到当前详情）
                    navController.navigate("illust/$id")
                },
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
                onSearchTag = { tag ->
                    // 标签搜索：切到发现页并搜索（main?search= 顶层通道）
                    navController.navigate("main?search=${Uri.encode(tag)}")
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
                navArgument("url") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                },
                navArgument("title") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                },
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
                onBack = { navController.safeBack() },
                onOpenUser = { userId ->
                    navController.navigate("user/$userId")
                },
            )
        }
        // 小说阅读器：支持 pixiv://reader/{id} 深链；系列目录点击其他分册直接换读；
        // toEnd=true（系列上一章尾页进入）时阅读器定位到文档末尾。
        // 显式方向过渡（replace 章节切换时旧页不播退出动画，仅新页 enter 动画）：
        // enter = 从右滑入 + 淡入（阅读方向），pop 返回 = 向右滑出
        composable(
            route = ROUTE_READER,
            arguments = listOf(
                navArgument("novelId") { type = NavType.LongType },
                navArgument("toEnd") { type = NavType.BoolType; defaultValue = false },
            ),
            deepLinks = listOf(navDeepLink { uriPattern = "pixiv://reader/{novelId}" }),
            enterTransition = {
                slideInHorizontally(tween(220)) { it } + fadeIn(tween(220))
            },
            exitTransition = {
                slideOutHorizontally(tween(220)) { -it } + fadeOut(tween(220))
            },
            popEnterTransition = {
                slideInHorizontally(tween(220)) { -it } + fadeIn(tween(220))
            },
            popExitTransition = {
                slideOutHorizontally(tween(220)) { it } + fadeOut(tween(220))
            },
        ) { backStackEntry ->
            val novelId = backStackEntry.arguments?.getLong("novelId") ?: 0L
            val toEnd = backStackEntry.arguments?.getBoolean("toEnd") ?: false
            ReaderRoute(
                novelId = novelId,
                toEnd = toEnd,
                onBack = { navController.safeBack() },
                onOpenNovel = { id ->
                    // 系列章节切换：replace 当前阅读器（不压栈），返回键直接退出阅读器而非退回上一章
                    navController.navigate("reader/$id") {
                        popUpTo(ROUTE_READER) { inclusive = true }
                    }
                },
                onOpenNovelToEnd = { id ->
                    // 首页向前翻页：replace 打开系列上一本并定位到尾页
                    navController.navigate("reader/$id?toEnd=true") {
                        popUpTo(ROUTE_READER) { inclusive = true }
                    }
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
                onBack = { navController.safeBack() },
                onOpenIllust = { illustId ->
                    navController.navigate("illust/$illustId")
                },
                onOpenNovel = { novelId ->
                    navController.navigate("novel/$novelId")
                },
                onOpenViewer = { id, page ->
                    // 全屏查看器：pane 内图片点击（定位到指定页）
                    navController.navigate("viewer/$id?page=$page")
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
                // 平板 pane：小说卡点击 → 注入 feature:novel 的小说详情 pane
                // （feature 间禁止依赖，用户页经此槽位复用，与关注页 MainShell 注入同款）
                novelDetailPane = { selectedId, novelVm, commentVm, onOpenSeries ->
                    NovelDetailPane(
                        selectedId = selectedId,
                        placeholder = stringResource(
                            com.pixiv.reader.feature.novel.R.string.novel_ranking_preview_placeholder
                        ),
                        onOpenReader = { novelId ->
                            navController.navigate("reader/$novelId")
                        },
                        onOpenUser = { target ->
                            navController.navigate("user/$target")
                        },
                        // 「查看完整系列」由宿主分流（pane 内切换系列 pane / 全屏）
                        onOpenSeries = onOpenSeries,
                        commentVm = commentVm,
                        viewModel = novelVm,
                    )
                },
                // 平板 pane：系列卡点击 → 注入 feature:novel 的小说系列 pane
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
                        onOpenUser = { target ->
                            navController.navigate("user/$target")
                        },
                        onOpenCover = { url ->
                            navController.navigate("image_preview?url=${Uri.encode(url)}")
                        },
                        onSearchTag = { tag ->
                            navController.navigate("main?search=${Uri.encode(tag)}") {
                                popUpTo(ROUTE_MAIN) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        viewModel = seriesVm,
                    )
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
                navArgument("type") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                },
                navArgument("tag") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                },
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
        // 通知中心
        composable(ROUTE_NOTIFICATION) {
            NotificationRoute(
                onBack = { navController.safeBack() },
                onOpenUser = { userId ->
                    navController.navigate("user/$userId")
                },
                onOpenIllust = { illustId ->
                    navController.navigate("illust/$illustId")
                },
                onOpenNovel = { novelId ->
                    navController.navigate("novel/$novelId")
                },
                onOpenGroup = { groupId, title ->
                    navController.navigate(
                        "notification_group/$groupId?title=${Uri.encode(title.orEmpty())}"
                    )
                },
            )
        }
        // 通知分组子列表
        composable(
            route = ROUTE_NOTIFICATION_GROUP,
            arguments = listOf(
                navArgument("groupId") { type = NavType.LongType },
                navArgument("title") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                },
            ),
        ) {
            NotificationGroupRoute(
                onBack = { navController.safeBack() },
                onOpenUser = { userId ->
                    navController.navigate("user/$userId")
                },
                onOpenIllust = { illustId ->
                    navController.navigate("illust/$illustId")
                },
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
        // 下载管理：插画/小说；本地文件走 local_reader 路由
        composable(ROUTE_DOWNLOADS) {
            val context = LocalContext.current
            DownloadsRoute(
                onBack = { navController.safeBack() },
                onOpenIllust = { illustId ->
                    navController.navigate("illust/$illustId")
                },
                onOpenNovel = { novelId ->
                    navController.navigate("novel/$novelId")
                },
                onOpenLocalReader = { novelId ->
                    navController.navigate("local_reader/$novelId")
                },
                onRetry = { entry -> retryDownload(context, entry) },
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
        // 漫画排行榜（全屏页）：点击排名行打开插画/漫画详情，点作者行打开用户主页
        composable(ROUTE_MANGA_RANKING) {
            MangaRankingRoute(
                onBack = { navController.safeBack() },
                onOpenIllust = { illustId ->
                    navController.navigate("illust/$illustId")
                },
                onOpenUser = { userId ->
                    navController.navigate("user/$userId")
                },
                onOpenViewer = { illustId, page ->
                    navController.navigate("viewer/$illustId?page=$page")
                },
            )
        }
        // 插画排行榜（全屏页）：点击排名行打开插画/漫画详情，点作者行打开用户主页
        composable(ROUTE_ILLUST_RANKING) {
            IllustRankingRoute(
                onBack = { navController.safeBack() },
                onOpenIllust = { illustId ->
                    navController.navigate("illust/$illustId")
                },
                onOpenUser = { userId ->
                    navController.navigate("user/$userId")
                },
                onOpenViewer = { illustId, page ->
                    navController.navigate("viewer/$illustId?page=$page")
                },
            )
        }
        // 小说排行榜（全屏页）：条目用 NovelCard（整卡→详情、作者→主页、收藏、标签）
        composable(ROUTE_NOVEL_RANKING) {
            NovelRankingRoute(
                onBack = { navController.safeBack() },
                onOpenNovel = { novelId ->
                    navController.navigate("novel/$novelId")
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
                onOpenReader = { novelId ->
                    navController.navigate("reader/$novelId")
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
