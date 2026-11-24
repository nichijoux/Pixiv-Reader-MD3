package com.pixiv.reader.feature.follow.ui

import android.annotation.SuppressLint
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.common.ui.WindowSizeClass
import com.pixiv.reader.core.common.ui.classifyWindowWidth
import com.pixiv.reader.core.comment.state.CommentListViewModel
import com.pixiv.reader.core.comment.state.CommentTarget
import com.pixiv.reader.core.comment.ui.CommentPane
import com.pixiv.reader.core.network.illust.IllustViewModel
import com.pixiv.reader.core.network.novel.NovelViewModel
import com.pixiv.reader.core.ui.component.detail.IllustDetailPane
import com.pixiv.reader.core.ui.component.detail.IllustDetailStrings
import com.pixiv.reader.core.ui.component.feedback.PixivHaptics
import com.pixiv.reader.core.ui.component.feedback.rememberHapticCue
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.layout.ListDetailOverlay
import com.pixiv.reader.core.ui.component.layout.detailPaneWidth
import com.pixiv.reader.feature.follow.R
import com.pixiv.reader.feature.follow.data.FollowType
import com.pixiv.reader.feature.follow.state.FollowViewModel
import kotlinx.coroutines.launch

/** 顺序即 HorizontalPager 页序。 */
private val TYPE_TABS = listOf(FollowType.ALL, FollowType.NOVEL, FollowType.ILLUST)

/**
 * 手机/平板统一左右结构：左列用户侧栏固定，右列三段页签 + 详情 pane。
 * 小说/系列 pane 由 app 组合根经槽位注入（feature 间禁止依赖，故反转）；pane 可用性按
 * 「内容区 − 用户列宽」实算，列宽必须与 [FollowUserColumn] 实际列宽一致，否则点击分流与 pane 错位。
 * 本页无 Scaffold/TopAppBar，pane 侧需自行 statusBarsPadding，才能与其它宿主「pane 从顶栏下方起步」对齐。
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowRoute(
    reselectKey: Int = 0,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenViewer: (Long, Int) -> Unit,
    onSearchTag: (String) -> Unit = {},
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

    fun selectPane(novelId: Long? = null, illustId: Long? = null, seriesId: Long? = null) {
        // 所有入口都同时清除其余类型，防止旧详情遮住新目标。
        selectedNovelId = novelId
        selectedIllustId = illustId
        selectedSeriesId = seriesId
    }

    val closePane = {
        selectPane()
        paneHistory = emptyList()
    }
    // 互跳前压栈当前选中（调用方先 push 再切换目标）
    val pushPaneHistory = {
        val current = selectedNovelId?.let { "N:$it" }
            ?: selectedIllustId?.let { "I:$it" }
            ?: selectedSeriesId?.let { "S:$it" }
        if (current != null) paneHistory = paneHistory + current
    }
    // 返回键：栈非空还原上一个选中，栈空关闭
    val popPaneOrClose = {
        val last = paneHistory.lastOrNull()
        if (last == null) {
            closePane()
        } else {
            paneHistory = paneHistory.dropLast(1)
            val id = last.substringAfter(":").toLongOrNull()
            when (last.substringBefore(":")) {
                "N" -> selectPane(novelId = id)
                "S" -> selectPane(seriesId = id)
                "I" -> selectPane(illustId = id)
                else -> selectPane()
            }
        }
    }

    // backstack entry 级，与小说 / 作品 Tab 的 pane 同款模式
    val novelDetailVm: NovelViewModel = hiltViewModel()
    val illustDetailVm: IllustViewModel = hiltViewModel()
    val commentVm: CommentListViewModel = hiltViewModel()

    val users by viewModel.users.collectAsStateWithLifecycle()
    val selectedUserId by viewModel.selectedUserId.collectAsStateWithLifecycle()
    val usersLoading by viewModel.usersPaged.isLoading.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val feedTypesWithMore by viewModel.feedTypesWithMore.collectAsStateWithLifecycle()
    val feedError by viewModel.feedError.collectAsStateWithLifecycle()

    // 下拉刷新手势起始触觉：仅在 false→true 时响
    val haptic = rememberHapticCue()
    var wasRefreshing by remember { mutableStateOf(isRefreshing) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing && !wasRefreshing) {
            haptic(PixivHaptics.HapticCue.GESTURE_START)
        }
        wasRefreshing = isRefreshing
    }

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
        // 宽度必须与下方 FollowUserColumn 实际列宽一致，否则点击分流与 pane 可用性不一致
        val columnWidth = if (isCompact) 60.dp else 168.dp
        val detailPaneEnabled = detailPaneWidth((contentWidth - columnWidth).value, minListWidth = 240f) != null

        Row(modifier = Modifier.fillMaxSize()) {
            FollowUserColumn(
                users = users,
                selectedUserId = selectedUserId,
                isLoadingUsers = usersLoading,
                windowClass = windowClass,
                onSelectUser = viewModel::selectUser,
                onLoadMoreUsers = viewModel::loadMoreUsers,
            )

            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                ListDetailOverlay(
                    selected = selectedNovelId ?: selectedIllustId ?: selectedSeriesId,
                    onClose = closePane,
                    // 返回键沿 pane 互跳链逐级回退，栈空才关闭
                    onBack = popPaneOrClose,
                    modifier = Modifier.fillMaxSize(),
                    // 保底 240dp = 瀑布流单列宽度下限，竖屏窄平板（内容区 ~716dp）仍可启用 pane
                    minListWidth = 240.dp,
                    listContent = { listMax ->
                        // ListDetailOverlay 左移量按「内容居中」推算，需限宽居中才能让 pane 打开后紧邻用户列
                        AdaptiveContentBox(maxWidth = listMax) {
                            val pagerState = rememberPagerState(pageCount = { TYPE_TABS.size })
                            val scope = rememberCoroutineScope()
                            val selectedIndex = pagerState.currentPage.coerceIn(0, TYPE_TABS.lastIndex)

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surface),
                            ) {
                                PrimaryTabRow(
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
                                            // 只让当前页的尾项触发其自身来源分页。
                                            hasMore = page == selectedIndex && type in feedTypesWithMore,
                                            hasError = feedError,
                                            windowClass = windowClass,
                                            scrollToTopKey = reselectKey,
                                            onSearchTag = onSearchTag,
                                            onLoadMore = { viewModel.loadMoreFeed(type) },
                                            onRetry = viewModel::retry,
                                            // 列表点击开启新的右栏导航链，手机直接打开全屏详情。
                                            onOpenIllust = { id ->
                                                if (detailPaneEnabled) {
                                                    paneHistory = emptyList()
                                                    selectPane(illustId = id)
                                                } else {
                                                    onOpenIllust(id)
                                                }
                                            },
                                            onOpenNovel = { id ->
                                                if (detailPaneEnabled) {
                                                    paneHistory = emptyList()
                                                    selectPane(novelId = id)
                                                } else {
                                                    onOpenNovel(id)
                                                }
                                            },
                                            onOpenUser = onOpenUser,
                                            onOpenSeries = { id ->
                                                if (detailPaneEnabled) {
                                                    paneHistory = emptyList()
                                                    selectPane(seriesId = id)
                                                } else {
                                                    onOpenSeries(id)
                                                }
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
                        // pane 侧在此统一避让顶部 inset，与其它宿主行为对齐（避免悬浮关闭钮顶入状态栏）
                        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                            when {
                                selectedNovelId != null -> novelDetailPane(
                                    selectedNovelId,
                                    novelDetailVm,
                                    commentVm,
                                    { id ->
                                        if (detailPaneEnabled) {
                                            pushPaneHistory()
                                            selectPane(seriesId = id)
                                        } else {
                                            onOpenSeries(id)
                                        }
                                    },
                                )
                                selectedSeriesId != null -> seriesDetailPane(
                                    selectedSeriesId,
                                    { id ->
                                        if (detailPaneEnabled) {
                                            pushPaneHistory()
                                            selectPane(novelId = id)
                                        } else {
                                            onOpenNovel(id)
                                        }
                                    },
                                    { id ->
                                        pushPaneHistory()
                                        selectPane(seriesId = id)
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
                                    comments = { onBackToDetail ->
                                        CommentPane(
                                            commentVm = commentVm,
                                            onOpenUser = onOpenUser,
                                            onBackToDetail = onBackToDetail,
                                        )
                                    },
                                    onOpenComments = { selectedIllustId?.let { commentVm.switchTo(CommentTarget.ILLUST, it) } },
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

@Composable
private fun FollowType.labelRes(): Int = when (this) {
    FollowType.ALL -> R.string.follow_tab_all
    FollowType.NOVEL -> R.string.follow_tab_novel
    FollowType.ILLUST -> R.string.follow_tab_illust
}
