package com.pixiv.reader.core.ui.component.comment

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.Comment
import com.pixiv.reader.core.ui.R
import com.pixiv.reader.core.ui.component.card.UserAvatar
import com.pixiv.reader.core.ui.component.emoji.buildEmojiAnnotatedString
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.SkeletonBlock
import com.pixiv.reader.core.ui.component.feedback.skeletonPulseColor
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.component.input.CommentInput
import com.pixiv.reader.core.ui.component.input.ConfirmDialog
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes

/**
 * 评论列表内容块（core:ui 下沉，供 feature:comments 评论页与排行右栏评论区共用）。
 * 不含 Scaffold/TopAppBar——由调用方决定外壳（全屏页 / 右栏面板）。
 * 参数均为调用方 collect 后的纯数据/回调（评论 VM 不进本组件）。
 *
 * @param comments 评论列表（一级评论，按分页累积）
 * @param isLoading 首屏加载中
 * @param isLoadingMore 触底加载更多中
 * @param hasMore 是否还有下一页
 * @param error 首屏错误信息（null=无错误）
 * @param replies 回复树（评论 id → 该评论的回复列表）
 * @param repliesLoading 加载回复中的评论 id 集合
 * @param expandedReplies 已展开回复的评论 id 集合
 * @param draft 评论输入框草稿文本
 * @param replyTarget 当前回复目标（null=直接评论作品）
 * @param stamps 可选贴纸列表
 * @param emptyText 空态文案
 * @param onLoadComments 首次加载 / 失败重试回调
 * @param onLoadMoreComments 触底加载更多回调
 * @param onOpenUser 点击头像/用户名打开用户主页
 * @param onReply 点击回复（参数：目标评论 + 作品 id）
 * @param onLoadReplies 加载某评论的回复列表
 * @param onToggleRepliesExpanded 展开 / 收起某评论的回复
 * @param onDraftChange 草稿文本变化
 * @param onPost 发表评论 / 回复
 * @param onStampPick 选择贴纸（参数为贴纸 id）
 * @param ownUid 当前登录用户 id（>0 时自己的评论显示删除按钮；0=不显示）
 * @param onDeleteComment 删除自己的评论回调（null=不提供删除；参数为评论 id）
 * @param modifier 外部传入的 Modifier
 * @return 无返回值
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CommentListContent(
    comments: List<Comment>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
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
    // 触底加载更多：最后可见项接近列表末尾且仍有下一页时触发
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
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

    Box(modifier = modifier.fillMaxSize()) {
        when {
            // 首载：骨架占位（仿评论行布局），替代全屏转圈
            isLoading && comments.isEmpty() -> CommentSkeleton(Modifier.fillMaxSize())
            error != null && comments.isEmpty() -> ErrorBox(
                message = error,
                onRetry = onLoadComments,
                modifier = Modifier.fillMaxSize(),
            )

            comments.isEmpty() -> EmptyBox(
                text = emptyText,
                modifier = Modifier.fillMaxSize(),
            )

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // 底部留出输入条高度（约 72dp），最后一条评论可滚到输入条上方不被遮挡
                contentPadding = PaddingValues(bottom = 72.dp),
            ) {
                items(comments, key = { it.id }) { comment ->
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
        // 底部 overlay：回复目标条 + 输入条（align 到屏幕底，浮在列表上）
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                // 键盘弹出时随 ime insets 抬起（edge-to-edge 下 adjustResize 不再压缩窗口）；
                // 顺序：先导航栏后键盘，IME 打开时导航栏 inset 被消费，不会双重垫高
                .navigationBarsPadding()
                .imePadding(),
        ) {
            CommentInput(
                draft = draft,
                onDraftChange = onDraftChange,
                onPost = onPost,
                stamps = stamps,
                onStampPick = { stamp -> onStampPick(stamp.stamp_id) },
                mentionName = replyTarget?.user?.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.smPlus),
            )
        }
    }
}

/** 评论行：头像 + 昵称 + 时间 + 正文 + 树形子回复（最多 3 条，超出可展开）+ 回复入口。 */
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
    // 是否为自己的评论（判定删除入口 + 长按手势）
    val isMine = ownUid > 0L && comment.user?.id == ownUid
    // 删除确认框状态（自己的评论长按触发）
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 自己的评论：长按弹删除确认（仅 isMine 时挂手势，他人评论保持无手势）
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
            // 贴纸（stamp）：正文下方渲染，固定 120dp 方形，比例保持
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
            // 回复入口（胶囊，对齐 HTML `.replybtn`）
            ReplyPill(
                text = stringResource(R.string.comment_reply),
                modifier = Modifier.padding(top = Spacing.xsPlus),
                onClick = { onReply(comment) },
            )
            // 树形对话：父评论下方渲染子回复（浅色块 + 缩进）。
            // v3 列表只给 has_replies 标志，子回复按需拉取；未加载时自动触发加载。
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
                    // has_replies 但尚未加载（非加载中状态）→ 触发加载
                    LaunchedEffect(comment.id) { onLoadReplies() }
                }
            }
        }
    }
    // 删除确认框：确认后上抛 onDeleteComment（由调用方调 VM 删除并刷新）
    if (showDeleteConfirm && onDeleteComment != null) {
        ConfirmDialog(
            title = stringResource(R.string.comment_delete_title),
            message = stringResource(R.string.comment_delete_message),
            confirmText = stringResource(R.string.common_delete),
            onConfirm = {
                showDeleteConfirm = false
                onDeleteComment(comment.id)
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

/** 子回复未展开时的最大显示条数。 */
private const val MAX_VISIBLE_REPLIES = 3

/** 回复入口胶囊（对齐 HTML `.replybtn`：primary-container 底 + primary 字 + 圆角胶囊）。 */
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

/** 子评论行（树形对话第二层：缩进浅色块内、带小头像、可回复；自己的评论长按可删除）。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReplyRow(
    reply: Comment,
    isMine: Boolean,
    onReply: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    // 删除确认框状态（自己的子回复长按触发）
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
            // 贴纸（stamp）：子回复同样渲染
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
            // 回复入口（胶囊，可回复子评论）
            ReplyPill(
                text = stringResource(R.string.comment_reply),
                modifier = Modifier.padding(top = Spacing.xs),
                onClick = onReply,
            )
        }
    }
    // 删除确认框：确认后上抛 onDelete（由调用方调 VM 删除并刷新）
    if (showDeleteConfirm && onDelete != null) {
        ConfirmDialog(
            title = stringResource(R.string.comment_delete_title),
            message = stringResource(R.string.comment_delete_message),
            confirmText = stringResource(R.string.common_delete),
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

/** 评论正文渲染：`(xxx)` 文本表情 → 行内小图（24dp，对齐 pixiv 网页），未命中保持原文本。 */
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

/** pixiv 评论时间为 ISO 格式，取日期部分 yyyy-MM-dd。 */
private fun formatCommentDate(date: String?): String = date?.take(10) ?: ""

/** 评论列表加载骨架：仿评论行布局（36dp 圆头像 + 昵称/时间条 + 正文 2 行 + 分隔线）渲染 8 条。 */
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
