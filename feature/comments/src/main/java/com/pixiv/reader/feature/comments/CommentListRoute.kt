package com.pixiv.reader.feature.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.Comment
import com.pixiv.reader.core.ui.component.CommentInput
import com.pixiv.reader.core.ui.component.EmptyBox
import com.pixiv.reader.core.ui.component.ErrorBox
import com.pixiv.reader.core.ui.component.NotificationHost
import com.pixiv.reader.core.ui.component.SkeletonBlock
import com.pixiv.reader.core.ui.component.UserAvatar
import com.pixiv.reader.core.ui.component.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.toNotificationType
import com.pixiv.reader.core.ui.component.skeletonPulseColor
import com.pixiv.reader.core.ui.theme.AppShapes

/**
 * 通用评论列表页（novel / illust 共用，路由 `comments/{type}/{targetId}`）。
 *
 * 全屏页 + TopAppBar；`PagedState<Comment>` 分页（触底加载更多）；
 * 支持回复（底部回复条 + 输入框预填 @昵称，`parent_comment_id` 发子评论）；
 * 点作者行进入用户主页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentListRoute(
    type: String,
    targetId: Long,
    onBack: () -> Unit,
    onOpenUser: (Long) -> Unit,
    viewModel: CommentListViewModel = hiltViewModel(),
) {
    val comments by viewModel.commentsPaged.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.commentsPaged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.commentsPaged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.commentsPaged.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.commentsPaged.error.collectAsStateWithLifecycle()
    val replies by viewModel.replies.collectAsStateWithLifecycle()
    val repliesLoading by viewModel.repliesLoading.collectAsStateWithLifecycle()
    val expandedReplies by viewModel.expandedReplies.collectAsStateWithLifecycle()
    val draft by viewModel.commentDraft.collectAsStateWithLifecycle()
    val replyTarget by viewModel.replyTarget.collectAsStateWithLifecycle()

    val notificationHostState = rememberNotificationHostState()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.message.collect { msg ->
            notificationHostState.show(context.getString(msg.res, *msg.args.toTypedArray()), type = msg.type.toNotificationType())
        }
    }

    val listState = rememberLazyListState()
    // 触底加载更多：最后可见项接近列表末尾且仍有下一页时触发
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= comments.lastIndex - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && hasMore && !isLoading && !isLoadingMore) {
            viewModel.loadMoreComments()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.comment_title, comments.size)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.comment_cd_back))
                    }
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding(),
            ) {
                if (replyTarget != null) {
                    // 回复目标条：显示 @昵称 + 取消
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.comment_reply_prefix, replyTarget?.user?.name.orEmpty()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { viewModel.setReplyTarget(null, null) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.comment_reply_cancel),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                CommentInput(
                    draft = draft,
                    onDraftChange = viewModel::onCommentDraftChange,
                    onPost = viewModel::postComment,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        },
    ) { padding ->
        when {
            // 首载：骨架占位（仿评论行布局），替代全屏转圈
            isLoading && comments.isEmpty() -> CommentSkeleton(Modifier.padding(padding))
            error != null && comments.isEmpty() -> ErrorBox(
                message = error.orEmpty(),
                onRetry = viewModel::loadComments,
                modifier = Modifier.padding(padding),
            )

            comments.isEmpty() -> EmptyBox(
                text = stringResource(R.string.comment_empty),
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(comments, key = { it.id }) { comment ->
                    CommentRow(
                        comment = comment,
                        onOpenUser = onOpenUser,
                        onReply = { target -> viewModel.setReplyTarget(target, comment.id) },
                        replies = replies[comment.id].orEmpty(),
                        repliesLoading = repliesLoading.contains(comment.id),
                        expanded = expandedReplies.contains(comment.id),
                        onLoadReplies = { viewModel.loadReplies(comment.id) },
                        onToggleExpanded = { viewModel.toggleRepliesExpanded(comment.id) },
                    )
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (isLoadingMore) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

/**
 * 评论列表加载骨架：仿 [CommentRow] 布局（36dp 圆头像 + 昵称/时间条 + 正文 2 行 + 分隔线）
 * 渲染 8 条，呼吸脉冲替代全屏转圈。
 */
@Composable
private fun CommentSkeleton(modifier: Modifier = Modifier) {
    val color = skeletonPulseColor(label = "commentSkeleton")
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(count = 8) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                SkeletonBlock(Modifier.size(36.dp).clip(CircleShape), color)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkeletonBlock(
                            modifier = Modifier.fillMaxWidth(0.4f).height(14.dp).clip(RoundedCornerShape(6.dp)),
                            color = color,
                        )
                        Spacer(Modifier.weight(1f))
                        SkeletonBlock(
                            modifier = Modifier.width(48.dp).height(10.dp).clip(RoundedCornerShape(6.dp)),
                            color = color,
                        )
                    }
                    SkeletonBlock(
                        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(0.9f).height(12.dp).clip(RoundedCornerShape(6.dp)),
                        color = color,
                    )
                    SkeletonBlock(
                        modifier = Modifier.padding(top = 6.dp).fillMaxWidth(0.65f).height(12.dp).clip(RoundedCornerShape(6.dp)),
                        color = color,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** 评论行：头像 + 昵称 + 时间 + 正文 + 树形子回复（最多 3 条，超出可展开）+ 回复入口。 */
@Composable
private fun CommentRow(
    comment: Comment,
    onOpenUser: (Long) -> Unit,
    onReply: (Comment) -> Unit,
    replies: List<Comment>,
    repliesLoading: Boolean,
    expanded: Boolean,
    onLoadReplies: () -> Unit,
    onToggleExpanded: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        UserAvatar(
            name = comment.user?.name,
            avatarUrl = comment.user?.profile_image_urls?.best(),
            modifier = Modifier
                .size(36.dp)
                .clickable { comment.user?.id?.let(onOpenUser) },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
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
            Text(
                text = comment.comment ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
            // 回复入口（胶囊，对齐 HTML `.replybtn`）
            ReplyPill(
                text = stringResource(R.string.comment_reply),
                modifier = Modifier.padding(top = 6.dp),
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
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                    LaunchedEffect(comment.id) { onLoadReplies() }
                } else if (replies.isNotEmpty()) {
                    // 最多显示 3 条；超出且未展开时显示「查看全部」入口
                    val showAll = expanded || replies.size <= MAX_VISIBLE_REPLIES
                    val visibleReplies = if (showAll) replies else replies.take(MAX_VISIBLE_REPLIES)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        visibleReplies.forEachIndexed { index, reply ->
                            ReplyRow(
                                reply = reply,
                                onReply = { onReply(reply) },
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
                                    .padding(top = 6.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onToggleExpanded)
                                    .padding(vertical = 2.dp),
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
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** 子评论行（树形对话第二层：缩进浅色块内、带小头像、可回复）。 */
@Composable
private fun ReplyRow(
    reply: Comment,
    onReply: () -> Unit,
) {
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
            Text(
                text = buildString {
                    if (prefix != null) append(prefix)
                    append(reply.comment.orEmpty())
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp),
            )
            // 回复入口（胶囊，可回复子评论）
            ReplyPill(
                text = stringResource(R.string.comment_reply),
                modifier = Modifier.padding(top = 4.dp),
                onClick = onReply,
            )
        }
    }
}

/** pixiv 评论时间为 ISO 格式，取日期部分 yyyy-MM-dd。 */
private fun formatCommentDate(date: String?): String = date?.take(10) ?: ""
