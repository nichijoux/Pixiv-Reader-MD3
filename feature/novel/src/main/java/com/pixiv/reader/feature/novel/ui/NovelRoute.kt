package com.pixiv.reader.feature.novel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ui.component.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.AdaptiveContentTitle
import com.pixiv.reader.core.ui.component.EmptyBox
import com.pixiv.reader.core.ui.component.ErrorBox
import com.pixiv.reader.core.ui.component.NotificationHost
import com.pixiv.reader.core.ui.component.SeriesCard
import com.pixiv.reader.core.ui.component.SeriesCardData
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
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })

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

    // 关注/追更 Tab 首次进入才加载（数据已驻留则不重复请求）
    LaunchedEffect(pagerState.currentPage) {
        when (pagerState.currentPage) {
            1 -> viewModel.ensureFollowLoaded()
            2 -> viewModel.ensureWatchlistLoaded()
        }
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
                    selectedTabIndex = pagerState.currentPage.coerceIn(0, 2),
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
                    Tab(
                        selected = pagerState.currentPage == 2,
                        onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                        text = { Text(stringResource(R.string.novel_tab_watchlist)) },
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
                        1 -> NovelFollowTab(
                            onOpenNovel = onOpenNovel,
                            onOpenCover = onOpenCover,
                            onOpenUser = onOpenUser,
                            onSearchTag = onSearchTag,
                            onOpenSeries = onOpenSeries,
                            onToggleFavorite = viewModel::toggleNovelFavorite,
                            viewModel = viewModel,
                        )
                        else -> NovelWatchlistTab(
                            onOpenSeries = onOpenSeries,
                            onOpenUser = onOpenUser,
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

/** 追更页：已追更的小说系列列表（复用 core:ui [SeriesCard]，下拉刷新 + 触底自动加载）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovelWatchlistTab(
    onOpenSeries: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    viewModel: NovelFeedViewModel,
) {
    val items by viewModel.watchlist.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.watchlist.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.watchlist.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.watchlist.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.watchlist.error.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isWatchlistRefreshing.collectAsStateWithLifecycle()
    val infos by viewModel.watchlistInfos.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // 滚动接近底部时自动加载下一页
    LaunchedEffect(listState, items.size, hasMore) {
        if (!hasMore || items.isEmpty()) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                if (lastVisible >= items.size - 3) viewModel.loadMoreWatchlist()
            }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::pullRefreshWatchlist,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            // 首载 / 下拉刷新（reset 后 items 清空）→ 骨架占位
            (isLoading || isRefreshing) && items.isEmpty() -> NovelFeedSkeleton(showBannerHeader = false)
            error != null && items.isEmpty() -> ErrorBox(
                message = error,
                onRetry = viewModel::refreshWatchlist,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
            items.isEmpty() -> EmptyBox(
                stringResource(R.string.novel_watchlist_empty),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.id }) { series ->
                    val info = infos[series.id]
                    SeriesCard(
                        data = SeriesCardData(
                            title = if (series.isMasked) {
                                stringResource(R.string.novel_watchlist_masked)
                            } else {
                                series.title
                            },
                            // 封面/简介/连载状态/字数来自 getNovelSeries 详情（SeriesDetailCache，与用户页同缓存）
                            coverUrl = info?.coverUrl,
                            caption = info?.caption,
                            isConcluded = info?.isConcluded,
                            partsCount = series.published_content_count,
                            totalChars = info?.totalChars ?: 0,
                            // 最近更新时间（ISO 取前 10 位 yyyy-MM-dd，与 NovelCard 一致），作者行右侧 icon 展示
                            updatedAt = series.last_published_content_datetime?.take(10),
                            authorName = series.user?.name,
                            authorAvatarUrl = series.user?.profile_image_urls?.best(),
                        ),
                        onClick = { onOpenSeries(series.id) },
                        onOpenAuthor = { series.user?.id?.let(onOpenUser) },
                    )
                }
                if (hasMore) {
                    item(key = "load_more") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isLoadingMore) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Text(
                                    text = stringResource(R.string.novel_load_more),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(onClick = viewModel::loadMoreWatchlist)
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
