package com.pixiv.reader.feature.manga

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.common.ui.WindowSizeClass
import com.pixiv.reader.core.common.ui.classifyWindowWidth
import com.pixiv.reader.core.network.comment.CommentListViewModel
import com.pixiv.reader.core.network.illust.IllustViewModel
import com.pixiv.reader.core.ui.component.card.RankingIllustCard
import com.pixiv.reader.core.ui.component.comment.CommentListContent
import com.pixiv.reader.core.ui.component.detail.IllustDetailContent
import com.pixiv.reader.core.ui.component.detail.IllustDetailStrings
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentTitle
import com.pixiv.reader.core.ui.component.list.RankingIllustSkeleton
import com.pixiv.reader.core.ui.component.list.RankingList

/**
 * 漫画排行榜全屏页：分段 Tab + 左右滑动切换（复用通用 [RankingList]），排名列表行点击打开作品详情。
 *
 * 每段数据由 ViewModel 内独立 PagedState 承载（RankingList 按段 collect），滑动切回已加载段
 * 不重复请求、无过渡动画。
 *
 * @param onBack 返回
 * @param onOpenIllust 点击排名行打开插画/漫画详情
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaRankingRoute(
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenViewer: (Long, Int) -> Unit,
    viewModel: MangaRankingViewModel = hiltViewModel(),
) {
    val notificationHostState = rememberNotificationHostState()
    UiMessageEffect(viewModel.message, notificationHostState)
    // 平板（Expanded, 窗口 > 840dp）：双栏 list+detail；手机/小屏走单栏（原行为不变）
    val isExpanded = classifyWindowWidth(LocalConfiguration.current.screenWidthDp) == WindowSizeClass.Expanded
    var selected by remember { mutableStateOf<Illust?>(null) }
    // 右栏详情：内嵌 IllustViewModel（同一 backstack entry 作用域，与 MangaRankingViewModel 共存），
    // 选中项变化时 switchTo 换作品加载；无选中时不加载（init 无路由参数不预载）。
    val detailVm: IllustViewModel = hiltViewModel()
    // 右栏评论区：点评论按钮切换；内嵌 CommentListViewModel（同作用域），switchTo 换目标
    val commentVm: CommentListViewModel = hiltViewModel()
    var showComments by remember { mutableStateOf(false) }
    // 手势返回拦截：右栏显示评论区时，先退回详情（局部状态），不直接退出排行榜页
    BackHandler(enabled = showComments) { showComments = false }
    val detailStrings = IllustDetailStrings(
            loadRetry = stringResource(R.string.manga_illust_load_retry),
            fullscreen = stringResource(R.string.manga_illust_fullscreen),
            statView = stringResource(R.string.manga_illust_stat_view),
            statBookmark = stringResource(R.string.manga_illust_stat_bookmark),
            statPages = stringResource(R.string.manga_illust_stat_pages),
            expand = stringResource(R.string.manga_illust_expand),
            collapse = stringResource(R.string.manga_illust_collapse),
            follow = stringResource(R.string.manga_illust_follow),
            followed = stringResource(R.string.manga_illust_followed),
            related = stringResource(R.string.manga_illust_related),
            bookmark = stringResource(R.string.manga_illust_bookmark),
            bookmarked = stringResource(R.string.manga_illust_bookmarked),
            download = stringResource(R.string.manga_illust_download),
            comments = stringResource(R.string.manga_illust_comments),
    )
    LaunchedEffect(selected?.id) {
        selected?.id?.let(detailVm::switchTo)
    }

    Scaffold(
        snackbarHost = { NotificationHost(notificationHostState) },
        topBar = {
            TopAppBar(
                title = {
                    // 平板限宽居中（与下方 RankingList 内容对齐）
                    AdaptiveContentTitle(stringResource(R.string.manga_ranking_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.manga_cd_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* 更多（暂保留） */ }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.manga_cd_more),
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
                    emptyText = stringResource(R.string.manga_ranking_empty),
                    skeleton = { RankingIllustSkeleton() },
                ) { item, rank ->
                    RankingIllustCard(
                        rank = rank,
                        illust = item,
                        onClick = { selected = item },
                        onToggleFavorite = { fav -> viewModel.toggleIllustFavorite(item.id, fav) },
                        onOpenAuthor = { item.user?.id?.let(onOpenUser) },
                    )
                }
                // 中：分隔
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                // 右：详情 / 评论区（showComments 切换）
                Box(
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    val dIllust by detailVm.illust.collectAsStateWithLifecycle()
                    val dPages by detailVm.pages.collectAsStateWithLifecycle()
                    val dUgoiraFrames by detailVm.ugoiraFrames.collectAsStateWithLifecycle()
                    val dUgoiraProgress by detailVm.ugoiraProgress.collectAsStateWithLifecycle()
                    val dIsLoading by detailVm.isLoading.collectAsStateWithLifecycle()
                    val dError by detailVm.error.collectAsStateWithLifecycle()
                    val dBookmarked by detailVm.isBookmarked.collectAsStateWithLifecycle()
                    val dBookmarking by detailVm.isBookmarking.collectAsStateWithLifecycle()
                    val dFollowed by detailVm.isAuthorFollowed.collectAsStateWithLifecycle()
                    val dFollowing by detailVm.isAuthorFollowing.collectAsStateWithLifecycle()
                    val dRelated by detailVm.relatedPaged.items.collectAsStateWithLifecycle()
                    val currentId = selected?.id
                    when {
                        currentId == null -> Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.manga_ranking_preview_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        showComments -> RankingCommentsPane(
                            commentVm = commentVm,
                            currentId = currentId,
                            onOpenUser = onOpenUser,
                            onBackToDetail = { showComments = false },
                        )

                        dIsLoading && dIllust == null -> LoadingBox()
                        dError != null && dIllust == null -> ErrorBox(
                            message = dError?.let { stringResource(it.res, *it.args.toTypedArray()) },
                            onRetry = detailVm::load,
                        )

                        else -> IllustDetailContent(
                            illust = dIllust,
                            pages = dPages,
                            ugoiraFrames = dUgoiraFrames,
                            ugoiraProgress = dUgoiraProgress,
                            relatedItems = dRelated,
                            strings = detailStrings,
                            onPageChange = {},
                            onOpenViewer = { page -> onOpenViewer(dIllust?.id ?: currentId, page) },
                            onOpenUser = onOpenUser,
                            onOpenIllust = { id ->
                                detailVm.switchTo(id)
                                showComments = false
                            },
                            onSearchTag = { /* 右栏暂不跳搜索 */ },
                            isAuthorFollowed = dFollowed,
                            isAuthorFollowing = dFollowing,
                            onToggleFollowAuthor = detailVm::toggleFollowAuthor,
                            isBookmarked = dBookmarked,
                            isBookmarking = dBookmarking,
                            onToggleBookmark = detailVm::toggleBookmark,
                            onDownload = detailVm::download,
                            onOpenComments = {
                                showComments = true
                                commentVm.switchTo("illust", currentId)
                            },
                            expandableIntro = true,
                        )
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
                emptyText = stringResource(R.string.manga_ranking_empty),
                skeleton = { RankingIllustSkeleton() },
            ) { item, rank ->
                RankingIllustCard(
                    rank = rank,
                    illust = item,
                    onClick = { onOpenIllust(item.id) },
                    onToggleFavorite = { fav -> viewModel.toggleIllustFavorite(item.id, fav) },
                    onOpenAuthor = { item.user?.id?.let(onOpenUser) },
                )
            }
        }
    }
}

/**
 * 排行右栏评论区面板：顶部返回条（切回详情）+ 评论列表（内嵌 CommentListViewModel）。
 * 左栏保持排行榜列表不变（用户要求：评论区占右栏，左栏仍是排行榜）。
 */
@Composable
internal fun RankingCommentsPane(
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
                text = stringResource(R.string.manga_ranking_back_to_detail),
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
            error = error,
            replies = replies,
            repliesLoading = repliesLoading,
            expandedReplies = expandedReplies,
            draft = draft,
            replyTarget = replyTarget,
            stamps = stamps,
            emptyText = stringResource(R.string.manga_ranking_comment_empty),
            onLoadComments = commentVm::loadComments,
            onLoadMoreComments = commentVm::loadMoreComments,
            onOpenUser = { userId -> onOpenUser(userId) },
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