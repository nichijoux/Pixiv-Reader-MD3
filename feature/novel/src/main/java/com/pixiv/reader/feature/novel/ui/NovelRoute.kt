package com.pixiv.reader.feature.novel.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ui.component.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.AdaptiveContentTitle
import com.pixiv.reader.core.ui.component.NotificationHost
import com.pixiv.reader.core.ui.component.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.toNotificationType
import com.pixiv.reader.feature.novel.R
import com.pixiv.reader.feature.novel.state.NovelFeedViewModel
import kotlinx.coroutines.launch

/**
 * 小说 Tab：推荐 / 关注 两个页签（滑动切换）+ 推荐页排行榜入口 banner（第五十三/五十四轮）。
 *
 * 顶部与漫画 Tab 一致：`Scaffold + TopAppBar`（自带状态栏 inset），actions 为排行榜入口。
 * 推荐页：排行榜入口 banner（列表头部，随滚动）+ 推荐流；关注页：关注用户的新小说流。
 * 两个流各自独立 PagedState（数据驻留 VM），关注 Tab 首次进入才加载，切回不重复请求；
 * 均支持下拉刷新（PullToRefreshBox）。初始页由「我的-浏览设置-小说默认页」决定。
 * item 与搜索结果一致（NovelCard）：封面→全屏大图、作者→主页、收藏、标签→搜索。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelRoute(
    onOpenNovel: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
    onOpenUser: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenNovelRanking: () -> Unit,
    onOpenSeries: (Long) -> Unit,
    viewModel: NovelFeedViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })

    // 操作通知（收藏等）：collect VM message → NotificationHost
    val notificationHostState = rememberNotificationHostState()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.message.collect { msg ->
            notificationHostState.show(context.getString(msg.res, *msg.args.toTypedArray()), type = msg.type.toNotificationType())
        }
    }

    // 首帧定位默认页（进程重建回偏好页；旋转保留当前页）
    var defaultTabApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!defaultTabApplied) {
            defaultTabApplied = true
            pagerState.scrollToPage(viewModel.loadDefaultTab().value.coerceIn(0, 1))
        }
    }

    // 关注 Tab 首次进入才加载（数据已驻留则不重复请求）
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 1) viewModel.ensureFollowLoaded()
    }

    Scaffold(
        snackbarHost = { NotificationHost(notificationHostState) },
        topBar = {
            TopAppBar(
                title = {
                    // 平板限宽居中（与下方 AdaptiveContentBox 内容对齐）
                    AdaptiveContentTitle(stringResource(R.string.novel_title))
                },
                actions = {
                    IconButton(onClick = onOpenNovelRanking) {
                        Icon(
                            Icons.Filled.Leaderboard,
                            contentDescription = stringResource(R.string.novel_cd_ranking),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        // 平板限宽居中：PrimaryTabRow + HorizontalPager 不超过 MAX_CONTENT_WIDTH_DP
        AdaptiveContentBox(modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage.coerceIn(0, 1),
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text(stringResource(R.string.novel_tab_recommend)) },
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text(stringResource(R.string.novel_tab_follow)) },
                    )
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                ) { page ->
                    when (page) {
                        0 -> NovelRecommendTab(
                            onOpenNovelRanking = onOpenNovelRanking,
                            onOpenNovel = onOpenNovel,
                            onOpenCover = onOpenCover,
                            onOpenUser = onOpenUser,
                            onSearchTag = onSearchTag,
                            onOpenSeries = onOpenSeries,
                            onToggleFavorite = viewModel::toggleNovelFavorite,
                            viewModel = viewModel,
                        )
                        else -> NovelFollowTab(
                            onOpenNovel = onOpenNovel,
                            onOpenCover = onOpenCover,
                            onOpenUser = onOpenUser,
                            onSearchTag = onSearchTag,
                            onOpenSeries = onOpenSeries,
                            onToggleFavorite = viewModel::toggleNovelFavorite,
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }
    }
}

/** 推荐页：排行榜入口 banner（列表头部，随滚动）+ 推荐流（下拉刷新）。 */
@Composable
private fun NovelRecommendTab(
    onOpenNovelRanking: () -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
    onOpenUser: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    viewModel: NovelFeedViewModel,
) {
    val items by viewModel.feed.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.feed.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.feed.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.feed.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.feed.error.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    NovelPagedList(
        items = items,
        isLoading = isLoading,
        isLoadingMore = isLoadingMore,
        hasMore = hasMore,
        error = error,
        emptyText = stringResource(R.string.novel_feed_empty),
        isRefreshing = isRefreshing,
        onRefresh = viewModel::pullRefresh,
        onRetry = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onOpenNovel = onOpenNovel,
        onOpenCover = onOpenCover,
        onOpenUser = onOpenUser,
        onSearchTag = onSearchTag,
        onOpenSeries = onOpenSeries,
        onToggleFavorite = onToggleFavorite,
        header = { NovelRankingBanner(onClick = onOpenNovelRanking) },
    )
}

/** 关注页：关注用户的新小说流（下拉刷新）。 */
@Composable
private fun NovelFollowTab(
    onOpenNovel: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
    onOpenUser: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    viewModel: NovelFeedViewModel,
) {
    val items by viewModel.follow.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.follow.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.follow.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.follow.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.follow.error.collectAsStateWithLifecycle()
    val isFollowRefreshing by viewModel.isFollowRefreshing.collectAsStateWithLifecycle()

    NovelPagedList(
        items = items,
        isLoading = isLoading,
        isLoadingMore = isLoadingMore,
        hasMore = hasMore,
        error = error,
        emptyText = stringResource(R.string.novel_follow_empty),
        isRefreshing = isFollowRefreshing,
        onRefresh = viewModel::pullRefreshFollow,
        onRetry = viewModel::refreshFollow,
        onLoadMore = viewModel::loadMoreFollow,
        onOpenNovel = onOpenNovel,
        onOpenCover = onOpenCover,
        onOpenUser = onOpenUser,
        onSearchTag = onSearchTag,
        onOpenSeries = onOpenSeries,
        onToggleFavorite = onToggleFavorite,
    )
}
