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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.comment.CommentListContent
import com.pixiv.reader.feature.comments.R
import com.pixiv.reader.core.network.comment.CommentListViewModel

/**
 * 通用评论列表页（novel / illust 共用，路由 `comments/{type}/{targetId}`）。
 *
 * 全屏页 + TopAppBar；`PagedState<Comment>` 分页（触底加载更多）；
 * 支持回复（点回复后输入框预填 @昵称 渲染为胶囊，一次退格删除即取消；`parent_comment_id` 发子评论）；
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
    // 输入框内的回复胶囊被退格删除（草稿失去 @前缀）时联动取消回复态；
    // 设置回复目标时 VM 预填的 "@昵称 " 前缀不会误触发
    LaunchedEffect(replyTarget, draft) {
        val name = replyTarget?.user?.name.orEmpty()
        // 空白昵称的回复目标无 @前缀，跳过联动避免误取消
        if (replyTarget != null && name.isNotBlank() && !draft.startsWith("@$name ")) {
            viewModel.setReplyTarget(null, null)
        }
    }

    val notificationHostState = rememberNotificationHostState()
    UiMessageEffect(viewModel.message, notificationHostState)

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
            emptyText = stringResource(R.string.comment_empty),
            onLoadComments = viewModel::loadComments,
            onLoadMoreComments = viewModel::loadMoreComments,
            onOpenUser = onOpenUser,
            onReply = { target, topId -> viewModel.setReplyTarget(target, topId) },
            onLoadReplies = viewModel::loadReplies,
            onToggleRepliesExpanded = viewModel::toggleRepliesExpanded,
            onDraftChange = viewModel::onCommentDraftChange,
            onPost = { viewModel.postComment() },
            onStampPick = { stampId -> viewModel.postComment(stampId) },
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}
