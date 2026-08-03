package com.pixiv.reader.feature.novel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pixivapi.model.Comment
import com.example.pixivapi.model.Novel
import com.pixiv.reader.core.common.MAX_CONTENT_WIDTH_DP
import com.pixiv.reader.core.common.formatCount
import com.pixiv.reader.core.novel.htmlToPlainText
import com.pixiv.reader.core.ui.component.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.EmptyBox
import com.pixiv.reader.core.ui.component.ErrorBox
import com.pixiv.reader.core.ui.component.LoadingBox
import com.pixiv.reader.core.ui.component.PixivImage
import com.pixiv.reader.core.ui.component.UserAvatar

/**
 * 小说 Tab：推荐流（P4）。
 */
@Composable
fun NovelRoute(
    onOpenNovel: (Long) -> Unit,
    viewModel: NovelFeedViewModel = hiltViewModel(),
) {
    val items by viewModel.feed.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.feed.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.feed.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.feed.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.feed.error.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // 滚动接近底部时自动加载下一页
    LaunchedEffect(listState, items.size, hasMore) {
        if (!hasMore || items.isEmpty()) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                if (lastVisible >= items.size - 3) viewModel.loadMore()
            }
    }

    AdaptiveContentBox {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("小说", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                }
            }
            when {
                isLoading && items.isEmpty() -> LoadingBox()
                error != null && items.isEmpty() -> ErrorBox(message = error.orEmpty(), onRetry = viewModel::refresh)
                items.isEmpty() -> EmptyBox("暂时没有推荐小说")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items, key = { it.id }) { novel ->
                        NovelCard(novel = novel, onClick = { onOpenNovel(novel.id) })
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
                                        text = "上滑加载更多",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { viewModel.loadMore() }
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
    viewModel: NovelViewModel = hiltViewModel(),
) {
    val novel by viewModel.novel.collectAsStateWithLifecycle()
    val seriesNovels by viewModel.seriesNovels.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val commentsLoading by viewModel.commentsLoading.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val isBookmarking by viewModel.isBookmarking.collectAsStateWithLifecycle()
    val isWatchlisted by viewModel.isWatchlisted.collectAsStateWithLifecycle()
    val isWatchlisting by viewModel.isWatchlisting.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.message.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        when {
            isLoading && novel == null -> LoadingBox()
            error != null && novel == null -> ErrorBox(message = error.orEmpty(), onRetry = viewModel::load)
            novel == null -> EmptyBox("没有找到该小说")
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
                    comments = comments,
                    commentsLoading = commentsLoading,
                    onBack = onBack,
                    onOpenNovel = onOpenNovel,
                    onOpenReader = onOpenReader,
                    onBookmark = viewModel::toggleBookmark,
                    onWatchlist = viewModel::toggleWatchlist,
                    onRetryComments = viewModel::loadComments,
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
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
    comments: List<Comment>,
    commentsLoading: Boolean,
    onBack: () -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenReader: (Long) -> Unit,
    onBookmark: () -> Unit,
    onWatchlist: () -> Unit,
    onRetryComments: () -> Unit,
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
                NovelCenteredBox { NovelHeader(detail) }
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
                        onBookmark = onBookmark,
                        onWatchlist = onWatchlist,
                        onRead = { onOpenReader(detail.id) },
                    )
                }
            }
            if (seriesNovels.isNotEmpty()) {
                item(key = "series_title") {
                    NovelCenteredBox {
                        Text(
                            text = "系列分册（${seriesNovels.size}）",
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
                    contentDescription = "返回",
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

/** 评论区（第一页主评论）。 */
@Composable
private fun CommentsSection(
    comments: List<Comment>,
    loading: Boolean,
    onRetry: () -> Unit,
) {
    // 注意：必须有统一根容器（Column）。
    // CommentsSection 被包在 NovelCenteredBox 的 Box 里，Box 子项默认堆叠，
    // 若直接 emit 多个并列 composable（标题 + 各评论行）会全部重叠。
    Column {
        Text(
            text = "评论（${comments.size}）",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        when {
            loading && comments.isEmpty() -> Text(
                text = "评论加载中…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )

            comments.isEmpty() -> Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "暂无评论",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "点击重试",
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
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
                    text = comment.user?.name ?: "匿名用户",
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
        }
    }
}

/** pixiv 评论时间为 ISO 格式，取日期部分 yyyy-MM-dd。 */
private fun formatCommentDate(date: String?): String = date?.take(10) ?: ""

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NovelHeader(novel: Novel) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = novel.title.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.padding(top = 12.dp),
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
            NovelStatText("字数", formatCountForNovel(novel.text_length ?: 0))
            NovelStatText("收藏", formatCount((novel.total_bookmarks ?: 0).toLong()))
            NovelStatText("浏览", formatCount((novel.total_view ?: 0).toLong()))
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
    onBookmark: () -> Unit,
    onWatchlist: () -> Unit,
    onRead: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        val readLabel = if (progress != null && (progress.percentage ?: 0) > 0) {
            "继续阅读 ${progress.percentage}%"
        } else {
            "开始阅读"
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
                Text(if (isBookmarked) "已收藏" else "收藏", modifier = Modifier.padding(start = 4.dp))
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
                Text(if (isWatchlisted) "已追更" else "追更", modifier = Modifier.padding(start = 4.dp))
            }
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
                    text = "字数 ${formatCountForNovel(novel.text_length ?: 0)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "收藏 ${formatCount((novel.total_bookmarks ?: 0).toLong())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isCurrent) {
            Text(
                text = "当前",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
