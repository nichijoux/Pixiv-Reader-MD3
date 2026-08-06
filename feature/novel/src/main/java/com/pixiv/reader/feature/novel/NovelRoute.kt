package com.pixiv.reader.feature.novel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.common.MAX_CONTENT_WIDTH_DP
import com.pixiv.reader.core.common.formatCount
import com.pixiv.reader.core.common.formatCountForNovel
import com.pixiv.reader.core.novel.htmlToPlainText
import com.pixiv.reader.core.ui.component.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.AdaptiveContentTitle
import com.pixiv.reader.core.ui.component.EmptyBox
import com.pixiv.reader.core.ui.component.ErrorBox
import com.pixiv.reader.core.ui.component.LoadingBox
import com.pixiv.reader.core.ui.component.NotificationHost
import com.pixiv.reader.core.ui.component.NovelCard
import com.pixiv.reader.core.ui.component.NovelCardData
import com.pixiv.reader.core.ui.component.PixivImage
import com.pixiv.reader.core.ui.component.UserAvatar
import com.pixiv.reader.core.ui.component.rememberNotificationHostState
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
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

/**
 * 小说详情（P4 / 第六十四轮重构）：沉浸式封面 banner（视差）+ 标题 / 作者 / 发布时间 / 统计 / 标签 / 简介 +
 * 阅读 / 收藏 / 追更 / 下载 / 评论 + 系列目录（手机限高滚动 / 平板左栏卡片，等高）。
 * 评论区已拆到独立页 [NovelCommentsRoute]（详情页不再加载评论）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NovelDetailRoute(
    novelId: Long,
    onBack: () -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenReader: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenComments: (Long) -> Unit,
    viewModel: NovelViewModel = hiltViewModel(),
) {
    val novel by viewModel.novel.collectAsStateWithLifecycle()
    val seriesNovels by viewModel.seriesNovels.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val isBookmarking by viewModel.isBookmarking.collectAsStateWithLifecycle()
    val isWatchlisted by viewModel.isWatchlisted.collectAsStateWithLifecycle()
    val isWatchlisting by viewModel.isWatchlisting.collectAsStateWithLifecycle()
    val downloading by viewModel.downloading.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    var showDownloadDialog by rememberSaveable { mutableStateOf(false) }

    val notificationHostState = rememberNotificationHostState()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.message.collect { msg -> notificationHostState.show(context.getString(msg.res, *msg.args.toTypedArray())) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        when {
            isLoading && novel == null -> LoadingBox()
            error != null && novel == null -> ErrorBox(
                message = error?.let { stringResource(it.res, *it.args.toTypedArray()) }.orEmpty(),
                onRetry = viewModel::load,
            )
            novel == null -> EmptyBox(stringResource(R.string.novel_not_found))
            else -> {
                val detail = checkNotNull(novel)
                NovelDetailContent(
                    detail = detail,
                    seriesNovels = seriesNovels,
                    progress = progress,
                    isBookmarked = isBookmarked,
                    isBookmarking = isBookmarking,
                    isWatchlisted = isWatchlisted,
                    isWatchlisting = isWatchlisting,
                    downloading = downloading,
                    downloadProgress = downloadProgress,
                    onBack = onBack,
                    onOpenNovel = onOpenNovel,
                    onOpenReader = onOpenReader,
                    onOpenUser = onOpenUser,
                    onOpenSeries = onOpenSeries,
                    onOpenComments = onOpenComments,
                    onBookmark = viewModel::toggleBookmark,
                    onWatchlist = viewModel::toggleWatchlist,
                    onDownload = { showDownloadDialog = true },
                )
            }
        }
        val dialogNovel = novel
        if (showDownloadDialog && dialogNovel != null) {
            DownloadDialog(
                hasSeries = dialogNovel.series?.id != null,
                onTxtCurrent = {
                    viewModel.exportNovel(NovelExportFormat.TXT)
                    showDownloadDialog = false
                },
                onEpubCurrent = {
                    viewModel.exportNovel(NovelExportFormat.EPUB)
                    showDownloadDialog = false
                },
                onTxtSeries = {
                    viewModel.exportSeries(NovelExportFormat.TXT)
                    showDownloadDialog = false
                },
                onEpubSeries = {
                    viewModel.exportSeries(NovelExportFormat.EPUB)
                    showDownloadDialog = false
                },
                onOfflineCurrent = {
                    viewModel.downloadOfflineCurrent()
                    showDownloadDialog = false
                },
                onOfflineSeries = {
                    viewModel.downloadOfflineSeries()
                    showDownloadDialog = false
                },
                onDismiss = { showDownloadDialog = false },
            )
        }
        NotificationHost(
            state = notificationHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 沉浸式 banner 高度。 */
private val NOVEL_BANNER_HEIGHT = 280.dp
/** banner 图片比容器高出的量（视差平移余量，需大于最大位移 280×0.45≈126dp）。 */
private val NOVEL_BANNER_PARALLAX = 160.dp
/** 平板判断阈值（screenWidthDp ≥ 该值走双栏布局）。 */
private const val TABLET_WIDTH_DP = 600
/** 手机端系列目录滚动区最大高度（占屏高比例，避免随分册数量增高）。 */
private const val NOVEL_TOC_MAX_HEIGHT_FRACTION = 0.4f
/** 平板左栏系列目录卡片宽度。 */
private val NOVEL_TOC_PANEL_WIDTH = 264.dp

/** 详情内容：沉浸式封面 banner（视差）+ 标题信息 / 操作 / 系列目录（手机限高滚动、平板左栏等高）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NovelDetailContent(
    detail: Novel,
    seriesNovels: List<Novel>,
    progress: com.pixiv.reader.core.database.entity.ReadingProgressEntity?,
    isBookmarked: Boolean,
    isBookmarking: Boolean,
    isWatchlisted: Boolean,
    isWatchlisting: Boolean,
    downloading: Boolean,
    downloadProgress: String?,
    onBack: () -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenReader: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenComments: (Long) -> Unit,
    onBookmark: () -> Unit,
    onWatchlist: () -> Unit,
    onDownload: () -> Unit,
) {
    val listState = rememberLazyListState()
    // 视差：banner 被滚过的像素，驱动封面图相对位移
    val scrollOffset by remember {
        derivedStateOf { listState.firstVisibleItemScrollOffset }
    }
    val isTablet = LocalConfiguration.current.screenWidthDp >= TABLET_WIDTH_DP
    val tocMaxHeight = (LocalConfiguration.current.screenHeightDp * NOVEL_TOC_MAX_HEIGHT_FRACTION).dp
    val seriesId = detail.series?.id

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            // 沉浸式封面 banner（延伸到状态栏，上滑视差）——仅作背景，非完整展示
            item(key = "banner") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(NOVEL_BANNER_HEIGHT),
                ) {
                    PixivImage(
                        url = detail.image_urls?.medium
                            ?: detail.image_urls?.square_medium,
                        contentDescription = detail.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(NOVEL_BANNER_HEIGHT + NOVEL_BANNER_PARALLAX)
                            .graphicsLayer {
                                // 图片相对容器下移：滚得越多位移越大 → 上滑时封面移动慢于列表（视差）
                                translationY = scrollOffset * 0.45f
                            },
                        contentScale = ContentScale.Crop,
                    )
                    // 底部渐变过渡到正文背景
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0f to Color.Transparent,
                                        1f to MaterialTheme.colorScheme.surface,
                                    ),
                                ),
                            ),
                    )
                }
            }
            // 标题信息 + 操作；平板且有系列时左侧并排目录卡片（等高）
            item(key = "info_actions") {
                NovelCenteredBox {
                    if (isTablet && seriesNovels.isNotEmpty()) {
                        Row(
                            modifier = Modifier.height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                        ) {
                            NovelTocPanel(
                                seriesNovels = seriesNovels,
                                currentId = detail.id,
                                seriesId = seriesId,
                                onOpenNovel = onOpenNovel,
                                onOpenSeries = onOpenSeries,
                                modifier = Modifier.width(NOVEL_TOC_PANEL_WIDTH),
                            )
                            Column(Modifier.weight(1f)) {
                                NovelHeader(detail, onOpenUser = onOpenUser)
                                NovelActions(
                                    novel = detail,
                                    progress = progress,
                                    isBookmarked = isBookmarked,
                                    isBookmarking = isBookmarking,
                                    isWatchlisted = isWatchlisted,
                                    isWatchlisting = isWatchlisting,
                                    downloading = downloading,
                                    downloadProgress = downloadProgress,
                                    onBookmark = onBookmark,
                                    onWatchlist = onWatchlist,
                                    onDownload = onDownload,
                                    onRead = { onOpenReader(detail.id) },
                                    onComments = { onOpenComments(detail.id) },
                                )
                            }
                        }
                    } else {
                        Column {
                            NovelHeader(detail, onOpenUser = onOpenUser)
                            NovelActions(
                                novel = detail,
                                progress = progress,
                                isBookmarked = isBookmarked,
                                isBookmarking = isBookmarking,
                                isWatchlisted = isWatchlisted,
                                isWatchlisting = isWatchlisting,
                                downloading = downloading,
                                downloadProgress = downloadProgress,
                                onBookmark = onBookmark,
                                onWatchlist = onWatchlist,
                                onDownload = onDownload,
                                onRead = { onOpenReader(detail.id) },
                                onComments = { onOpenComments(detail.id) },
                            )
                        }
                    }
                }
            }
            // 手机端：系列目录单列（限高内部滚动，不随分册数量增高）
            if (!isTablet && seriesNovels.isNotEmpty()) {
                item(key = "series_toc") {
                    NovelCenteredBox {
                        NovelTocScroll(
                            seriesNovels = seriesNovels,
                            currentId = detail.id,
                            seriesId = seriesId,
                            onOpenNovel = onOpenNovel,
                            onOpenSeries = onOpenSeries,
                            maxHeight = tocMaxHeight,
                        )
                    }
                }
            }
            item(key = "bottom_space") { Spacer(Modifier.height(24.dp)) }
        }

        // 返回按钮浮层（沉浸式：浮在 banner 之上，半透明圆底）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f)),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.novel_cd_back),
                    tint = Color.White,
                )
            }
        }
    }
}

/** 平板适配：详情正文内容限宽居中（banner 保持全宽沉浸）。 */
@Composable
private fun NovelCenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = MAX_CONTENT_WIDTH_DP.dp),
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NovelHeader(
    novel: Novel,
    onOpenUser: (Long) -> Unit,
) {
    Column(modifier = Modifier.padding(Spacing.lg)) {
        Text(
            text = novel.title.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier
                .padding(top = Spacing.md)
                .clickable { novel.user?.id?.let(onOpenUser) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            UserAvatar(
                name = novel.user?.name,
                avatarUrl = novel.user?.profile_image_urls?.best(),
                modifier = Modifier.size(36.dp),
            )
            Text(novel.user?.name.orEmpty(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        // 发布时间
        val publishDate = novel.create_date?.take(10)
        if (!publishDate.isNullOrBlank()) {
            Row(
                modifier = Modifier.padding(top = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.novel_publish_date, publishDate),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        // 统计：三块均分撑满整行（字数 / 收藏 / 浏览）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            NovelStatText(
                icon = Icons.Filled.MenuBook,
                label = stringResource(R.string.novel_stat_word),
                value = formatCountForNovel(novel.text_length ?: 0),
                modifier = Modifier.weight(1f),
            )
            NovelStatText(
                icon = Icons.Filled.FavoriteBorder,
                label = stringResource(R.string.novel_stat_bookmark),
                value = formatCount((novel.total_bookmarks ?: 0).toLong()),
                modifier = Modifier.weight(1f),
            )
            NovelStatText(
                icon = Icons.Filled.Visibility,
                label = stringResource(R.string.novel_stat_view),
                value = formatCount((novel.total_view ?: 0).toLong()),
                modifier = Modifier.weight(1f),
            )
        }
        val tags = novel.tags.orEmpty().take(8)
        if (tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tags.forEach { tag ->
                    Text(
                        text = "#${tag.displayName ?: tag.name.orEmpty()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(AppShapes.pill)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
        val caption = novel.caption
        if (!caption.isNullOrBlank()) {
            Text(
                text = htmlToPlainText(caption),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** 统计块：图标 + 数值 + 标签（横向均分整行）。 */
@Composable
private fun NovelStatText(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NovelActions(
    novel: Novel,
    progress: com.pixiv.reader.core.database.entity.ReadingProgressEntity?,
    isBookmarked: Boolean,
    isBookmarking: Boolean,
    isWatchlisted: Boolean,
    isWatchlisting: Boolean,
    downloading: Boolean,
    downloadProgress: String?,
    onBookmark: () -> Unit,
    onWatchlist: () -> Unit,
    onDownload: () -> Unit,
    onRead: () -> Unit,
    onComments: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        val readLabel = if (progress != null && (progress.percentage ?: 0) > 0) {
            stringResource(R.string.novel_continue_reading, progress.percentage)
        } else {
            stringResource(R.string.novel_start_reading)
        }
        Button(onClick = onRead, modifier = Modifier.fillMaxWidth().height(44.dp)) {
            Text(readLabel)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onBookmark,
                enabled = !isBookmarking,
                modifier = Modifier.weight(1f).height(40.dp),
            ) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(if (isBookmarked) stringResource(R.string.novel_bookmarked) else stringResource(R.string.novel_bookmark), modifier = Modifier.padding(start = 4.dp))
            }
            OutlinedButton(
                onClick = onWatchlist,
                enabled = !isWatchlisting && novel.series?.id != null,
                modifier = Modifier.weight(1f).height(40.dp),
            ) {
                Icon(
                    imageVector = if (isWatchlisted) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(if (isWatchlisted) stringResource(R.string.novel_watchlisted) else stringResource(R.string.novel_watch), modifier = Modifier.padding(start = 4.dp))
            }
            OutlinedButton(
                onClick = onDownload,
                enabled = !downloading,
                modifier = Modifier.weight(1f).height(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(stringResource(R.string.novel_download), modifier = Modifier.padding(start = 4.dp))
            }
            OutlinedButton(
                onClick = onComments,
                modifier = Modifier.weight(1f).height(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ModeComment,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(stringResource(R.string.novel_comment_button), modifier = Modifier.padding(start = 4.dp))
            }
        }
        if (downloading && !downloadProgress.isNullOrBlank()) {
            Text(
                text = downloadProgress,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** 系列目录行：序号徽标 + 标题 + 字数/收藏 + 当前章徽标。 */
@Composable
private fun ChapterRow(
    novel: Novel,
    index: Int,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 序号徽标
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(
                    if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondaryContainer,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (index + 1).toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isCurrent) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Text(
                text = novel.title.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.novel_chapter_word, formatCountForNovel(novel.text_length ?: 0)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.novel_chapter_bookmark, formatCount((novel.total_bookmarks ?: 0).toLong())),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isCurrent) {
            Text(
                text = stringResource(R.string.novel_chapter_current),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(AppShapes.pill)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** 系列目录（手机端单列）：标题 + 限高内部滚动列表 + 查看完整系列（不随分册数量增高）。 */
@Composable
private fun NovelTocScroll(
    seriesNovels: List<Novel>,
    currentId: Long,
    seriesId: Long?,
    onOpenNovel: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.novel_toc_section, seriesNovels.size),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .clip(AppShapes.card)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShapes.card)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .verticalScroll(rememberScrollState()),
        ) {
            seriesNovels.forEachIndexed { index, chapter ->
                ChapterRow(
                    novel = chapter,
                    index = index,
                    isCurrent = chapter.id == currentId,
                    onClick = { onOpenNovel(chapter.id) },
                )
            }
        }
        SeriesMoreRow(seriesId, onOpenSeries)
    }
}

/** 系列目录（平板左栏卡片）：与右侧信息等高（外层 Row `IntrinsicSize.Max`），列表内部滚动。 */
@Composable
private fun NovelTocPanel(
    seriesNovels: List<Novel>,
    currentId: Long,
    seriesId: Long?,
    onOpenNovel: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(AppShapes.card)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.novel_toc_section, seriesNovels.size),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            seriesNovels.forEachIndexed { index, chapter ->
                ChapterRow(
                    novel = chapter,
                    index = index,
                    isCurrent = chapter.id == currentId,
                    onClick = { onOpenNovel(chapter.id) },
                )
            }
        }
        SeriesMoreRow(seriesId, onOpenSeries)
    }
}

/** 「查看完整系列 ›」行（无系列 id 时不渲染）。 */
@Composable
private fun SeriesMoreRow(
    seriesId: Long?,
    onOpenSeries: (Long) -> Unit,
) {
    if (seriesId == null) return
    Text(
        text = stringResource(R.string.novel_series_view_all),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenSeries(seriesId) }
            .padding(vertical = 12.dp),
        textAlign = TextAlign.Center,
    )
}

/** 下载选择对话框：导出文件（TXT/EPUB）+ 离线阅读（缓存到应用）。 */
@Composable
private fun DownloadDialog(
    hasSeries: Boolean,
    onTxtCurrent: () -> Unit,
    onEpubCurrent: () -> Unit,
    onTxtSeries: () -> Unit,
    onEpubSeries: () -> Unit,
    onOfflineCurrent: () -> Unit,
    onOfflineSeries: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.novel_download_title)) },
        text = {
            Column {
                DialogGroupTitle(stringResource(R.string.novel_download_group_export))
                DownloadOption(
                    title = stringResource(R.string.novel_download_txt_current),
                    subtitle = stringResource(R.string.novel_download_txt_current_desc),
                    onClick = onTxtCurrent,
                )
                DownloadOption(
                    title = stringResource(R.string.novel_download_epub_current),
                    subtitle = stringResource(R.string.novel_download_epub_current_desc),
                    onClick = onEpubCurrent,
                )
                if (hasSeries) {
                    DownloadOption(
                        title = stringResource(R.string.novel_download_txt_series),
                        subtitle = stringResource(R.string.novel_download_txt_series_desc),
                        onClick = onTxtSeries,
                    )
                    DownloadOption(
                        title = stringResource(R.string.novel_download_epub_series),
                        subtitle = stringResource(R.string.novel_download_epub_series_desc),
                        onClick = onEpubSeries,
                    )
                }
                DialogGroupTitle(stringResource(R.string.novel_download_group_offline))
                DownloadOption(
                    title = stringResource(R.string.novel_download_offline_current),
                    subtitle = stringResource(R.string.novel_download_offline_current_desc),
                    onClick = onOfflineCurrent,
                )
                if (hasSeries) {
                    DownloadOption(
                        title = stringResource(R.string.novel_download_offline_series),
                        subtitle = stringResource(R.string.novel_download_offline_series_desc),
                        onClick = onOfflineSeries,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}

@Composable
private fun DialogGroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun DownloadOption(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
