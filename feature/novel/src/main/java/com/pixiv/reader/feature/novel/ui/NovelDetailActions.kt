package com.pixiv.reader.feature.novel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.common.ui.MAX_CONTENT_WIDTH_DP
import com.pixiv.reader.core.ui.component.input.VerticalActionButton
import com.pixiv.reader.core.ui.theme.FavoriteRed
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.novel.R

/** 操作区（内容区）：阅读主按钮（AutoStories 图标）+ 下载进度；四个竖排按钮固定在底部见 [NovelActionBar]。 */
@Composable
internal fun NovelActions(
    downloading: Boolean,
    downloadProgress: String?,
    onRead: () -> Unit,
) {
    Column(modifier = Modifier.padding(Spacing.lg)) {
        val readLabel = stringResource(R.string.novel_start_reading)
        Button(
            onClick = onRead, modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(
                Icons.Filled.AutoStories,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = readLabel,
                style = novelReadButtonStyle(),
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        if (downloading && !downloadProgress.isNullOrBlank()) {
            Text(
                text = downloadProgress,
                style = novelMetaStyle(),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * 底部固定操作条：收藏 / 追更 / 下载 / 评论 四个竖排 icon+文字按钮（对齐插画详情页风格，
 * 复用 core:ui [VerticalActionButton]），Scaffold 外 Box 底部固定，限宽居中（平板与内容对齐）。
 */
@Composable
internal fun NovelActionBar(
    seriesId: Long?,
    isBookmarked: Boolean,
    isBookmarking: Boolean,
    isWatchlisted: Boolean,
    isWatchlisting: Boolean,
    downloading: Boolean,
    onBookmark: () -> Unit,
    onWatchlist: () -> Unit,
    onDownload: () -> Unit,
    onComments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = MAX_CONTENT_WIDTH_DP.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            VerticalActionButton(
                icon = if (isBookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                label = if (isBookmarked) stringResource(R.string.novel_bookmarked) else stringResource(
                    R.string.novel_bookmark
                ),
                active = isBookmarked,
                enabled = !isBookmarking,
                onClick = onBookmark,
                modifier = Modifier.weight(1f),
                // 收藏激活用红心（与插画详情页一致）
                activeIconTint = FavoriteRed,
            )
            VerticalActionButton(
                icon = if (isWatchlisted) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                label = if (isWatchlisted) stringResource(R.string.novel_watchlisted) else stringResource(
                    R.string.novel_watch
                ),
                active = isWatchlisted,
                enabled = !isWatchlisting && seriesId != null && seriesId > 0L,
                onClick = onWatchlist,
                modifier = Modifier.weight(1f),
            )
            VerticalActionButton(
                icon = Icons.Filled.Download,
                label = stringResource(R.string.novel_download),
                active = false,
                enabled = !downloading,
                onClick = onDownload,
                modifier = Modifier.weight(1f),
            )
            VerticalActionButton(
                icon = Icons.Filled.ModeComment,
                label = stringResource(R.string.novel_comment_button),
                active = false,
                enabled = true,
                onClick = onComments,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
