package com.pixiv.reader.core.ui.component.comment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.network.comment.CommentListViewModel
import com.pixiv.reader.core.ui.R

/**
 * 评论 pane（Master-Detail 右栏内嵌评论区，首页 / 作品页 / 小说页共用）。
 *
 * 复用 [CommentListViewModel]（调用方 `hiltViewModel()` 注入，core:ui 不依赖 hilt）——
 * 由调用方在进入评论区前 `switchTo(type, id)` 定位目标；顶部返回条切回详情。
 * 返回键导航（评论 → 详情 → 列表）由外层 pane 的 BackHandler 负责，本组件只管内容。
 *
 * @param commentVm 评论 ViewModel（调用方注入；进入前需已 [CommentListViewModel.switchTo]）
 * @param onOpenUser 点击评论者打开用户主页（全屏路由）
 * @param onBackToDetail 返回详情回调（顶部返回条；通常置外层 pane 的评论开关为 false）
 */
@Composable
fun CommentPane(
    commentVm: CommentListViewModel,
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
                text = stringResource(R.string.core_comment_back_to_detail),
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
            emptyText = stringResource(R.string.core_comment_empty),
            onLoadComments = commentVm::loadComments,
            onLoadMoreComments = commentVm::loadMoreComments,
            onOpenUser = onOpenUser,
            onReply = { target, topId -> commentVm.setReplyTarget(target, topId) },
            onLoadReplies = commentVm::loadReplies,
            onToggleRepliesExpanded = commentVm::toggleRepliesExpanded,
            onDraftChange = commentVm::onCommentDraftChange,
            onPost = { commentVm.postComment() },
            onStampPick = { stampId -> commentVm.postComment(stampId) },
        )
    }
}
