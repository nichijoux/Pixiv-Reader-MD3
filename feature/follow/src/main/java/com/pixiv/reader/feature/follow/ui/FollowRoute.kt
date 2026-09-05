package com.pixiv.reader.feature.follow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.common.ui.WindowSizeClass
import com.pixiv.reader.core.common.ui.classifyWindowWidth
import com.pixiv.reader.core.network.comment.CommentListViewModel
import com.pixiv.reader.core.network.illust.IllustViewModel
import com.pixiv.reader.core.network.novel.NovelViewModel
import com.pixiv.reader.core.ui.component.detail.IllustDetailPane
import com.pixiv.reader.core.ui.component.detail.IllustDetailStrings
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.layout.ListDetailOverlay
import com.pixiv.reader.core.ui.component.layout.detailPaneWidth
import com.pixiv.reader.feature.follow.R
import com.pixiv.reader.feature.follow.data.FollowType
import com.pixiv.reader.feature.follow.state.FollowViewModel
import kotlinx.coroutines.launch

/** 右列类型段（HorizontalPager 页 → 类型，顺序即页序）。 */
private val TYPE_TABS = listOf(FollowType.ALL, FollowType.NOVEL, FollowType.ILLUST)

/**
 * 关注页：左列关注用户（固定侧栏）+ 右列混合动态流（全部 / 小说 / 插画三段的滑动 Tab）。
 *
 * ## 布局（手机 / 平板统一左右结构）
 * - 左列 [FollowUserColumn]：手机窄版 60dp（头像 + 小字名），平板宽版 168dp（头像 + 完整名），
 *   **固定在左侧、不随 pane 开合移动**
 * - 右列：`TabRow`（全部/小说/插画，均分占满）+ `HorizontalPager` 左右滑动切换；
 *   每页是独立类型流（数据驻留 VM，滑动切回不重复请求），手机上单列流、平板上瀑布流
 * - 左列点用户 → 加载该用户全部作品（插画/漫画/小说混合）；点「全部」恢复关注新作品流
 *
 * ## 平板 Master-Detail
 * 点动态卡片 → 动态流左移让位 + 右侧详情 pane 滑入（[ListDetailOverlay]，作用域为用户列
 * 右侧区域）：小说卡 → [novelDetailPane] 槽位、系列卡（小说卡系列标题）→ [seriesDetailPane]
 * 槽位（feature:novel 的小说详情 / 系列 pane 由 app 组合根注入，feature 间禁止依赖，
 * 故经槽位反转）；作品卡 → core:ui [IllustDetailPane]（直接复用）。
 * pane 可用性按「内容区 − 用户列宽」实算（[detailPaneWidth]，保底 240dp = 关注流单列下限），
 * 保证竖屏窄平板仍可启用；未启用时点击卡片回退全屏路由跳转。
 * 本页无 Scaffold/TopAppBar，状态栏 inset 由列表侧（TabRow）与 pane 侧（[detailPane]
 * 内层 [statusBarsPadding]）各自避让，与其它宿主「pane 从顶栏下方起步」对齐。
 *
 * 回调经 MainShell 上抛到顶层导航（详情 / 查看器 / 用户页等全屏路由）。
 *
 * @param onOpenIllust 打开作品详情全屏路由（手机端点击作品卡）
 * @param onOpenNovel 打开小说详情全屏路由（手机端点击小说卡）
 * @param onOpenUser 打开用户主页全屏路由
 * @param onOpenSeries 打开系列页全屏路由
 * @param onOpenViewer 打开全屏查看器（pane 内图片点击；参数为作品 id + 页码）
 * @param novelDetailPane 小说详情 pane 槽位（app 组合根注入；参数为选中小说 id、
 *   本页作用域的 [NovelViewModel] / [CommentListViewModel] 与「查看完整系列」回调
 *   （宿主分流 pane 内切换系列 pane / 全屏））
 * @param seriesDetailPane 小说系列 pane 槽位（app 组合根注入；参数为选中系列 id、
 *   分册点击回调（宿主分流 pane 内切换小说详情 / 全屏）与分册卡系列标题回调
 *   （宿主压栈后原地切换系列））
 * @param viewModel 关注页 ViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowRoute(
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenViewer: (Long, Int) -> Unit,
    novelDetailPane: @Composable (
        selectedId: Long?,
        novelViewModel: NovelViewModel,
        commentViewModel: CommentListViewModel,
        onOpenSeries: (Long) -> Unit,
    ) -> Unit,
    seriesDetailPane: @Composable (
        selectedId: Long?,
        onOpenNovel: (Long) -> Unit,
        onOpenSeries: (Long) -> Unit,
    ) -> Unit,
    viewModel: FollowViewModel = hiltViewModel(),
) {
    val configuration = LocalConfiguration.current
    val windowClass = remember(configuration) {
        classifyWindowWidth(configuration.screenWidthDp)
    }
    val isCompact = windowClass == WindowSizeClass.Compact

    // 平板 pane 选中态：小说 / 作品 / 系列卡三选一（互相排斥，关闭时全清）
    var selectedNovelId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedIllustId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedSeriesId by rememberSaveable { mutableStateOf<Long?>(null) }
    // pane 间互跳返回栈（"N:小说"/"S:系列"/"I:作品"）：每次互跳压栈当前选中，
    // 返回键逐级还原上一个 pane，栈空才关闭 pane（防互跳链被返回键一步清空）
    var paneHistory by rememberSaveable { mutableStateOf(listOf<String>()) }
    val closePane = {
        selectedNovelId = null
        selectedIllustId = null
        selectedSeriesId = null
        paneHistory = emptyList()
    }
    // 互跳前压栈当前选中（调用方先 push 再切换目标）
    val pushPaneHistory = {
        val current = selectedNovelId?.let { "N:$it" }
            ?: selectedIllustId?.let { "I:$it" }
            ?: selectedSeriesId?.let { "S:$it" }
        if (current != null) paneHistory = paneHistory + current
    }
    // 返回键：栈非空逐级还原上一个选中（pane 间回退），栈空关闭 pane
    val popPaneOrClose = {
        val last = paneHistory.lastOrNull()
        if (last == null) {
            closePane()
        } else {
            paneHistory = paneHistory.dropLast(1)
            selectedNovelId = null
            selectedIllustId = null
            selectedSeriesId = null
            when (last.substringBefore(":")) {
                "N" -> selectedNovelId = last.substringAfter(":").toLongOrNull()
                "S" -> selectedSeriesId = last.substringAfter(":").toLongOrNull()
                "I" -> selectedIllustId = last.substringAfter(":").toLongOrNull()
            }
        }
    }

    // 详情 pane 作用域 ViewModel（本 backstack entry 级，与小说 Tab / 作品 Tab pane 同款模式）
    val novelDetailVm: NovelViewModel = hiltViewModel()
    val illustDetailVm: IllustViewModel = hiltViewModel()
    val commentVm: CommentListViewModel = hiltViewModel()

    val users by viewModel.users.collectAsStateWithLifecycle()
    val selectedUserId by viewModel.selectedUserId.collectAsStateWithLifecycle()
    val usersLoading by viewModel.usersPaged.isLoading.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val feedError by viewModel.feedError.collectAsStateWithLifecycle()

    val allItems by viewModel.allItems.collectAsStateWithLifecycle()
    val novelItems by viewModel.novelItems.collectAsStateWithLifecycle()
    val illustItems by viewModel.illustItems.collectAsStateWithLifecycle()
    // 注意：不能 remember 捕获——三流为 StateFlow 的值快照，重组时必须取最新（否则永远空列表）
    val itemsByType = mapOf(
        FollowType.ALL to allItems,
        FollowType.NOVEL to novelItems,
        FollowType.ILLUST to illustItems,
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val contentWidth = maxWidth
        // pane 可用性按「内容区 − 用户列宽」实算（与 ListDetailOverlay 内部判定同公式），
        // 列表保底放宽到 240dp（关注流瀑布流单列下限），竖屏窄平板仍可启用；
        // 宽度必须与下方 FollowUserColumn 实际列宽一致，否则点击分流与 pane 实际可用性不一致
        val columnWidth = if (isCompact) 60.dp else 168.dp
        val detailPaneEnabled = detailPaneWidth((contentWidth - columnWidth).value, minListWidth = 240f) != null

        Row(modifier = Modifier.fillMaxSize()) {
            // ── 左列：关注用户列表（固定侧栏，不随 pane 开合移动） ──
            FollowUserColumn(
                users = users,
                selectedUserId = selectedUserId,
                isLoadingUsers = usersLoading,
                isCompact = isCompact,
                onSelectUser = viewModel::selectUser,
                onLoadMoreUsers = viewModel::loadMoreUsers,
            )

            // ── 右列：动态流 + 详情 pane（Master-Detail 作用域为用户列右侧区域） ──
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                ListDetailOverlay(
                    selected = selectedNovelId ?: selectedIllustId ?: selectedSeriesId,
                    onClose = closePane,
                    // 返回键沿 pane 互跳链逐级回退，栈空才关闭
                    onBack = popPaneOrClose,
                    modifier = Modifier.fillMaxSize(),
                    // 保底 240dp：关注流瀑布流单列宽度下限，竖屏窄平板（内容区 ~716dp）仍可启用 pane
                    minListWidth = 240.dp,
                    listContent = { listMax ->
                        // 限宽居中（ListDetailOverlay 左移量按「内容居中」推算，pane 打开后
                        // 内容恰好贴本区域左缘、紧邻用户列）
                        AdaptiveContentBox(maxWidth = listMax) {
                            val pagerState = rememberPagerState(pageCount = { TYPE_TABS.size })
                            val scope = rememberCoroutineScope()
                            val selectedIndex = pagerState.currentPage.coerceIn(0, TYPE_TABS.lastIndex)

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    // 沉浸式：背景 surface 延伸到状态栏后面（状态栏透明），色彩统一
                                    .background(MaterialTheme.colorScheme.surface),
                            ) {
                                // TabRow 仅内容让开状态栏文字区；背景与 Column 同色无缝延伸
                                TabRow(
                                    selectedTabIndex = selectedIndex,
                                    modifier = Modifier.statusBarsPadding(),
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ) {
                                    TYPE_TABS.forEachIndexed { index, type ->
                                        Tab(
                                            selected = selectedIndex == index,
                                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                            text = {
                                                Text(stringResource(type.labelRes()))
                                            },
                                        )
                                    }
                                }
                                // 下拉刷新：指示器出现在右列信息流顶部（TabRow 下方），按当前模式重拉
                                PullToRefreshBox(
                                    isRefreshing = isRefreshing,
                                    onRefresh = viewModel::pullRefresh,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    HorizontalPager(
                                        state = pagerState,
                                        modifier = Modifier.fillMaxSize(),
                                    ) { page ->
                                        val type = TYPE_TABS.getOrNull(page) ?: FollowType.ALL
                                        FollowFeed(
                                            items = itemsByType.getValue(type),
                                            isLoading = isLoading,
                                            isLoadingMore = isLoadingMore,
                                            hasError = feedError,
                                            isCompact = isCompact,
                                            onLoadMore = viewModel::loadMoreFeed,
                                            onRetry = viewModel::retry,
                                            // 平板（pane 启用）→ 选中进右栏详情；手机 → 全屏路由跳转
                                            onOpenIllust = { id ->
                                                if (detailPaneEnabled) selectedIllustId = id else onOpenIllust(id)
                                            },
                                            onOpenNovel = { id ->
                                                if (detailPaneEnabled) selectedNovelId = id else onOpenNovel(id)
                                            },
                                            onOpenUser = onOpenUser,
                                            // 系列 pane 启用 → 进右栏；否则全屏路由
                                            onOpenSeries = { id ->
                                                if (detailPaneEnabled) selectedSeriesId = id else onOpenSeries(id)
                                            },
                                            onToggleIllustFavorite = viewModel::toggleIllustFavorite,
                                            onToggleNovelFavorite = viewModel::toggleNovelFavorite,
                                        )
                                    }
                                }
                            }
                        }
                    },
                    detailPane = {
                        // 状态栏避让：本页无 Scaffold/TopAppBar（列表侧由 TabRow 自行
                        // statusBarsPadding），pane 侧在此统一避让顶部 inset，与其它
                        // ListDetailOverlay 宿主（pane 从顶栏下方起步）行为对齐，
                        // 避免悬浮关闭钮 / 顶部内容顶入状态栏
                        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                            when {
                                selectedNovelId != null -> novelDetailPane(
                                    selectedNovelId,
                                    novelDetailVm,
                                    commentVm,
                                    // 「查看完整系列」：pane 启用 → 压栈当前选中、切换到系列 pane；否则全屏路由
                                    { id ->
                                        if (detailPaneEnabled) {
                                            pushPaneHistory()
                                            selectedNovelId = null
                                            selectedSeriesId = id
                                        } else {
                                            onOpenSeries(id)
                                        }
                                    },
                                )
                                selectedSeriesId != null -> seriesDetailPane(
                                    selectedSeriesId,
                                    // 分册点击：pane 启用 → 压栈当前选中、切换到小说详情 pane；否则全屏路由
                                    { id ->
                                        if (detailPaneEnabled) {
                                            pushPaneHistory()
                                            selectedSeriesId = null
                                            selectedNovelId = id
                                        } else {
                                            onOpenNovel(id)
                                        }
                                    },
                                    // 分册卡系列标题：压栈当前选中、原地切换系列（返回键可回退）
                                    { id ->
                                        pushPaneHistory()
                                        selectedSeriesId = id
                                    },
                                )
                                selectedIllustId != null -> IllustDetailPane(
                                    selectedId = selectedIllustId,
                                    strings = IllustDetailStrings(
                                        loadRetry = stringResource(R.string.follow_illust_load_retry),
                                        fullscreen = stringResource(R.string.follow_illust_fullscreen),
                                        statView = stringResource(R.string.follow_illust_stat_view),
                                        statBookmark = stringResource(R.string.follow_illust_stat_bookmark),
                                        statPages = stringResource(R.string.follow_illust_stat_pages),
                                        expand = stringResource(R.string.follow_illust_expand),
                                        collapse = stringResource(R.string.follow_illust_collapse),
                                        follow = stringResource(R.string.follow_illust_follow),
                                        followed = stringResource(R.string.follow_illust_followed),
                                        related = stringResource(R.string.follow_illust_related),
                                        bookmark = stringResource(R.string.follow_illust_bookmark),
                                        bookmarked = stringResource(R.string.follow_illust_bookmarked),
                                        download = stringResource(R.string.follow_illust_download),
                                        comments = stringResource(R.string.follow_illust_comments),
                                    ),
                                    placeholder = stringResource(R.string.follow_detail_placeholder),
                                    onOpenUser = onOpenUser,
                                    onOpenViewer = onOpenViewer,
                                    commentVm = commentVm,
                                    viewModel = illustDetailVm,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

/** 类型段 label 资源（全部 / 小说 / 插画）。 */
@Composable
private fun FollowType.labelRes(): Int = when (this) {
    FollowType.ALL -> R.string.follow_tab_all
    FollowType.NOVEL -> R.string.follow_tab_novel
    FollowType.ILLUST -> R.string.follow_tab_illust
}
