package com.pixiv.reader.core.ui.component.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.network.illust.IllustViewModel
import com.pixiv.reader.core.ui.component.feedback.ErrorBox

/**
 * Master-Detail 右栏详情 pane：评论 UI 经 [comments] 槽位注入以避免 core:ui 反向依赖 core:comment；
 * 内嵌评论的 BackHandler 先于外层关闭 pane 触发（导航链：评论 → 详情 → 列表）。
 */
@Composable
fun IllustDetailPane(
    selectedId: Long?,
    strings: IllustDetailStrings,
    placeholder: String,
    onOpenUser: (Long) -> Unit,
    onOpenViewer: (Long, Int) -> Unit,
    comments: @Composable (onBackToDetail: () -> Unit) -> Unit,
    onOpenComments: () -> Unit,
    viewModel: IllustViewModel,
    onSearchTag: (String) -> Unit = {},
) {
    val currentId = selectedId
    LaunchedEffect(currentId) {
        if (currentId != null) viewModel.switchTo(currentId)
    }
    // 在 pane 层单次消费种子再下发，避免与下游争抢（consume 取出即删）
    val heroSeed = remember(currentId) {
        currentId?.let { HeroSeedStore.consume(illustCoverKey(it)) }
    }
    // 命中缓存未消费或切卡销毁时兜底清理残留种子，防进程内无界增长
    DisposableEffect(currentId) {
        onDispose {
            if (currentId != null) HeroSeedStore.clear(illustCoverKey(currentId))
        }
    }
    var showComments by remember { mutableStateOf(false) }
    BackHandler(enabled = showComments) { showComments = false }

    val illust by viewModel.illust.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val ugoiraFrames by viewModel.ugoiraFrames.collectAsStateWithLifecycle()
    val ugoiraProgress by viewModel.ugoiraProgress.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val bookmarkState by viewModel.bookmarkState.collectAsStateWithLifecycle()
    val isBookmarked = bookmarkState.isOn
    val isBookmarking = bookmarkState.inFlight
    val authorFollowState by viewModel.authorFollowState.collectAsStateWithLifecycle()
    val isAuthorFollowed = authorFollowState.isOn
    val isAuthorFollowing = authorFollowState.inFlight
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

            isLoading && illust == null -> IllustDetailSkeleton()

            error != null && illust == null -> ErrorBox(
                message = error?.let { stringResource(it.res, *it.args.toTypedArray()) },
                onRetry = viewModel::load,
            )

            showComments -> comments { showComments = false }

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
                onSearchTag = onSearchTag,
                isAuthorFollowed = isAuthorFollowed,
                isAuthorFollowing = isAuthorFollowing,
                onToggleFollowAuthor = viewModel::toggleFollowAuthor,
                isBookmarked = isBookmarked,
                isBookmarking = isBookmarking,
                onToggleBookmark = viewModel::toggleBookmark,
                onDownload = viewModel::download,
                onOpenComments = {
                    showComments = true
                    onOpenComments()
                },
                seed = heroSeed,
                heroIllustId = currentId,
            )
        }
    }
}
