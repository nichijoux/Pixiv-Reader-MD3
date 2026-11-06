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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.pixiv.reader.core.common.model.TagType
import com.pixiv.reader.feature.watchlist.WatchlistSegment
import com.pixiv.reader.core.ui.component.layout.AdaptiveNavItem
import com.pixiv.reader.core.ui.component.layout.AdaptiveNavScaffold
import com.pixiv.reader.core.ui.component.detail.heroBoundsModifier
import com.pixiv.reader.core.ui.theme.pixivTabFadeIn
import com.pixiv.reader.core.ui.theme.pixivTabFadeOut
import com.pixiv.reader.feature.discover.ui.DiscoverRoute
import com.pixiv.reader.feature.follow.ui.FollowRoute
import com.pixiv.reader.feature.home.HomeRoute
import com.pixiv.reader.feature.manga.MangaRoute
import com.pixiv.reader.feature.novel.ui.NovelRoute
import com.pixiv.reader.feature.user.ui.MeRoute


/** 首页搜索框 ↔ 发现页搜索栏的共享元素 key（hero container transform）。 */
private const val SHARED_SEARCH_BAR_KEY = "search_bar"

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
 * 跨 Tab 搜索启动载荷：关键词 + 预选搜索类型（novelTab 标签搜小说、illustTab 搜作品）。
 * DiscoverRoute 以 [initialQuery]/[initialType] 一次性消费。
 */
data class SearchLaunch(val query: String, val tagType: TagType = TagType.ILLUST)

/**
 * 登录后的主壳。
 * 自适应导航：手机 = 底部 NavigationBar；平板 = 左侧 NavigationRail。
 *
 * 内层 Tab 的跨 Tab 搜索：各 Tab 标签点击 → pendingSearch 缓存（[SearchLaunch]）→
 * 切到 discover_tab 后 DiscoverRoute 以 initialQuery/initialTagType 消费（一次性）。
 * 顶层路由 `main?search={search}` 进入时同样走 pendingSearch 通道（默认搜作品），且内层起点
 * 直达发现页（单条目栈）。该深链进入的搜索会话实例在发现页按返回时经 [onExitSearchSession]
 * 直接离开（回到来源页）。
 *
 * @param onLogout 退出登录（外层清栈回登录页）
 * @param onOpenIllust 打开作品详情（由外层导航处理，底部导航自动隐藏）
 * @param onOpenNovel 打开小说详情
 * @param onOpenUser 打开用户主页
 * @param onOpenSeries 打开小说系列详情
 * @param onOpenHistory 打开浏览历史
 * @param onOpenBookmarks 打开我的收藏
 * @param onOpenWatchlist 打开追更列表（参数为分段：小说 Tab / Me 页传 NOVEL，作品 Tab 顶栏传 MANGA）
 * @param onOpenReadLater 打开稍后再看
 * @param onOpenPixivision 打开 pixivision 特辑列表
 * @param onOpenUserRanking 打开画师榜
 * @param onOpenAiRanking 打开 AI 榜
 * @param onOpenEraRanking 打开年代榜
 * @param onOpenWallpaperRanking 打开壁纸榜
 * @param onOpenNotifications 打开通知中心
 * @param onOpenBlocked 打开屏蔽管理
 * @param onOpenDownloads 打开下载管理
 * @param onOpenMangaRanking 打开漫画排行榜
 * @param onOpenIllustRanking 打开插画排行榜
 * @param onOpenNovelRanking 打开小说排行榜
 * @param onOpenViewer 打开全屏查看器（pane 内图片点击；参数为作品 id + 页码）
 * @param onOpenReader 打开小说阅读器（小说 pane「开始阅读」入口）
 * @param onOpenCover 打开封面/头像全屏大图（系列 pane 封面点击）
 * @param onExitSearchSession 搜索会话返回（经 main?search 深链进入时发现页按返回，离开本 main 实例回到来源页）
 * @param initialSearch 初始搜索词（顶层路由 `main?search=` 单次消费；null 无搜索）
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
    /** 打开 FANBOX 原生首页（我的页生态入口，已登录 FANBOX 时） */
    onOpenFanbox: () -> Unit,
    /** 打开 FANBOX 内置网页（url, title）：登录引导 / 创作者主页等 */
    onOpenFanboxWeb: (String, String) -> Unit,
    /** 打开 pixiv COMIC 原生首页（我的页生态入口；免费内容无需登录） */
    onOpenComic: () -> Unit,
    /** 打开全屏查看器（pane 内图片点击；参数为作品 id + 页码） */
    onOpenViewer: (Long, Int) -> Unit,
    /** 打开小说阅读器（小说 pane「开始阅读」入口） */
    onOpenReader: (Long) -> Unit,
    /** 打开封面/头像全屏大图（系列 pane 封面点击） */
    onOpenCover: (String) -> Unit,
    /** 搜索会话返回：经 main?search 深链进入的实例在发现页按返回时直接离开本实例（回到来源页），由外层导航提供 */
    onExitSearchSession: () -> Unit,
    initialSearch: String? = null,
    // 初始搜索词的标签类型（排行榜等深链通道；与 initialSearch 配套，null=不限）
    initialSearchTagType: TagType? = null,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val tabs = rememberTabs()
    // 待搜索载荷（关键词 + 类型）：Tab 标签点击 / main?search 深链一次性消费。
    // 深链进入时必须以 initialSearch 同步构造初值（discover_tab 首次组合即读取，
    // 不能延后到 LaunchedEffect，否则 initialQuery 丢失）
    var pendingSearch by remember {
        mutableStateOf(
            initialSearch?.takeIf { it.isNotBlank() }?.let {
                SearchLaunch(it, tagType = initialSearchTagType ?: TagType.ILLUST)
            }
        )
    }

    // 再次点击当前 Tab → 对应页面滚回顶部：每个 Tab 一个计数作为 key 下传，
    // 页面侧比较 key 变化才滚动（首次组合只记录不触发，避免带旧值进入页面误滚）
    val tabReselectKeys = remember { mutableStateMapOf<String, Int>() }

    // 统一 Tab 导航：清栈到 start（保存被弹出的状态）+ launchSingleTop + restoreState。
    // 所有「跳到某 Tab」的导航都必须走这里——否则（如 launchSingleTop 不带 popUpTo）会把目标
    // 压栈到当前 Tab 之上形成非标准栈，再切回原 Tab 时同一 navigate 内 popUpTo(saveState) 弹出
    // 并立即 restoreState 恢复同 destination，触发 Navigation 状态恢复异常（点原 Tab 不跳转）。
    fun navigateToTab(route: String) {
        // 已在该 Tab：不重复导航，改为发回顶信号
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

    /**
     * 标签搜索便捷入口：缓存载荷并切到发现页（发现页按 [tagType] 预选搜索分类）。
     *
     * @param tag 标签词
     * @param tagType 标签内容类型（默认作品）
     */
    fun launchTagSearch(tag: String, tagType: TagType = TagType.ILLUST) {
        pendingSearch = SearchLaunch(tag, tagType)
        navigateToTab("discover_tab")
    }

    // 搜索会话返回：仅 main?search 深链进入的实例生效——该实例内层起点即 discover_tab（单条目
    // 栈，内层 NavHost 返回回调恒 disabled），发现页按返回由此直接离开本 main 实例回到来源页，
    // 不会先落回 home_tab。Tab 内 launchTagSearch 进入发现页不受影响（initialSearch 为 null，
    // 内层栈 [home_tab, discover_tab] 正常 pop 回首页，保持原有 hero 过渡返回行为）。
    BackHandler(enabled = initialSearch != null && currentRoute == "discover_tab") {
        onExitSearchSession()
    }

    // hero 过渡：首页搜索框 → 发现页搜索栏共享元素（sharedBounds container transform）。
    // 根布局 SharedTransitionLayout 已上提到 MainActivity（跨 NavHost 层级 hero 共用同一
    // 共享坐标系），此处经 CompositionLocal 取 scope——无 scope 环境（预览等）退普通 Modifier。
    // 旧注释记录 Compose 1.7.x 冷启动首帧可能抛 SharedBoundsNode「Uninitialized LayoutCoordinates」
    // 闪退而弃用；依赖升级（BOM 2026.08）后已修复，重新启用
    // Tab 过渡 lambda 非组合上下文：动效方案在组合期先取好，供各 lambda 闭包捕获
    val motionScheme = MaterialTheme.motionScheme
    AdaptiveNavScaffold(
        items = tabs,
        selectedRoute = currentRoute,
        onSelect = { route -> navigateToTab(route) },
        // 发现页全屏化：隐藏底栏（由首页搜索框进入，返回走系统返回键）
        showBottomBar = currentRoute != "discover_tab",
    ) { padding ->
        NavHost(
            navController = navController,
            // main?search 深链（搜索会话）起点直达发现页：内层单条目栈，返回直接离开本实例；
            // 普通进入起点为首页 Tab
            startDestination = if (initialSearch?.isNotBlank() == true) "discover_tab" else "home_tab",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            // Tab 默认过渡：纯 fade（Expressive effects 快档），不做水平位移——
            // 取代 navigation-compose 默认 700ms 淡入淡出
            enterTransition = { pixivTabFadeIn(motionScheme) },
            exitTransition = { pixivTabFadeOut(motionScheme) },
            popEnterTransition = { pixivTabFadeIn(motionScheme) },
            popExitTransition = { pixivTabFadeOut(motionScheme) },
        ) {
            // 首页：退出淡出（hero 元素由 sharedBounds 接管，浮于容器过渡之上）
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
                    // hero：首页搜索框共享元素修饰（与发现页搜索栏同 key）；
                    // scope 来自根部 SharedTransitionLayout，可见性作用域为本 Tab 的 composable
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
                    // 关注页小说卡标签 → 搜小说
                    onSearchTag = { tag -> launchTagSearch(tag, tagType = TagType.NOVEL) },
                    // 平板 pane：小说卡点击 → 注入 feature:novel 的小说详情 pane
                    // （feature 间禁止依赖，关注页经此槽位复用；作品 pane 在 core:ui，关注页直用。
                    //   VM 创建 + pane 组装由共享槽位承担，导航出口走回调链）
                    novelDetailPane = { selectedId, novelVm, commentVm, onOpenSeries ->
                        NovelDetailPaneSlot(
                            selectedId = selectedId,
                            novelVm = novelVm,
                            commentVm = commentVm,
                            onOpenSeries = onOpenSeries,
                            onOpenReader = onOpenReader,
                            onOpenUser = onOpenUser,
                            // 关注页小说详情标签 → 搜小说（内层通道，与同页系列槽一致）
                            onSearchTag = { tag -> launchTagSearch(tag, tagType = TagType.NOVEL) },
                        )
                    },
                    // 平板 pane：系列卡（小说卡系列标题）点击 → 注入 feature:novel 的小说系列 pane
                    //（VM 创建 + pane 组装由共享槽位承担，导航出口走回调链）
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
            // 发现页：跨 Tab 标签 / main?search 深链进入。
            // 容器动画由 hero 共享元素接管（首页搜索框 → 搜索栏 morph），页面本体仅淡入淡出；
            // 严禁给该 Tab 加位移，否则共享元素会跟随容器位移产生错位
            composable(
                route = "discover_tab",
                enterTransition = { pixivTabFadeIn(motionScheme) },
                exitTransition = { pixivTabFadeOut(motionScheme) },
                popEnterTransition = { pixivTabFadeIn(motionScheme) },
                popExitTransition = { pixivTabFadeOut(motionScheme) },
            ) {
                // 一次性消费跨 Tab 搜索载荷（先取出再置空，避免双重消费）
                val launch = pendingSearch
                pendingSearch = null
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
                    initialQuery = launch?.query,
                    initialTagType = launch?.tagType,
                    // hero：发现页搜索栏共享元素修饰（与首页搜索框同 key）；
                    // scope 来自根部 SharedTransitionLayout，可见性作用域为本 Tab 的 composable
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
