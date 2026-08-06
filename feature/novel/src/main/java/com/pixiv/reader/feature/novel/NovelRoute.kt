package com.pixiv.reader.feature.novel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.common.MAX_CONTENT_WIDTH_DP
import com.pixiv.reader.core.ui.component.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.AdaptiveContentTitle
import com.pixiv.reader.core.ui.component.EmptyBox
import com.pixiv.reader.core.ui.component.ErrorBox
import com.pixiv.reader.core.ui.component.LoadingBox
import com.pixiv.reader.core.ui.component.NotificationHost
import com.pixiv.reader.core.ui.component.NovelCard
import com.pixiv.reader.core.ui.component.NovelCardData
import com.pixiv.reader.core.ui.component.rememberNotificationHostState
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
            notificationHostState.show(context.getString(msg.res, *msg.args.toTypedArray()))
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

/**
 * 小说通用列表（推荐/关注页共用）：三态 + 下拉刷新 + 触底自动加载 + 触底手动加载。
 * 整页保持独立 LazyColumn，滚动位置各自独立；[header] 为列表首 item（随滚动，如排行榜 banner）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovelPagedList(
    items: List<Novel>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    error: String?,
    emptyText: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
    onOpenUser: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    val listState = rememberLazyListState()

    // 滚动接近底部时自动加载下一页
    LaunchedEffect(listState, items.size, hasMore) {
        if (!hasMore || items.isEmpty()) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                if (lastVisible >= items.size - 3) onLoadMore()
            }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            isLoading && items.isEmpty() -> LoadingBox()
            error != null && items.isEmpty() -> ErrorBox(
                message = error,
                onRetry = onRetry,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
            items.isEmpty() -> EmptyBox(emptyText, modifier = Modifier.verticalScroll(rememberScrollState()))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (header != null) {
                    item(key = "list_header") { header() }
                }
                items(items, key = { it.id }) { novel ->
                    NovelCard(
                        novel = NovelCardData(
                            id = novel.id,
                            title = novel.title.orEmpty(),
                            coverUrl = novel.image_urls?.square_medium ?: novel.image_urls?.medium,
                            authorId = novel.user?.id ?: 0L,
                            authorName = novel.user?.name.orEmpty(),
                            authorAvatarUrl = novel.user?.profile_image_urls?.best(),
                            publishDate = novel.create_date,
                            seriesTitle = novel.series?.title,
                            seriesId = novel.series?.id,
                            favoriteCount = novel.total_bookmarks ?: 0,
                            wordCount = novel.text_length ?: 0,
                            tags = novel.tags.orEmpty()
                                .take(6)
                                .map { it.translated_name ?: it.name ?: "" }
                                .filter { it.isNotBlank() },
                            isFavorite = novel.is_bookmarked == true,
                        ),
                        onClick = { onOpenNovel(novel.id) },
                        onOpenCover = { (novel.image_urls?.square_medium ?: novel.image_urls?.medium)?.let(onOpenCover) },
                        onOpenAuthor = { novel.user?.id?.let(onOpenUser) },
                        onToggleFavorite = { fav -> onToggleFavorite(novel.id, fav) },
                        onTagClick = onSearchTag,
                        onSeriesClick = { novel.series?.id?.let(onOpenSeries) },
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
                                        .clickable(onClick = onLoadMore)
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

/** 排行榜入口卡片：奖杯 + 标题/副文案 + 箭头（同漫画 Tab 的入口样式）。 */
@Composable
private fun NovelRankingBanner(
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.onPrimaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Leaderboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.novel_ranking_banner),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(R.string.novel_ranking_banner_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
