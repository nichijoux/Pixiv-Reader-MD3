package com.pixiv.reader.feature.comments.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.Comment
import com.pixiv.reader.core.ui.component.PixivImage
import com.pixiv.reader.core.ui.component.UserAvatar
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.feature.comments.R

/** 评论行：头像 + 昵称 + 时间 + 正文 + 树形子回复（最多 3 条，超出可展开）+ 回复入口。 */
@Composable
internal fun CommentRow(
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
            CommentText(
                text = comment.comment.orEmpty(),
                modifier = Modifier.padding(top = 4.dp),
            )
            // 贴纸（stamp）：正文下方渲染，固定 120dp 方形，比例保持
            comment.stamp?.stamp_url?.takeIf { it.isNotBlank() }?.let { stampUrl ->
                PixivImage(
                    url = stampUrl,
                    contentDescription = stringResource(R.string.comment_stamp_cd),
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(120.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                )
            }
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
internal fun ReplyPill(
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
internal fun ReplyRow(
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
            CommentText(
                text = buildString {
                    if (prefix != null) append(prefix)
                    append(reply.comment.orEmpty())
                },
                modifier = Modifier.padding(top = 2.dp),
            )
            // 贴纸（stamp）：子回复同样渲染
            reply.stamp?.stamp_url?.takeIf { it.isNotBlank() }?.let { stampUrl ->
                PixivImage(
                    url = stampUrl,
                    contentDescription = stringResource(R.string.comment_stamp_cd),
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(120.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                )
            }
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
internal fun formatCommentDate(date: String?): String = date?.take(10) ?: ""
