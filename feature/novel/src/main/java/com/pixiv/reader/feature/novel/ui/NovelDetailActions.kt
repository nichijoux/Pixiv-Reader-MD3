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
import com.pixiv.reader.core.ui.theme.Sizes
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
                modifier = Modifier.size(Sizes.s18)
            )
            Text(
                text = readLabel,
                style = novelReadButtonStyle(),
                modifier = Modifier.padding(start = Spacing.xsPlus),
            )
        }
        if (downloading && !downloadProgress.isNullOrBlank()) {
            Text(
                text = downloadProgress,
                style = novelMetaStyle(),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

/**
 * 底部固定操作条：收藏 / 追更 / 下载 / 评论 四个竖排 icon+文字按钮（对齐插画详情页风格，
 * 复用 core:ui [VerticalActionButton]），Scaffold 外 Box 底部固定，限宽居中（平板与内容对齐）。
 *
 * @param navigationBarInset 是否追加系统导航栏避让——Scaffold bottomBar 场景（手机全屏详情页）
 *   为 true；Master-Detail 右栏内嵌场景为 false（外层 Scaffold padding 已含导航栏避让，
 *   再加会导致操作条比左栏底部高出一个导航栏高度、无法对齐）
 * @param seriesId 所属系列 id（null/非正数表示无系列，追更按钮禁用）
 * @param isBookmarked 是否已收藏（决定收藏按钮图标与文案）
 * @param isBookmarking 收藏请求进行中（进行中禁用收藏按钮防连点）
 * @param isWatchlisted 是否已追更（决定追更按钮图标与文案）
 * @param isWatchlisting 追更请求进行中（进行中禁用追更按钮防连点）
 * @param downloading 下载进行中（进行中禁用下载按钮）
 * @param onBookmark 收藏/取消收藏回调
 * @param onWatchlist 追更/取消追更回调
 * @param onDownload 打开下载格式选择弹窗回调
 * @param onComments 打开评论区回调
 * @param modifier 外部传入的 Modifier
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
    navigationBarInset: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            // 导航栏避让按场景开关：bottomBar 场景自带，pane 内嵌场景由外层统一处理
            .then(if (navigationBarInset) Modifier.navigationBarsPadding() else Modifier),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = MAX_CONTENT_WIDTH_DP.dp)
                .padding(horizontal = Spacing.md, vertical = Spacing.smPlus),
            horizontalArrangement = Arrangement.spacedBy(Spacing.smPlus),
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
