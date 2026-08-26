package com.pixiv.reader.feature.novel.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.activity.compose.BackHandler
import com.pixiv.reader.core.network.comment.CommentListViewModel
import com.pixiv.reader.core.network.novel.NovelViewModel
import com.pixiv.reader.core.ui.component.comment.CommentListContent
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.common.ui.WindowSizeClass
import com.pixiv.reader.core.common.ui.classifyWindowWidth
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ui.component.card.NovelCard
import com.pixiv.reader.core.ui.component.card.toCardData
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.feedback.toNotificationType
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentTitle
import com.pixiv.reader.core.ui.component.list.RankingList
import com.pixiv.reader.feature.novel.R
import com.pixiv.reader.feature.novel.state.NovelLanguageFilter
import com.pixiv.reader.feature.novel.state.NovelRankingViewModel
import com.pixiv.reader.feature.novel.state.labelRes
import com.pixiv.reader.feature.novel.state.matchesLanguageFilter

/**
 * 小说排行榜全屏页：分段 Tab + 左右滑动切换（复用通用 [RankingList]）。
 * 条目使用通用 [NovelCard]（上下两部分布局），封面左上角叠加排名徽标（[NovelCard.rank]）。
 *
 * 每段数据由 ViewModel 内独立 PagedState 承载（RankingList 按段 collect），滑动切回已加载段
 * 不重复请求、无过渡动画。
 *
 * @param onBack 返回
 * @param onOpenNovel 点击卡片打开小说详情
 * @param onOpenCover 点击封面打开全屏大图
 * @param onOpenUser 点击作者行打开用户主页
 * @param onSearchTag 点击标签搜索（排行榜页暂不跳转，由调用方决定）
 * @param onOpenReader 右栏「开始阅读」打开阅读器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelRankingRoute(
    onBack: () -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
    onOpenUser: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenReader: (Long) -> Unit,
    viewModel: NovelRankingViewModel = hiltViewModel(),
) {
    val notificationHostState = rememberNotificationHostState()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.message.collect { msg ->
            notificationHostState.show(
                context.getString(msg.res, *msg.args.toTypedArray()),
                type = msg.type.toNotificationType()
            )
        }
    }
    val languageFilter by viewModel.languageFilter.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }
    // 平板（Expanded, 窗口 > 840dp）：双栏 list+detail；手机/小屏走单栏（原行为不变）
    val isExpanded = classifyWindowWidth(LocalConfiguration.current.screenWidthDp) == WindowSizeClass.Expanded
    var selected by remember { mutableStateOf<Novel?>(null) }
    // 右栏详情：内嵌 NovelViewModel（同一 backstack entry 作用域），选中项变化时 switchTo
    val detailVm: NovelViewModel = hiltViewModel()
    // 右栏评论区：点评论按钮切换；内嵌 CommentListViewModel（同作用域），switchTo 换目标
    val commentVm: CommentListViewModel = hiltViewModel()
    var showComments by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    // 手势返回拦截：右栏显示评论区时，先退回详情（局部状态），不直接退出排行榜页
    BackHandler(enabled = showComments) { showComments = false }

    Scaffold(
        snackbarHost = { NotificationHost(notificationHostState) },
        topBar = {
            TopAppBar(
                title = {
                    // 平板限宽居中（与下方 RankingList 内容对齐）
                    AdaptiveContentTitle(stringResource(R.string.novel_ranking_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.novel_cd_back),
                        )
                    }
                },
                actions = {
                    Box {
                        TextButton(
                            onClick = { menuExpanded = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        ) {
                            Icon(
                                Icons.Filled.Translate,
                                contentDescription = null,
                                modifier = Modifier.width(18.dp),
                            )
                            Text(
                                text = stringResource(languageFilter.labelRes()),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = stringResource(R.string.novel_cd_language_filter),
                                modifier = Modifier.width(18.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            NovelLanguageFilter.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(option.labelRes())) },
                                    leadingIcon = {
                                        if (option == languageFilter) {
                                            Icon(Icons.Filled.Check, contentDescription = null)
                                        } else {
                                            Spacer(Modifier.width(24.dp))
                                        }
                                    },
                                    onClick = {
                                        viewModel.setLanguageFilter(option)
                                        menuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        if (isExpanded) {
            Row(modifier = Modifier.padding(padding).fillMaxSize()) {
                // 左：列表（点行只更新选中态，不跳转）
                RankingList(
                    modes = viewModel.modes,
                    onModeSelect = viewModel::onPageSelected,
                    stateFor = viewModel::stateFor,
                    onRetry = viewModel::retry,
                    onLoadMore = viewModel::loadMore,
                    modifier = Modifier.weight(0.4f).fillMaxHeight(),
                    emptyText = stringResource(R.string.novel_ranking_empty),
                    filter = { novel -> novel.matchesLanguageFilter(languageFilter) },
                    filteredEmptyText = stringResource(R.string.novel_ranking_filter_empty),
                    skeleton = { NovelFeedSkeleton(showBannerHeader = false) },
                ) { item, rank ->
                    NovelCard(
                        novel = item.toCardData(),
                        rank = rank,
                        onClick = { selected = item },
                        onOpenCover = {
                            (item.image_urls?.square_medium ?: item.image_urls?.medium)?.let(
                                onOpenCover
                            )
                        },
                        onOpenAuthor = { item.user?.id?.let(onOpenUser) },
                        onToggleFavorite = { fav -> viewModel.toggleNovelFavorite(item.id, fav) },
                        onTagClick = onSearchTag,
                        onSeriesClick = { item.series?.id?.let(onOpenSeries) },
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                // 中：分隔
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                // 右：真详情（内嵌 NovelViewModel，随选中项加载；评论区分支）
                Box(
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    val s = selected
                    val currentId = s?.id
                    LaunchedEffect(currentId) {
                        if (currentId != null) detailVm.switchTo(currentId)
                    }
                    val dNovel by detailVm.novel.collectAsStateWithLifecycle()
                    val dSeries by detailVm.seriesNovels.collectAsStateWithLifecycle()
                    val dProgress by detailVm.progress.collectAsStateWithLifecycle()
                    val dIsLoading by detailVm.isLoading.collectAsStateWithLifecycle()
                    val dError by detailVm.error.collectAsStateWithLifecycle()
                    val dBookmarked by detailVm.isBookmarked.collectAsStateWithLifecycle()
                    val dBookmarking by detailVm.isBookmarking.collectAsStateWithLifecycle()
                    val dWatchlisted by detailVm.isWatchlisted.collectAsStateWithLifecycle()
                    val dWatchlisting by detailVm.isWatchlisting.collectAsStateWithLifecycle()
                    val dFollowed by detailVm.isAuthorFollowed.collectAsStateWithLifecycle()
                    val dFollowing by detailVm.isAuthorFollowing.collectAsStateWithLifecycle()
                    val dDownloading by detailVm.downloading.collectAsStateWithLifecycle()
                    val dDownloadProgress by detailVm.downloadProgress.collectAsStateWithLifecycle()
                    when {
                        currentId == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.novel_ranking_preview_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        showComments -> NovelRankingCommentsPane(
                            commentVm = commentVm,
                            currentId = currentId,
                            onOpenUser = onOpenUser,
                            onBackToDetail = { showComments = false },
                        )

                        dIsLoading && dNovel == null -> LoadingBox()
                        dError != null && dNovel == null -> ErrorBox(
                            message = dError?.let { stringResource(it.res, *it.args.toTypedArray()) }.orEmpty(),
                            onRetry = detailVm::load,
                        )

                        else -> {
                            val detail = dNovel
                            if (detail != null) {
                                Column(Modifier.fillMaxSize()) {
                                    NovelDetailContent(
                                        detail = detail,
                                        seriesNovels = dSeries,
                                        progress = dProgress,
                                        isAuthorFollowed = dFollowed,
                                        isAuthorFollowing = dFollowing,
                                        downloading = dDownloading,
                                        downloadProgress = dDownloadProgress,
                                        onBack = {},
                                        onOpenNovel = { id -> detailVm.switchTo(id) },
                                        onOpenReader = onOpenReader,
                                        onOpenUser = onOpenUser,
                                        onOpenSeries = onOpenSeries,
                                        onToggleFollowAuthor = detailVm::toggleFollowAuthor,
                                        modifier = Modifier.weight(1f),
                                        forceSingleColumn = true,
                                    )
                                    NovelActionBar(
                                        seriesId = detail.series?.id,
                                        isBookmarked = dBookmarked,
                                        isBookmarking = dBookmarking,
                                        isWatchlisted = dWatchlisted,
                                        isWatchlisting = dWatchlisting,
                                        downloading = dDownloading,
                                        onBookmark = detailVm::toggleBookmark,
                                        onWatchlist = detailVm::toggleWatchlist,
                                        onDownload = { showDownloadDialog = true },
                                        onComments = {
                                            showComments = true
                                            commentVm.switchTo("novel", currentId)
                                        },
                                    )
                                    // 下载格式选择弹窗（复用详情页 DownloadSheet）
                                    if (showDownloadDialog) {
                                        DownloadSheet(
                                            config = DownloadSheetConfig.Detail(detail.series?.id?.let { it > 0L } == true),
                                            onFormat = { format, scope, _ ->
                                                detailVm.export(format.name, scope == NovelDownloadScope.SERIES)
                                                showDownloadDialog = false
                                            },
                                            onDismiss = { showDownloadDialog = false },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // 手机/小屏：单栏列表，点击行直接跳详情（保持原行为不变）
            RankingList(
                modes = viewModel.modes,
                onModeSelect = viewModel::onPageSelected,
                stateFor = viewModel::stateFor,
                onRetry = viewModel::retry,
                onLoadMore = viewModel::loadMore,
                modifier = Modifier.padding(padding),
                emptyText = stringResource(R.string.novel_ranking_empty),
                filter = { novel -> novel.matchesLanguageFilter(languageFilter) },
                filteredEmptyText = stringResource(R.string.novel_ranking_filter_empty),
                skeleton = { NovelFeedSkeleton(showBannerHeader = false) },
            ) { item, rank ->
                NovelCard(
                    novel = item.toCardData(),
                    rank = rank,
                    onClick = { onOpenNovel(item.id) },
                    onOpenCover = {
                        (item.image_urls?.square_medium ?: item.image_urls?.medium)?.let(
                            onOpenCover
                        )
                    },
                    onOpenAuthor = { item.user?.id?.let(onOpenUser) },
                    onToggleFavorite = { fav -> viewModel.toggleNovelFavorite(item.id, fav) },
                    onTagClick = onSearchTag,
                    onSeriesClick = { item.series?.id?.let(onOpenSeries) },
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }
    }
}

/**
 * 排行右栏评论区：顶部返回条（切回详情）+ 评论列表（内嵌 CommentListViewModel）。
 * 左栏保持排行榜列表不变（评论区占右栏，左栏仍是排行榜）。
 */
@Composable
private fun NovelRankingCommentsPane(
    commentVm: CommentListViewModel,
    currentId: Long,
    onOpenUser: (Long) -> Unit,
    onBackToDetail: () -> Unit,
) {
    val comments by commentVm.commentsPaged.items.collectAsStateWithLifecycle()
    val isLoading by commentVm.commentsPaged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by commentVm.commentsPaged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by commentVm.commentsPaged.hasMore.collectAsStateWithLifecycle()
    val error by commentVm.commentsPaged.error.collectAsStateWithLifecycle()
    val replies by commentVm.replies.collectAsStateWithLifecycle()
    val repliesLoading by commentVm.repliesLoading.collectAsStateWithLifecycle()
    val expandedReplies by commentVm.expandedReplies.collectAsStateWithLifecycle()
    val draft by commentVm.commentDraft.collectAsStateWithLifecycle()
    val replyTarget by commentVm.replyTarget.collectAsStateWithLifecycle()
    val stamps by commentVm.stamps.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部返回条：切回详情
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBackToDetail)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.novel_ranking_back_to_detail),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        CommentListContent(
            comments = comments,
            isLoading = isLoading,
            isLoadingMore = isLoadingMore,
            hasMore = hasMore,
            error = error.orEmpty(),
            replies = replies,
            repliesLoading = repliesLoading,
            expandedReplies = expandedReplies,
            draft = draft,
            replyTarget = replyTarget,
            stamps = stamps,
            emptyText = stringResource(R.string.novel_ranking_comment_empty),
            onLoadComments = commentVm::loadComments,
            onLoadMoreComments = commentVm::loadMoreComments,
            onOpenUser = onOpenUser,
            onReply = { target, topId -> commentVm.setReplyTarget(target, topId) },
            onLoadReplies = commentVm::loadReplies,
            onToggleRepliesExpanded = commentVm::toggleRepliesExpanded,
            onDraftChange = commentVm::onCommentDraftChange,
            onPost = { commentVm.postComment() },
            onStampPick = { stampId -> commentVm.postComment(stampId) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
