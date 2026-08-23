package com.pixiv.reader.feature.comments.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.feedback.toNotificationType
import com.pixiv.reader.core.ui.component.input.CommentInput
import com.pixiv.reader.feature.comments.R
import com.pixiv.reader.feature.comments.state.CommentListViewModel

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
    val stamps by viewModel.stamps.collectAsStateWithLifecycle()

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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        // 沉浸式：列表铺满到屏幕底部，输入条 + 回复目标条作为 overlay 浮在列表上方
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                // 首载：骨架占位（仿评论行布局），替代全屏转圈
                isLoading && comments.isEmpty() -> CommentSkeleton(Modifier.fillMaxSize())
                error != null && comments.isEmpty() -> ErrorBox(
                    message = error.orEmpty(),
                    onRetry = viewModel::loadComments,
                    modifier = Modifier.fillMaxSize(),
                )

                comments.isEmpty() -> EmptyBox(
                    text = stringResource(R.string.comment_empty),
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
            // 底部 overlay：回复目标条 + 输入条（align 到屏幕底，浮在列表上）
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
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
                    onPost = { viewModel.postComment() },
                    stamps = stamps,
                    onStampPick = { stamp -> viewModel.postComment(stamp.stamp_id) },
                    onEmojiPick = { tag -> viewModel.onCommentDraftChange(draft + tag) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}
