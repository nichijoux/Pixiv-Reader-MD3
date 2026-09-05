package com.pixiv.reader.feature.novel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.network.comment.CommentListViewModel
import com.pixiv.reader.core.network.novel.NovelViewModel
import com.pixiv.reader.core.ui.component.comment.CommentPane
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.feature.novel.data.NovelExportFormat

/**
 * 小说详情 pane（小说 Tab Master-Detail 右栏）。
 *
 * 复用 [NovelViewModel]（调用方 `hiltViewModel()` 注入）——选中项变化时 [switchTo] 加载；
 * 系列目录内分册点击原地替换。详情内容复用 [NovelDetailContent]
 * （`forceSingleColumn=true` 单栏，避免 pane 宽度下双栏挤压）+ [NovelActionBar] 操作行
 * + 下载格式弹窗（复用详情页 [DownloadSheet]）。
 * 关闭按钮复用 [NovelDetailContent] 内置的左上角悬浮返回按钮（[onClose]），无独立顶部条。
 *
 * @param selectedId 当前选中小说 id（null = 未选中，显示 [placeholder]）
 * @param placeholder 未选中时的占位提示文案
 * @param onClose 关闭 pane 回调（同时是内置悬浮返回按钮的点击）
 * @param onOpenReader 打开阅读器（全屏路由）
 * @param onOpenUser 点击作者打开用户主页（全屏路由）
 * @param onOpenSeries 打开系列页（全屏路由）
 * @param commentVm 评论 ViewModel（调用方注入；进入评论区时按当前小说 switchTo）
 * @param viewModel 小说详情 ViewModel（调用方注入）
 */
@Composable
internal fun NovelDetailPane(
    selectedId: Long?,
    placeholder: String,
    onClose: () -> Unit,
    onOpenReader: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    commentVm: CommentListViewModel,
    viewModel: NovelViewModel,
) {
    val currentId = selectedId
    // 选中项变化时加载详情（幂等：同 id 不重载；排行右栏同款模式）
    LaunchedEffect(currentId) {
        if (currentId != null) viewModel.switchTo(currentId)
    }
    // 评论区开关：右栏内嵌（详情 ↔ 评论切换，不跳全屏）
    var showComments by remember { mutableStateOf(false) }
    // 返回键导航：评论 → 详情（先于外层 pane 关闭的 BackHandler 触发）
    BackHandler(enabled = showComments) { showComments = false }
    val novel by viewModel.novel.collectAsStateWithLifecycle()
    val seriesNovels by viewModel.seriesNovels.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val isBookmarking by viewModel.isBookmarking.collectAsStateWithLifecycle()
    val isWatchlisted by viewModel.isWatchlisted.collectAsStateWithLifecycle()
    val isWatchlisting by viewModel.isWatchlisting.collectAsStateWithLifecycle()
    val isAuthorFollowed by viewModel.isAuthorFollowed.collectAsStateWithLifecycle()
    val isAuthorFollowing by viewModel.isAuthorFollowing.collectAsStateWithLifecycle()
    val downloading by viewModel.downloading.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    // 下载格式弹窗（复用详情页 DownloadSheet）
    var showDownloadDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        when {
            currentId == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            isLoading && novel == null -> LoadingBox()
            error != null && novel == null -> ErrorBox(
                message = error?.let { stringResource(it.res, *it.args.toTypedArray()) },
                onRetry = viewModel::load,
            )

            else -> {
                val detail = novel
                if (detail != null) {
                    if (showComments) {
                        // 评论区：右栏内嵌（详情 ↔ 评论切换，不跳全屏）
                        CommentPane(
                            commentVm = commentVm,
                            onOpenUser = onOpenUser,
                            onBackToDetail = { showComments = false },
                        )
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            NovelDetailContent(
                                detail = detail,
                                seriesNovels = seriesNovels,
                                progress = progress,
                                isAuthorFollowed = isAuthorFollowed,
                                isAuthorFollowing = isAuthorFollowing,
                                downloading = downloading,
                                downloadProgress = downloadProgress,
                                // 复用 NovelDetailContent 内置悬浮返回按钮（左上角）关闭 pane
                                onBack = onClose,
                                onOpenNovel = viewModel::switchTo,
                                onOpenReader = onOpenReader,
                                onOpenUser = onOpenUser,
                                onOpenSeries = onOpenSeries,
                                onToggleFollowAuthor = viewModel::toggleFollowAuthor,
                                modifier = Modifier.weight(1f),
                                forceSingleColumn = true,
                            )
                            NovelActionBar(
                                seriesId = detail.series?.id,
                                isBookmarked = isBookmarked,
                                isBookmarking = isBookmarking,
                                isWatchlisted = isWatchlisted,
                                isWatchlisting = isWatchlisting,
                                downloading = downloading,
                                onBookmark = viewModel::toggleBookmark,
                                onWatchlist = viewModel::toggleWatchlist,
                                onDownload = { showDownloadDialog = true },
                                onComments = {
                                    showComments = true
                                    commentVm.switchTo("novel", currentId)
                                },
                                // pane 底部与左栏列表共用外层 Scaffold 的导航栏避让，
                                // 不再重复避让，保证四个操作按钮与左栏底部对齐
                                navigationBarInset = false,
                            )
                            // 下载格式选择弹窗（复用详情页 DownloadSheet）
                            if (showDownloadDialog) {
                                DownloadSheet(
                                    config = DownloadSheetConfig.Detail(detail.series?.id?.let { it > 0L } == true),
                                    onFormat = { format: NovelExportFormat, scope: NovelDownloadScope, _: List<Long> ->
                                        viewModel.export(format.name, scope == NovelDownloadScope.SERIES)
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
}
