package com.pixiv.reader.core.ui.component.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.network.comment.CommentListViewModel
import com.pixiv.reader.core.network.illust.IllustViewModel
import com.pixiv.reader.core.ui.component.comment.CommentPane
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.LoadingBox

/**
 * 插画详情 pane（Master-Detail 右栏，首页 / 作品页共用）。
 *
 * 复用 [IllustViewModel]（调用方 `hiltViewModel()` 注入，core:ui 不依赖 hilt）——
 * 选中项变化时 [switchTo] 加载；详情内相关作品点击原地替换；三态自管。
 * 评论内嵌：点评论按钮切到 [CommentPane]（右栏限定，不开全屏页），返回键/顶部返回条
 * 切回详情；再按返回键关闭 pane（BackHandler 由外层 [ListDetailOverlay] 处理，
 * 本层注册的 BackHandler 先于外层触发——导航链：评论 → 详情 → 列表）。
 * 无内建关闭按钮：pane 关闭由系统返回 / 外层关闭入口承担。
 *
 * @param selectedId 当前选中作品 id（null = 未选中，显示 [placeholder]）
 * @param strings 详情块文案（调用方模块提供，避免 core:ui 引用 feature 资源）
 * @param placeholder 未选中时的占位提示文案
 * @param onOpenUser 点击作者打开用户主页（全屏路由）
 * @param onOpenViewer 点击图片打开全屏查看器（全屏路由，参数为作品 id + 页码）
 * @param commentVm 评论 ViewModel（调用方注入；进入评论区时按当前作品 switchTo）
 * @param viewModel 插画详情 ViewModel（调用方注入）
 */
@Composable
fun IllustDetailPane(
    selectedId: Long?,
    strings: IllustDetailStrings,
    placeholder: String,
    onOpenUser: (Long) -> Unit,
    onOpenViewer: (Long, Int) -> Unit,
    commentVm: CommentListViewModel,
    viewModel: IllustViewModel,
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

    val illust by viewModel.illust.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val ugoiraFrames by viewModel.ugoiraFrames.collectAsStateWithLifecycle()
    val ugoiraProgress by viewModel.ugoiraProgress.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val isBookmarking by viewModel.isBookmarking.collectAsStateWithLifecycle()
    val isAuthorFollowed by viewModel.isAuthorFollowed.collectAsStateWithLifecycle()
    val isAuthorFollowing by viewModel.isAuthorFollowing.collectAsStateWithLifecycle()
    val relatedItems by viewModel.relatedPaged.items.collectAsStateWithLifecycle()

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

            isLoading && illust == null -> LoadingBox()
            error != null && illust == null -> ErrorBox(
                message = error?.let { stringResource(it.res, *it.args.toTypedArray()) },
                onRetry = viewModel::load,
            )

            showComments -> CommentPane(
                commentVm = commentVm,
                onOpenUser = onOpenUser,
                onBackToDetail = { showComments = false },
            )

            else -> IllustDetailContent(
                illust = illust,
                pages = pages,
                ugoiraFrames = ugoiraFrames,
                ugoiraProgress = ugoiraProgress,
                relatedItems = relatedItems,
                strings = strings,
                onPageChange = {},
                onOpenViewer = { page -> onOpenViewer(currentId, page) },
                onOpenUser = onOpenUser,
                onOpenIllust = viewModel::switchTo,
                onSearchTag = { /* pane 内暂不跳搜索 */ },
                isAuthorFollowed = isAuthorFollowed,
                isAuthorFollowing = isAuthorFollowing,
                onToggleFollowAuthor = viewModel::toggleFollowAuthor,
                isBookmarked = isBookmarked,
                isBookmarking = isBookmarking,
                onToggleBookmark = viewModel::toggleBookmark,
                onDownload = viewModel::download,
                onOpenComments = {
                    showComments = true
                    commentVm.switchTo("illust", currentId)
                },
            )
        }
    }
}
