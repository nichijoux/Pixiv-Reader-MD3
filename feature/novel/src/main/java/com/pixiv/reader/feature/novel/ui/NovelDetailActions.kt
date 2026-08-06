package com.pixiv.reader.feature.novel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.database.entity.ReadingProgressEntity
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.novel.R

/** 操作区：阅读主按钮（AutoStories 图标）+ 4 个竖排卡片按钮（icon 上 / label 下）。 */
@Composable
internal fun NovelActions(
    novel: Novel,
    progress: ReadingProgressEntity?,
    isBookmarked: Boolean,
    isBookmarking: Boolean,
    isWatchlisted: Boolean,
    isWatchlisting: Boolean,
    downloading: Boolean,
    downloadProgress: String?,
    onBookmark: () -> Unit,
    onWatchlist: () -> Unit,
    onDownload: () -> Unit,
    onRead: () -> Unit,
    onComments: () -> Unit,
) {
    Column(modifier = Modifier.padding(Spacing.lg)) {
        val readLabel = if (progress != null && (progress.percentage ?: 0) > 0) {
            stringResource(R.string.novel_continue_reading, progress.percentage)
        } else {
            stringResource(R.string.novel_start_reading)
        }
        Button(onClick = onRead, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.Filled.AutoStories, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = readLabel,
                style = novelReadButtonStyle(),
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            VerticalActionButton(
                icon = if (isBookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                label = if (isBookmarked) stringResource(R.string.novel_bookmarked) else stringResource(R.string.novel_bookmark),
                active = isBookmarked,
                enabled = !isBookmarking,
                onClick = onBookmark,
                modifier = Modifier.weight(1f),
            )
            VerticalActionButton(
                icon = if (isWatchlisted) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                label = if (isWatchlisted) stringResource(R.string.novel_watchlisted) else stringResource(R.string.novel_watch),
                active = isWatchlisted,
                enabled = !isWatchlisting && novel.series?.id?.let { it > 0L } == true,
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

/** 竖排卡片按钮（HTML `.abtn`）：icon 上 / label 下，52dp 高，圆角 12，激活态 primaryContainer。 */
@Composable
internal fun VerticalActionButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(52.dp)
            .clip(AppShapes.card)
            .border(
                width = 1.dp,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = AppShapes.card,
            )
            .background(
                if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            // 图标始终主色（对齐 HTML `.abtn .ic{fill:var(--primary)}`），仅 disabled 置灰
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = novelSmallLabelStyle().copy(fontWeight = FontWeight.SemiBold),
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = NovelIconLabelGap),
        )
    }
}
