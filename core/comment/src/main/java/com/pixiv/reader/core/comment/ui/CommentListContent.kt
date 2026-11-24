package com.pixiv.reader.core.comment.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.Comment
import com.pixiv.reader.core.comment.R
import com.pixiv.reader.core.comment.state.CommentSortOrder
import com.pixiv.reader.core.common.config.BottomNavBarStyle
import com.pixiv.reader.core.ui.component.layout.LocalBottomNavBarStyle
import com.pixiv.reader.core.ui.R as UiR
import com.pixiv.reader.core.ui.component.card.UserAvatar
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.FeedPhase
import com.pixiv.reader.core.ui.component.feedback.feedPhase
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.SkeletonBlock
import com.pixiv.reader.core.ui.component.feedback.skeletonPulseColor
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.component.input.ConfirmDialog
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes

/**
 * 评论列表内容块（评论页与排行右栏评论区共用）：不含 Scaffold/TopAppBar，外壳由调用方决定；传入的都是 collect
 * 后的纯数据/回调（评论 VM 不进本组件）。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CommentListContent(
    comments: List<Comment>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    sortOrder: CommentSortOrder = CommentSortOrder.NEWEST_FIRST,
    error: String?,
    replies: Map<Long, List<Comment>>,
    repliesLoading: Set<Long>,
    expandedReplies: Set<Long>,
    draft: String,
    replyTarget: Comment?,
    stamps: List<com.pixiv.api.model.Stamp>,
    emptyText: String,
    onLoadComments: () -> Unit,
    onLoadMoreComments: () -> Unit,
    onOpenUser: (Long) -> Unit,
    onReply: (Comment, Long) -> Unit,
    onLoadReplies: (Long) -> Unit,
    onToggleRepliesExpanded: (Long) -> Unit,
    onDraftChange: (String) -> Unit,
    onPost: () -> Unit,
    onStampPick: (Long) -> Unit,
    ownUid: Long = 0L,
    onDeleteComment: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // 切换排序后列表整体翻转，滚回顶部避免视口内容跳变（首组合同样触发一次，无害）
    LaunchedEffect(sortOrder) { listState.scrollToItem(0) }
    // derivedStateOf 捕获的 lambda 须随 comments 重建：分页追加产生新列表实例，无 key 的 remember 会一直读首次
    // 组合的旧列表（lastIndex 恒旧，触底判定失效）
    val shouldLoadMore by remember(comments) {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= comments.lastIndex - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && hasMore && !isLoading && !isLoadingMore) {
            onLoadMoreComments()
        }
    }
    // 动效方案在组合期取好：item lambda 不是组合上下文，无法在其中读取 MaterialTheme
    val motionScheme = MaterialTheme.motionScheme
    val navBarStyle = LocalBottomNavBarStyle.current
    val inputBarPadding = if (navBarStyle == BottomNavBarStyle.FLOATING_CAPSULE) 84.dp else 76.dp

    Box(modifier = modifier.fillMaxSize()) {
        when (feedPhase(loading = isLoading, hasItems = comments.isNotEmpty(), hasError = error != null)) {
            FeedPhase.LOADING -> CommentSkeleton(Modifier.fillMaxSize())
            FeedPhase.ERROR -> ErrorBox(
                message = error,
                onRetry = onLoadComments,
                modifier = Modifier.fillMaxSize(),
            )

            FeedPhase.EMPTY -> EmptyBox(
                text = emptyText,
                modifier = Modifier.fillMaxSize(),
            )

            FeedPhase.CONTENT -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // 底部留出输入条与系统导航栏高度，最后一条评论可滚到输入条上方不被遮挡
                contentPadding = PaddingValues(
                    bottom = inputBarPadding + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
            ) {
                items(comments, key = { it.id }) { comment ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(
                                fadeInSpec = motionScheme.fastEffectsSpec<Float>(),
                                placementSpec = motionScheme.fastSpatialSpec<IntOffset>(),
                            ),
                    ) {
                        // 全宽列：保证分隔线横贯列表宽度
                        Column(modifier = Modifier.fillMaxWidth()) {
                            CommentRow(
                                comment = comment,
                                ownUid = ownUid,
                                onOpenUser = onOpenUser,
                                onReply = { target -> onReply(target, comment.id) },
                                onDeleteComment = onDeleteComment,
                                replies = replies[comment.id].orEmpty(),
                                repliesLoading = repliesLoading.contains(comment.id),
                                expanded = expandedReplies.contains(comment.id),
                                onLoadReplies = { onLoadReplies(comment.id) },
                                onToggleExpanded = { onToggleRepliesExpanded(comment.id) },
                            )
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                if (isLoadingMore) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            LoadingIndicator()
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
        // 底部 overlay：按 LocalBottomNavBarStyle 切换贴边停靠或悬浮胶囊
        CommentInput(
            draft = draft,
            onDraftChange = onDraftChange,
            onPost = onPost,
            stamps = stamps,
            onStampPick = { stamp -> onStampPick(stamp.stamp_id) },
            mentionName = replyTarget?.user?.name,
            style = navBarStyle,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * 头像 + 昵称 + 时间 + 正文 + 树形子回复（最多 3 条，超出可展开）+ 回复入口。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CommentRow(
    comment: Comment,
    ownUid: Long,
    onOpenUser: (Long) -> Unit,
    onReply: (Comment) -> Unit,
    onDeleteComment: ((Long) -> Unit)?,
    replies: List<Comment>,
    repliesLoading: Boolean,
    expanded: Boolean,
    onLoadReplies: () -> Unit,
    onToggleExpanded: () -> Unit,
) {
    val isMine = ownUid > 0L && comment.user?.id == ownUid
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 仅 isMine 时挂手势，他人评论保持无长按
            .let { base ->
                if (isMine && onDeleteComment != null) {
                    base.combinedClickable(
                        onClick = {},
                        onLongClick = { showDeleteConfirm = true },
                    )
                } else {
                    base
                }
            }
            .padding(horizontal = Spacing.lg, vertical = Spacing.smPlus),
        verticalAlignment = Alignment.Top,
    ) {
        UserAvatar(
            name = comment.user?.name,
            avatarUrl = comment.user?.profile_image_urls?.best(),
            modifier = Modifier
                .size(Sizes.s36)
                .clickable { comment.user?.id?.let(onOpenUser) },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.smPlus),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.user?.name ?: stringResource(R.string.comment_anonymous_user),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { comment.user?.id?.let(onOpenUser) },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatCommentDate(comment.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CommentText(
                text = comment.comment.orEmpty(),
                modifier = Modifier.padding(top = Spacing.xs),
            )
            // 贴纸固定 120dp 方形，比例保持
            comment.stamp?.stamp_url?.takeIf { it.isNotBlank() }?.let { stampUrl ->
                PixivImage(
                    url = stampUrl,
                    contentDescription = stringResource(R.string.comment_stamp_cd),
                    modifier = Modifier
                        .padding(top = Spacing.xsPlus)
                        .size(120.dp)
                        .clip(AppShapes.small),
                    contentScale = ContentScale.Fit,
                )
            }
            // 对齐 pixiv HTML `.replybtn`
            ReplyPill(
                text = stringResource(R.string.comment_reply),
                modifier = Modifier.padding(top = Spacing.xsPlus),
                onClick = { onReply(comment) },
            )
            // v3 列表只给 has_replies 标志，子回复按需拉取；未加载时自动触发加载
            if (comment.has_replies) {
                if (repliesLoading && replies.isEmpty()) {
                    // 加载中占位
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.sm)
                            .clip(AppShapes.card)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(Spacing.smPlus),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator()
                    }
                    LaunchedEffect(comment.id) { onLoadReplies() }
                } else if (replies.isNotEmpty()) {
                    // 最多显示 3 条；超出且未展开时显示「查看全部」入口
                    val showAll = expanded || replies.size <= MAX_VISIBLE_REPLIES
                    val visibleReplies = if (showAll) replies else replies.take(MAX_VISIBLE_REPLIES)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.sm)
                            .clip(AppShapes.card)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = Spacing.smPlus, vertical = Spacing.sm),
                    ) {
                        visibleReplies.forEachIndexed { index, reply ->
                            ReplyRow(
                                reply = reply,
                                isMine = ownUid > 0L && reply.user?.id == ownUid,
                                onReply = { onReply(reply) },
                                onDelete = onDeleteComment?.let { cb -> { cb(reply.id) } },
                            )
                            if (index != visibleReplies.lastIndex) {
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        if (!showAll && replies.size > MAX_VISIBLE_REPLIES) {
                            // 「查看全部 N 条回复」入口（点击展开，无收起）
                            Text(
                                text = stringResource(R.string.comment_reply_expand, replies.size),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = Spacing.xsPlus)
                                    .clip(AppShapes.small)
                                    .clickable(onClick = onToggleExpanded)
                                    .padding(vertical = Spacing.xxs),
                            )
                        }
                    }
                } else {
                    // has_replies 但尚未加载 → 触发加载
                    LaunchedEffect(comment.id) { onLoadReplies() }
                }
            }
        }
    }
    if (showDeleteConfirm && onDeleteComment != null) {
        ConfirmDialog(
            title = stringResource(R.string.comment_delete_title),
            message = stringResource(R.string.comment_delete_message),
            confirmText = stringResource(UiR.string.common_delete),
            onConfirm = {
                showDeleteConfirm = false
                onDeleteComment(comment.id)
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

private const val MAX_VISIBLE_REPLIES = 3

@Composable
private fun ReplyPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .clip(AppShapes.pill)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    )
}

/** 树形对话第二层：缩进浅色块内、带小头像、可回复；自己的评论长按可删除。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReplyRow(
    reply: Comment,
    isMine: Boolean,
    onReply: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 自己的子回复：长按弹删除确认
            .let { base ->
                if (isMine && onDelete != null) {
                    base.combinedClickable(
                        onClick = {},
                        onLongClick = { showDeleteConfirm = true },
                    )
                } else {
                    base
                }
            },
        verticalAlignment = Alignment.Top,
    ) {
        UserAvatar(
            name = reply.user?.name,
            avatarUrl = reply.user?.profile_image_urls?.best(),
            modifier = Modifier.size(Sizes.s28),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = reply.user?.name ?: stringResource(R.string.comment_anonymous_user),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
                stringResource(R.string.comment_reply_prefix, parentName)
            } else {
                null
            }
            CommentText(
                text = buildString {
                    if (prefix != null) append(prefix)
                    append(reply.comment.orEmpty())
                },
                modifier = Modifier.padding(top = Spacing.xxs),
            )
            // 贴纸子回复同样渲染
            reply.stamp?.stamp_url?.takeIf { it.isNotBlank() }?.let { stampUrl ->
                PixivImage(
                    url = stampUrl,
                    contentDescription = stringResource(R.string.comment_stamp_cd),
                    modifier = Modifier
                        .padding(top = Spacing.xsPlus)
                        .size(120.dp)
                        .clip(AppShapes.small),
                    contentScale = ContentScale.Fit,
                )
            }
            // 回复入口（可回复子评论）
            ReplyPill(
                text = stringResource(R.string.comment_reply),
                modifier = Modifier.padding(top = Spacing.xs),
                onClick = onReply,
            )
        }
    }
    if (showDeleteConfirm && onDelete != null) {
        ConfirmDialog(
            title = stringResource(R.string.comment_delete_title),
            message = stringResource(R.string.comment_delete_message),
            confirmText = stringResource(UiR.string.common_delete),
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

/** `(xxx)` 文本表情 → 行内 24dp 小图（对齐 pixiv 网页），未命中保持原文本。 */
@Composable
private fun CommentText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val contentDescription = stringResource(R.string.comment_emoji_cd)
    val (annotated, inlineContent) = remember(text, contentDescription) {
        buildEmojiAnnotatedString(text, contentDescription)
    }
    Text(
        text = annotated,
        inlineContent = inlineContent,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/** 评论时间为 ISO 格式，取日期部分 yyyy-MM-dd。 */
private fun formatCommentDate(date: String?): String = date?.take(10) ?: ""

@Composable
private fun CommentSkeleton(modifier: Modifier = Modifier) {
    val color = skeletonPulseColor(label = "commentSkeleton")
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(count = 8) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.smPlus),
                verticalAlignment = Alignment.Top,
            ) {
                SkeletonBlock(Modifier.size(Sizes.s36).clip(CircleShape), color)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Spacing.smPlus),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkeletonBlock(
                            modifier = Modifier.fillMaxWidth(0.4f).height(14.dp).clip(AppShapes.tiny),
                            color = color,
                        )
                        Spacer(Modifier.weight(1f))
                        SkeletonBlock(
                            modifier = Modifier.width(48.dp).height(10.dp).clip(AppShapes.tiny),
                            color = color,
                        )
                    }
                    SkeletonBlock(
                        modifier = Modifier.padding(top = Spacing.sm).fillMaxWidth(0.9f).height(12.dp).clip(AppShapes.tiny),
                        color = color,
                    )
                    SkeletonBlock(
                        modifier = Modifier.padding(top = Spacing.xsPlus).fillMaxWidth(0.65f).height(12.dp).clip(AppShapes.tiny),
                        color = color,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
