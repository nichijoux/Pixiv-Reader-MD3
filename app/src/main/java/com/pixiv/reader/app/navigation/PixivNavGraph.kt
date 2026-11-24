package com.pixiv.reader.app.navigation

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.compose.runtime.rememberCoroutineScope
import com.pixiv.reader.app.download.retryDownload
import com.pixiv.reader.core.common.model.TagType
import com.pixiv.reader.core.novel.store.LocalReaderStore
import com.pixiv.reader.core.ui.component.detail.LocalNavAnimatedVisibilityScope
import com.pixiv.reader.core.ui.component.layout.FullscreenImageRoute
import com.pixiv.reader.core.ui.theme.pixivHeroNavEnter
import com.pixiv.reader.core.ui.theme.pixivHeroNavExit
import com.pixiv.reader.core.ui.theme.pixivHeroNavPopEnter
import com.pixiv.reader.core.ui.theme.pixivHeroNavPopExit
import com.pixiv.reader.core.ui.theme.pixivNavEnter
import com.pixiv.reader.core.ui.theme.pixivNavExit
import com.pixiv.reader.core.ui.theme.pixivNavPopEnter
import com.pixiv.reader.core.ui.theme.pixivNavPopExit
import com.pixiv.reader.feature.auth.AuthRoute
import com.pixiv.reader.feature.bookmark.BookmarkRoute
import com.pixiv.reader.core.comment.ui.CommentListRoute
import com.pixiv.reader.feature.discover.ui.AiRankingRoute
import com.pixiv.reader.feature.discover.ui.EraRankingRoute
import com.pixiv.reader.feature.discover.ui.PixivisionRoute
import com.pixiv.reader.feature.discover.ui.UserRankingRoute
import com.pixiv.reader.feature.discover.ui.WallpaperRankingRoute
import com.pixiv.reader.feature.illust.IllustDetailRoute
import com.pixiv.reader.feature.fanbox.ui.FanboxHomeRoute
import com.pixiv.reader.feature.fanbox.ui.FanboxPostRoute
import com.pixiv.reader.feature.fanbox.ui.FanboxWebRoute
import com.pixiv.reader.feature.comic.ui.ComicHomeRoute
import com.pixiv.reader.feature.comic.ui.ComicReaderRoute
import com.pixiv.reader.feature.comic.ui.ComicSearchRoute
import com.pixiv.reader.feature.comic.ui.ComicWorkDetailRoute
import com.pixiv.reader.feature.manga.IllustRankingRoute
import com.pixiv.reader.feature.manga.MangaRankingRoute
import com.pixiv.reader.feature.manga.MangaSeriesRoute
import com.pixiv.reader.feature.notification.NotificationGroupRoute
import com.pixiv.reader.feature.notification.NotificationRoute
import com.pixiv.reader.feature.novel.ui.NovelDetailRoute
import com.pixiv.reader.feature.novel.ui.NovelRankingRoute
import com.pixiv.reader.feature.novel.ui.NovelSeriesRoute
import com.pixiv.reader.feature.onboarding.ui.OnboardingRoute
import com.pixiv.reader.feature.reader.ui.ReaderRoute
import com.pixiv.reader.feature.user.ui.BlockedRoute
import com.pixiv.reader.feature.user.ui.DownloadsRoute
import com.pixiv.reader.feature.user.ui.HistoryRoute
import com.pixiv.reader.feature.user.ui.ReadLaterRoute
import com.pixiv.reader.feature.user.ui.UserBookmarksRoute
import com.pixiv.reader.feature.user.ui.UserFollowingRoute
import com.pixiv.reader.feature.user.ui.UserRoute
import com.pixiv.reader.feature.viewer.ViewerRoute
import com.pixiv.reader.feature.watchlist.WatchlistRoute
import com.pixiv.reader.feature.watchlist.WatchlistSegment
import kotlinx.coroutines.launch

const val ROUTE_AUTH = "auth"
const val ROUTE_ONBOARDING = "onboarding"
const val ROUTE_MAIN = "main"

// search 为跨 Tab 搜索词，sn=1 表示小说标签
private const val MAIN_ROUTE_PATTERN = "main?search={search}&sn={sn}"

const val ROUTE_ILLUST = "illust/{illustId}"
const val ROUTE_VIEWER = "viewer/{illustId}?page={page}"
const val ROUTE_NOVEL = "novel/{novelId}"
const val ROUTE_COMMENTS = "comments/{type}/{targetId}"

/** toEnd=true 时定位到文档末尾（系列上一章尾页翻入）。 */
const val ROUTE_READER = "reader/{novelId}?toEnd={toEnd}"

const val ROUTE_USER = "user/{userId}"
const val ROUTE_USER_BOOKMARKS = "user_bookmarks/{userId}"
const val ROUTE_USER_FOLLOWING = "user_following/{userId}"
const val ROUTE_NOVEL_SERIES = "novel_series/{seriesId}"
const val ROUTE_MANGA_SERIES = "illust_series/{seriesId}"
const val ROUTE_PIXIVISION = "pixivision"
const val ROUTE_USER_RANKING = "user_ranking"
const val ROUTE_AI_RANKING = "ai_ranking"
const val ROUTE_ERA_RANKING = "era_ranking"
const val ROUTE_WALLPAPER_RANKING = "wallpaper_ranking"
const val ROUTE_HISTORY = "history"
const val ROUTE_BOOKMARKS = "bookmarks"
const val ROUTE_WATCHLIST = "watchlist"
const val ROUTE_READ_LATER = "read_later"
const val ROUTE_NOTIFICATION = "notifications"
const val ROUTE_NOTIFICATION_GROUP = "notification_group/{groupId}?title={title}"
const val ROUTE_BLOCKED = "blocked"
const val ROUTE_DOWNLOADS = "downloads"
const val ROUTE_MANGA_RANKING = "manga_ranking"
const val ROUTE_ILLUST_RANKING = "illust_ranking"
const val ROUTE_NOVEL_RANKING = "novel_ranking"
const val ROUTE_IMAGE_PREVIEW = "image_preview?url={url}&title={title}"
const val ROUTE_LOCAL_READER = "local_reader/{novelId}"
const val ROUTE_FANBOX_HOME = "fanbox_home"
const val ROUTE_FANBOX_POST = "fanbox_post/{postId}"
const val ROUTE_FANBOX_WEB = "fanbox_web?url={url}&title={title}"
const val ROUTE_COMIC_HOME = "comic_home"
const val ROUTE_COMIC_WORK = "comic_work/{workId}"
const val ROUTE_COMIC_READER = "comic_reader/{episodeId}"
const val ROUTE_COMIC_SEARCH = "comic_search"

/**
 * 应用根导航：除 main 外全部全屏隐藏底栏；内层 Tab 须经 MainShell 回调上抛至顶层路由。
 * 深链 scheme 为 `pixiv://`（illust/novel/reader/user/downloads）；OAuth 回调 `pixiv://account/login` 由 MainActivity 拦截。
 */
@Composable
fun PixivNavGraph(
    isLoggedIn: Boolean,
    onboardingComplete: Boolean,
    onCompleteOnboarding: () -> Unit,
    onLogout: () -> Unit,
    navController: NavHostController = rememberNavController(),
    pendingDeepLink: Intent? = null,
    onDeepLinkHandled: (Intent) -> Unit = {},
) {
    // startDestination 只在首次装配确定，后续状态变化通过替换返回栈切换
    val rootRoute = when {
        !onboardingComplete -> ROUTE_ONBOARDING
        isLoggedIn -> ROUTE_MAIN
        else -> ROUTE_AUTH
    }
    val startDestination = rememberSaveable { rootRoute }
    val rootRoutePattern = if (rootRoute == ROUTE_MAIN) MAIN_ROUTE_PATTERN else rootRoute

    // 仅外层确实位于栈底时退出 Activity，避免拦截叠加的搜索主壳返回
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val activity = LocalContext.current as? Activity
    BackHandler(enabled = currentRoute == rootRoutePattern && navController.previousBackStackEntry == null) {
        activity?.finish()
    }

    // transition lambda 非组合上下文，须在组合期提前捕获 motionScheme
    val motionScheme = MaterialTheme.motionScheme
    // 插画/小说详情自带封面 sharedBounds hero，走无位移容器淡入微缩，避免与全局侧滑打架
    fun isHeroDetailRoute(route: String?): Boolean = route == ROUTE_ILLUST || route == ROUTE_NOVEL

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            if (isHeroDetailRoute(targetState.destination.route)) {
                pixivHeroNavEnter(motionScheme)
            } else {
                pixivNavEnter(motionScheme)
            }
        },
        exitTransition = {
            if (isHeroDetailRoute(targetState.destination.route)) {
                pixivHeroNavExit(motionScheme)
            } else {
                pixivNavExit(motionScheme)
            }
        },
        popEnterTransition = {
            if (isHeroDetailRoute(initialState.destination.route)) {
                pixivHeroNavPopEnter(motionScheme)
            } else {
                pixivNavPopEnter(motionScheme)
            }
        },
        popExitTransition = {
            if (isHeroDetailRoute(initialState.destination.route)) {
                pixivHeroNavPopExit(motionScheme)
            } else {
                pixivNavPopExit(motionScheme)
            }
        },
    ) {
        composable(ROUTE_ONBOARDING) {
            OnboardingRoute(onFinished = onCompleteOnboarding)
        }
        composable(ROUTE_AUTH) {
            // 登录成功统一由底部 LaunchedEffect 按登录态替换栈，避免双路导航
            AuthRoute(onLoginSuccess = {})
        }
        composable(
            route = MAIN_ROUTE_PATTERN,
            arguments = listOf(
                navArgument("search") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                },
                navArgument("sn") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val initialSearch = backStackEntry.arguments?.getString("search")
            val initialTagType = if (backStackEntry.arguments?.getString("sn") == "1") TagType.NOVEL else TagType.ILLUST
            // 下发可见性作用域，使内层卡片与顶层详情跨 NavHost 匹配 hero 共享元素
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                MainShell(
                    onLogout = onLogout,
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
                    onOpenWatchlist = { segment ->
                        navController.navigate("watchlist?type=${segment.routeValue}")
                    },
                    onOpenReadLater = {
                        navController.navigate(ROUTE_READ_LATER)
                    },
                    onOpenPixivision = {
                        navController.navigate(ROUTE_PIXIVISION)
                    },
                    onOpenUserRanking = {
                        navController.navigate(ROUTE_USER_RANKING)
                    },
                    onOpenAiRanking = {
                        navController.navigate(ROUTE_AI_RANKING)
                    },
                    onOpenEraRanking = {
                        navController.navigate(ROUTE_ERA_RANKING)
                    },
                    onOpenWallpaperRanking = {
                        navController.navigate(ROUTE_WALLPAPER_RANKING)
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
                    onOpenFanbox = {
                        navController.navigate(ROUTE_FANBOX_HOME)
                    },
                    onOpenFanboxWeb = { url, title ->
                        navController.navigate("fanbox_web?url=${Uri.encode(url)}&title=${Uri.encode(title)}")
                    },
                    onOpenComic = {
                        navController.navigate(ROUTE_COMIC_HOME)
                    },
                    onOpenViewer = { id, page ->
                        navController.navigate("viewer/$id?page=$page")
                    },
                    onOpenReader = { id ->
                        navController.navigate("reader/$id")
                    },
                    onOpenCover = { url ->
                        navController.navigate("image_preview?url=${Uri.encode(url)}")
                    },
                    onExitSearchSession = {
                        navController.safeBack()
                    },
                    initialSearch = initialSearch,
                    initialSearchTagType = initialTagType,
                )
            }
        }
        composable(
            route = ROUTE_ILLUST,
            arguments = listOf(navArgument("illustId") { type = NavType.LongType }),
            deepLinks = listOf(navDeepLink { uriPattern = "pixiv://illust/{illustId}" }),
        ) { _ ->
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                IllustDetailRoute(
                    onBack = { navController.safeBack() },
                    onOpenIllust = { id ->
                        navController.navigate("illust/$id")
                    },
                    onOpenViewer = { id, page ->
                        navController.navigate("viewer/$id?page=$page")
                    },
                    onOpenUser = { userId ->
                        navController.navigate("user/$userId")
                    },
                    onOpenComments = { id ->
                        navController.navigate("comments/illust/$id")
                    },
                    onSearchTag = { tag ->
                        navController.navigateTagSearch(tag)
                    },
                )
            }
        }
        composable(
            route = ROUTE_VIEWER,
            arguments = listOf(
                navArgument("illustId") { type = NavType.LongType },
                navArgument("page") { type = NavType.IntType; defaultValue = 0 },
            ),
        ) {
            ViewerRoute(onBack = { navController.safeBack() })
        }
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
        composable(
            route = ROUTE_NOVEL,
            arguments = listOf(navArgument("novelId") { type = NavType.LongType }),
            deepLinks = listOf(navDeepLink { uriPattern = "pixiv://novel/{novelId}" }),
        ) { backStackEntry ->
            val novelId = backStackEntry.arguments?.getLong("novelId") ?: 0L
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                NovelDetailRoute(
                    novelId = novelId,
                    onBack = { navController.safeBack() },
                    onOpenNovel = { id ->
                        navController.navigate("novel/$id")
                    },
                    onOpenReader = { id ->
                        navController.navigate("reader/$id")
                    },
                    onOpenUser = { userId ->
                        navController.navigate("user/$userId")
                    },
                    onOpenSeries = { seriesId ->
                        navController.navigate("novel_series/$seriesId")
                    },
                    onOpenComments = { id ->
                        navController.navigate("comments/novel/$id")
                    },
                    onSearchTag = { tag ->
                        navController.navigateTagSearch(tag, TagType.NOVEL)
                    },
                )
            }
        }
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
        // 显式方向过渡，覆盖全局默认以适配章节 replace 切换方向
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
                    // 章节切换用 popUpTo(inclusive=true) 替换栈，使返回键直接退出阅读器
                    navController.navigate("reader/$id") {
                        popUpTo(ROUTE_READER) { inclusive = true }
                    }
                },
                onOpenNovelToEnd = { id ->
                    navController.navigate("reader/$id?toEnd=true") {
                        popUpTo(ROUTE_READER) { inclusive = true }
                    }
                },
                onOpenImage = { url ->
                    navController.navigate("image_preview?url=${Uri.encode(url)}")
                },
            )
        }
        composable(
            route = ROUTE_USER,
            arguments = listOf(navArgument("userId") { type = NavType.LongType }),
            deepLinks = listOf(navDeepLink { uriPattern = "pixiv://user/{userId}" }),
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getLong("userId") ?: 0L
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                UserRoute(
                    onBack = { navController.safeBack() },
                    onOpenIllust = { illustId ->
                        navController.navigate("illust/$illustId")
                    },
                    onOpenNovel = { novelId ->
                        navController.navigate("novel/$novelId")
                    },
                    onOpenViewer = { id, page ->
                        navController.navigate("viewer/$id?page=$page")
                    },
                    onOpenCover = { url ->
                        navController.navigate("image_preview?url=${Uri.encode(url)}")
                    },
                    onOpenUser = { target ->
                        navController.navigate("user/$target")
                    },
                    onSearchTag = { tag -> navController.navigateTagSearch(tag) },
                    onNovelSearchTag = { tag -> navController.navigateTagSearch(tag, TagType.NOVEL) },
                    onOpenSeries = { seriesId ->
                        navController.navigate("novel_series/$seriesId")
                    },
                    onOpenMangaSeries = { seriesId ->
                        navController.navigate("illust_series/$seriesId")
                    },
                    onOpenUserBookmarks = {
                        navController.navigate("user_bookmarks/$userId")
                    },
                    onOpenUserFollowing = {
                        navController.navigate("user_following/$userId")
                    },
                    novelDetailPane = { selectedId, novelVm, commentVm, onOpenSeries ->
                        NovelDetailPaneSlot(
                            selectedId = selectedId,
                            novelVm = novelVm,
                            commentVm = commentVm,
                            onOpenSeries = onOpenSeries,
                            onOpenReader = { novelId ->
                                navController.navigate("reader/$novelId")
                            },
                            onOpenUser = { target ->
                                navController.navigate("user/$target")
                            },
                            onSearchTag = { tag -> navController.navigateTagSearch(tag, TagType.NOVEL) },
                        )
                    },
                    seriesDetailPane = { selectedId, onOpenNovel, onOpenSeries ->
                        NovelSeriesPaneSlot(
                            selectedId = selectedId,
                            onOpenNovel = onOpenNovel,
                            onOpenSeries = onOpenSeries,
                            onOpenUser = { target ->
                                navController.navigate("user/$target")
                            },
                            onOpenCover = { url ->
                                navController.navigate("image_preview?url=${Uri.encode(url)}")
                            },
                            onSearchTag = { tag -> navController.navigateTagSearch(tag, TagType.NOVEL) },
                        )
                    },
                )
            }
        }
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
        composable(
            route = ROUTE_NOVEL_SERIES,
            arguments = listOf(navArgument("seriesId") { type = NavType.LongType }),
        ) {
            NovelSeriesRoute(
                onBack = { navController.safeBack() },                onOpenNovel = { novelId ->
                    navController.navigate("novel/$novelId")
                },
                onOpenCover = { url ->
                    navController.navigate("image_preview?url=${Uri.encode(url)}")
                },
                onOpenUser = { target ->
                    navController.navigate("user/$target")
                },
                onSearchTag = { tag -> navController.navigateTagSearch(tag, TagType.NOVEL) },
                onOpenSeries = { seriesId ->
                    navController.navigate("novel_series/$seriesId")
                },
            )
        }
        composable(
            route = ROUTE_MANGA_SERIES,
            arguments = listOf(navArgument("seriesId") { type = NavType.LongType }),
        ) {
            MangaSeriesRoute(
                onBack = { navController.safeBack() },
                onOpenIllust = { illustId ->
                    navController.navigate("illust/$illustId")
                },
                onOpenUser = { userId ->
                    navController.navigate("user/$userId")
                },
            )
        }
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
                onSearchTag = { tag -> navController.navigateTagSearch(tag) },
                onNovelSearchTag = { tag -> navController.navigateTagSearch(tag, TagType.NOVEL) },
            )
        }
        composable(
            route = "watchlist?type={type}",
            arguments = listOf(
                navArgument("type") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                },
            ),
        ) { backStackEntry ->
            WatchlistRoute(
                initialType = WatchlistSegment.parse(backStackEntry.arguments?.getString("type")),
                onBack = { navController.safeBack() },
                onOpenSeries = { seriesId ->
                    navController.navigate("novel_series/$seriesId")
                },
                onOpenIllust = { illustId ->
                    navController.navigate("illust/$illustId")
                },
            )
        }
        composable(ROUTE_READ_LATER) {
            ReadLaterRoute(
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
            )
        }
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
        composable(ROUTE_BLOCKED) {
            BlockedRoute(
                onBack = { navController.safeBack() },
            )
        }
        composable(
            route = ROUTE_DOWNLOADS,
            deepLinks = listOf(navDeepLink { uriPattern = "pixiv://downloads" }),
        ) {
            val context = LocalContext.current
            val retryScope = rememberCoroutineScope()
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
                onRetry = { entry -> retryScope.launch { retryDownload(context, entry) } },
            )
        }
        // 正文绑定到 backStackEntry 的 ViewModel，跳全屏插图或重建 Activity 时保留
        composable(
            route = ROUTE_LOCAL_READER,
            arguments = listOf(navArgument("novelId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val novelId = backStackEntry.arguments?.getLong("novelId") ?: 0L
            val localEntry: LocalReaderEntry = viewModel(viewModelStoreOwner = backStackEntry) {
                LocalReaderEntry()
            }
            val local = localEntry.documentAndTitle
            if (local == null) {
                // 进程重建后内存正文丢失，自动返回入口页
                LaunchedEffect(backStackEntry) {
                    if (navController.currentBackStackEntry == backStackEntry) navController.safeBack()
                }
                return@composable
            }
            ReaderRoute(
                novelId = novelId,
                onBack = { navController.safeBack() },
                onOpenNovel = { id ->
                    navController.navigate("reader/$id")
                },
                onOpenImage = { url ->
                    navController.navigate("image_preview?url=${Uri.encode(url)}")
                },
                localDocument = local.first,
                localTitle = local.second,
            )
        }
        composable(ROUTE_PIXIVISION) {
            PixivisionRoute(
                onBack = { navController.safeBack() },
            )
        }
        composable(ROUTE_USER_RANKING) {
            UserRankingRoute(
                onBack = { navController.safeBack() },
                onOpenUser = { userId ->
                    navController.navigate("user/$userId")
                },
            )
        }
        composable(ROUTE_AI_RANKING) {
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                AiRankingRoute(
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
                    onSearchTag = { tag -> navController.navigateTagSearch(tag) },
                )
            }
        }
        composable(ROUTE_ERA_RANKING) {
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                EraRankingRoute(
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
                    onSearchTag = { tag -> navController.navigateTagSearch(tag) },
                )
            }
        }
        composable(ROUTE_WALLPAPER_RANKING) {
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                WallpaperRankingRoute(
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
                    onSearchTag = { tag -> navController.navigateTagSearch(tag) },
                )
            }
        }
        composable(ROUTE_MANGA_RANKING) {
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
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
                    onSearchTag = { tag -> navController.navigateTagSearch(tag) },
                )
            }
        }
        composable(ROUTE_ILLUST_RANKING) {
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
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
                    onSearchTag = { tag -> navController.navigateTagSearch(tag) },
                )
            }
        }
        composable(ROUTE_NOVEL_RANKING) {
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                NovelRankingRoute(
                    onBack = { navController.safeBack() },
                    onOpenNovel = { novelId ->
                        navController.navigate("novel/$novelId")
                    },
                    onOpenUser = { userId ->
                        navController.navigate("user/$userId")
                    },
                    onSearchTag = { tag -> navController.navigateTagSearch(tag, TagType.NOVEL) },
                    onOpenSeries = { seriesId ->
                        navController.navigate("novel_series/$seriesId")
                    },
                    onOpenReader = { novelId ->
                        navController.navigate("reader/$novelId")
                    },
                )
            }
        }
        composable(ROUTE_FANBOX_HOME) {
            FanboxHomeRoute(
                onBack = { navController.safeBack() },
                onOpenPost = { postId ->
                    navController.navigate("fanbox_post/$postId")
                },
                onOpenWeb = { url, title ->
                    navController.navigate("fanbox_web?url=${Uri.encode(url)}&title=${Uri.encode(title)}")
                },
                onOpenImage = { url, title ->
                    navController.navigate("image_preview?url=${Uri.encode(url)}&title=${Uri.encode(title)}")
                },
            )
        }
        composable(
            route = ROUTE_FANBOX_POST,
            arguments = listOf(navArgument("postId") { type = NavType.StringType }),
        ) { _ ->
            FanboxPostRoute(
                onBack = { navController.safeBack() },
                onOpenWeb = { url, title ->
                    navController.navigate("fanbox_web?url=${Uri.encode(url)}&title=${Uri.encode(title)}")
                },
                onOpenImage = { url, title ->
                    navController.navigate("image_preview?url=${Uri.encode(url)}&title=${Uri.encode(title)}")
                },
            )
        }
        composable(
            route = ROUTE_FANBOX_WEB,
            arguments = listOf(
                navArgument("url") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                },
                navArgument("title") {
                    type = NavType.StringType; nullable = true; defaultValue = null
                },
            ),
        ) { backStackEntry ->
            FanboxWebRoute(
                url = backStackEntry.arguments?.getString("url"),
                title = backStackEntry.arguments?.getString("title"),
                onBack = { navController.safeBack() },
            )
        }
        composable(ROUTE_COMIC_HOME) {
            ComicHomeRoute(
                onBack = { navController.safeBack() },
                onOpenWork = { workId ->
                    navController.navigate("comic_work/$workId")
                },
                onOpenSearch = { navController.navigate(ROUTE_COMIC_SEARCH) },
            )
        }
        composable(
            route = ROUTE_COMIC_WORK,
            arguments = listOf(navArgument("workId") { type = NavType.LongType }),
        ) { _ ->
            ComicWorkDetailRoute(
                onBack = { navController.safeBack() },
                onOpenReader = { episodeId ->
                    navController.navigate("comic_reader/$episodeId")
                },
            )
        }
        // 下一话用 popUpTo 替换栈，避免阅读器无限压栈
        composable(
            route = ROUTE_COMIC_READER,
            arguments = listOf(navArgument("episodeId") { type = NavType.LongType }),
        ) { _ ->
            ComicReaderRoute(
                onBack = { navController.safeBack() },
                onOpenNext = { episodeId ->
                    navController.navigate("comic_reader/$episodeId") {
                        popUpTo(ROUTE_COMIC_READER) { inclusive = true }
                    }
                },
            )
        }
        composable(ROUTE_COMIC_SEARCH) {
            ComicSearchRoute(
                onBack = { navController.safeBack() },
                onOpenWork = { workId ->
                    navController.navigate("comic_work/$workId")
                },
            )
        }
    }

    LaunchedEffect(navController, rootRoute, pendingDeepLink) {
        // 引导完成/登录/登出时替换整栈；已登录的内容页保留当前栈
        val route = navController.currentDestination?.route
        val replaceRoot = when (rootRoute) {
            ROUTE_MAIN -> route == ROUTE_ONBOARDING || route == ROUTE_AUTH
            else -> route != rootRoute
        }
        if (replaceRoot) {
            navController.navigate(rootRoute) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }

        // 内容深链等会话就绪后再交由 Navigation 处理
        if (rootRoute == ROUTE_MAIN && pendingDeepLink != null) {
            // 已在下载管理页时直接消费，避免点击通知重复叠栈
            val alreadyAtDownloads = pendingDeepLink.data?.host == "downloads" &&
                navController.currentDestination?.route == ROUTE_DOWNLOADS
            if (!alreadyAtDownloads) {
                navController.handleDeepLink(pendingDeepLink)
            }
            onDeepLinkHandled(pendingDeepLink)
        }
    }
}

/** 每个返回栈条目只从单槽消费一次正文，出栈时由 ViewModelStore 释放。 */
private class LocalReaderEntry : ViewModel() {
    val documentAndTitle = LocalReaderStore.consume()
}

/**
 * 栈底静默忽略，防 pop 过渡中穿透连点再次触发 navigateUp 弹出 startDestination 导致空栈竞态；
 * 外部深链单页栈则显式回到 main。
 */
private fun NavHostController.safeBack() {
    if (previousBackStackEntry != null) {
        navigateUp()
    } else if (currentDestination?.route in setOf(ROUTE_ILLUST, ROUTE_NOVEL, ROUTE_READER, ROUTE_USER, ROUTE_DOWNLOADS)) {
        navigate(ROUTE_MAIN) {
            popUpTo(graph.id) { inclusive = true }
            launchSingleTop = true
        }
    } else {
        Log.d("PixivNavGraph", "safeBack: 栈底忽略（穿透/误触/越界返回），避免空栈")
    }
}

/** 压栈新 main 搜索会话而非清栈：popUpTo(main) 会弹掉排行/详情来源页导致返回链断裂。 */
private fun NavHostController.navigateTagSearch(tag: String, tagType: TagType = TagType.ILLUST) {
    val sn = if (tagType == TagType.NOVEL) "&sn=1" else ""
    navigate("main?search=${Uri.encode(tag)}$sn") {
        launchSingleTop = true
    }
}
