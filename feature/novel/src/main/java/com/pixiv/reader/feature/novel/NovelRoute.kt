package com.pixiv.reader.feature.novel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.Comment
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.common.MAX_CONTENT_WIDTH_DP
import com.pixiv.reader.core.common.formatCount
import com.pixiv.reader.core.common.formatCountForNovel
import com.pixiv.reader.core.novel.htmlToPlainText
import com.pixiv.reader.core.ui.component.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.AdaptiveContentTitle
import com.pixiv.reader.core.ui.component.CommentInput
import com.pixiv.reader.core.ui.component.EmptyBox
import com.pixiv.reader.core.ui.component.ErrorBox
import com.pixiv.reader.core.ui.component.LoadingBox
import com.pixiv.reader.core.ui.component.NotificationHost
import com.pixiv.reader.core.ui.component.NovelCard
import com.pixiv.reader.core.ui.component.NovelCardData
import com.pixiv.reader.core.ui.component.PixivImage
import com.pixiv.reader.core.ui.component.UserAvatar
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
            pagerState.scrollToPage(viewModel.loadDefaultTab().coerceIn(0, 1))
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
 * 小说详情（P4）：沉浸式封面 banner（视差）+ 标题 / 作者 / 统计 / 标签 / 简介 +
 * 阅读入口 / 收藏 / 追更 + 系列分册（点击跳对应详情）+ 评论区。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NovelDetailRoute(
    novelId: Long,
    onBack: () -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenReader: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    viewModel: NovelViewModel = hiltViewModel(),
) {
    val novel by viewModel.novel.collectAsStateWithLifecycle()
    val seriesNovels by viewModel.seriesNovels.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val commentsLoading by viewModel.commentsLoading.collectAsStateWithLifecycle()
    val commentDraft by viewModel.commentDraft.collectAsStateWithLifecycle()
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
                    comments = comments,
                    commentsLoading = commentsLoading,
                    onBack = onBack,
                    onOpenNovel = onOpenNovel,
                    onOpenReader = onOpenReader,
                    onOpenUser = onOpenUser,
                    onBookmark = viewModel::toggleBookmark,
                    onWatchlist = viewModel::toggleWatchlist,
                    onDownload = { showDownloadDialog = true },
                    onRetryComments = viewModel::loadComments,
                    commentDraft = commentDraft,
                    onCommentDraftChange = viewModel::onCommentDraftChange,
                    onPostComment = viewModel::postComment,
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

/** 详情内容：沉浸式封面 banner（视差）+ 正文 + 系列 + 评论。 */
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
    comments: List<Comment>,
    commentsLoading: Boolean,
    onBack: () -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenReader: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onBookmark: () -> Unit,
    onWatchlist: () -> Unit,
    onDownload: () -> Unit,
    onRetryComments: () -> Unit,
    commentDraft: String,
    onCommentDraftChange: (String) -> Unit,
    onPostComment: () -> Unit,
) {
    val listState = rememberLazyListState()
    // 视差：banner 被滚过的像素，驱动封面图相对位移
    val scrollOffset by remember {
        derivedStateOf { listState.firstVisibleItemScrollOffset }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            // 沉浸式封面 banner（延伸到状态栏，上滑视差）
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
            item(key = "header") {
                NovelCenteredBox { NovelHeader(detail, onOpenUser = onOpenUser) }
            }
            item(key = "actions") {
                NovelCenteredBox {
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
                    )
                }
            }
            if (seriesNovels.isNotEmpty()) {
                item(key = "series_title") {
                    NovelCenteredBox {
                        Text(
                            text = stringResource(R.string.novel_series_section, seriesNovels.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
                items(seriesNovels, key = { it.id }) { chapter ->
                    NovelCenteredBox {
                        ChapterRow(
                            novel = chapter,
                            isCurrent = chapter.id == detail.id,
                            onClick = { onOpenNovel(chapter.id) },
                        )
                    }
                }
            }
            item(key = "comments_section") {
                NovelCenteredBox {
                    CommentsSection(
                        comments = comments,
                        loading = commentsLoading,
                        draft = commentDraft,
                        onDraftChange = onCommentDraftChange,
                        onPost = onPostComment,
                        onRetry = onRetryComments,
                    )
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

/** 评论区（第一页主评论）+ 评论输入框。 */
@Composable
private fun CommentsSection(
    comments: List<Comment>,
    loading: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    onPost: () -> Unit,
    onRetry: () -> Unit,
) {
    // 注意：必须有统一根容器（Column）。
    // CommentsSection 被包在 NovelCenteredBox 的 Box 里，Box 子项默认堆叠，
    // 若直接 emit 多个并列 composable（标题 + 各评论行）会全部重叠。
    Column {
        Text(
            text = stringResource(R.string.novel_comments_section, comments.size),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        when {
            loading && comments.isEmpty() -> Text(
                text = stringResource(R.string.novel_comments_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )

            comments.isEmpty() -> Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.novel_comments_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.novel_comments_retry),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onRetry)
                        .padding(4.dp),
                )
            }

            else -> comments.forEach { comment ->
                CommentRow(comment)
                Spacer(Modifier.height(8.dp))
            }
        }
        CommentInput(
            draft = draft,
            onDraftChange = onDraftChange,
            onPost = onPost,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun CommentRow(comment: Comment) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        UserAvatar(
            name = comment.user?.name,
            avatarUrl = comment.user?.profile_image_urls?.best(),
            modifier = Modifier.size(36.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.user?.name ?: stringResource(R.string.novel_anonymous_user),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatCommentDate(comment.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = comment.comment ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
            // 树形对话：父评论下方直接渲染子回复（浅色块 + 缩进，区分父子层）。
            val replies = comment.replies.orEmpty()
            if (replies.isNotEmpty()) {
                val visibleReplies = replies.take(20)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    visibleReplies.forEachIndexed { index, reply ->
                        ReplyRow(reply)
                        if (index != visibleReplies.lastIndex) {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

/** 子评论行（树形对话第二层：缩进浅色块内、带小头像、无分隔线）。 */
@Composable
private fun ReplyRow(reply: Comment) {
    Row(verticalAlignment = Alignment.Top) {
        UserAvatar(
            name = reply.user?.name,
            avatarUrl = reply.user?.profile_image_urls?.best(),
            modifier = Modifier.size(28.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = reply.user?.name ?: stringResource(R.string.novel_anonymous_user),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatCommentDate(reply.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val parentName = reply.parent_comment?.user?.name
            val prefix = if (!parentName.isNullOrBlank()) {
                stringResource(R.string.novel_reply_prefix, parentName)
            } else {
                null
            }
            Text(
                text = buildString {
                    if (prefix != null) append(prefix)
                    append(reply.comment.orEmpty())
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** pixiv 评论时间为 ISO 格式，取日期部分 yyyy-MM-dd。 */
private fun formatCommentDate(date: String?): String = date?.take(10) ?: ""

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NovelHeader(
    novel: Novel,
    onOpenUser: (Long) -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = novel.title.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier
                .padding(top = 12.dp)
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
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            NovelStatText(stringResource(R.string.novel_stat_word), formatCountForNovel(novel.text_length ?: 0))
            NovelStatText(stringResource(R.string.novel_stat_bookmark), formatCount((novel.total_bookmarks ?: 0).toLong()))
            NovelStatText(stringResource(R.string.novel_stat_view), formatCount((novel.total_view ?: 0).toLong()))
        }
        val tags = novel.tags.orEmpty().take(8)
        if (tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tags.forEach { tag ->
                    Text(
                        text = "#${tag.displayName ?: tag.name.orEmpty()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
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

@Composable
private fun NovelStatText(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun ChapterRow(
    novel: Novel,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
