package com.pixiv.reader.feature.novel.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.common.ui.MAX_CONTENT_WIDTH_DP
import com.pixiv.reader.core.ui.R as CoreUiR
import com.pixiv.reader.core.ui.component.feedback.PixivHaptics
import com.pixiv.reader.core.ui.component.feedback.rememberHapticCue
import com.pixiv.reader.core.ui.component.input.VerticalActionButton
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.core.ui.theme.extendedColors
import com.pixiv.reader.feature.novel.R

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

/** 私密收藏经长按菜单设置。 */
enum class BookmarkPrivacy {
    NONE,
    PUBLIC,
    PRIVATE,
}

@Composable
internal fun NovelActionBar(
    novelId: Long,
    seriesId: Long?,
    privacy: BookmarkPrivacy,
    isBookmarking: Boolean,
    isWatchlisted: Boolean,
    isWatchlisting: Boolean,
    downloading: Boolean,
    onBookmark: () -> Unit,
    onBookmarkLongClick: (() -> Unit)? = null,
    onWatchlist: () -> Unit,
    onDownload: () -> Unit,
    onComments: () -> Unit,
    navigationBarInset: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // 收藏心形弹跳：0.6 → 过冲 → 1 的 pop
    val favoriteBounce = remember { Animatable(1f) }
    // key 为小说 id：pane 不按 id 重建组合，不重置会跨作品误播弹跳与触觉
    var wasBookmarked by remember(novelId) { mutableStateOf(privacy != BookmarkPrivacy.NONE) }
    val haptic = rememberHapticCue()
    LaunchedEffect(privacy) {
        val bookmarked = privacy != BookmarkPrivacy.NONE
        val becameBookmarked = bookmarked && !wasBookmarked
        wasBookmarked = bookmarked
        if (becameBookmarked) {
            haptic(PixivHaptics.HapticCue.SUCCESS)
            favoriteBounce.snapTo(0.6f)
            favoriteBounce.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            // 导航栏避让按场景开关：bottomBar 场景自带，pane 内嵌场景由外层统一处理，否则会多出一圈导航栏高度
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
                icon = if (privacy != BookmarkPrivacy.NONE) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                label = when (privacy) {
                    // 私密收藏文案在 core:ui
                    BookmarkPrivacy.PRIVATE -> stringResource(CoreUiR.string.bookmark_private_cd)
                    BookmarkPrivacy.PUBLIC -> stringResource(R.string.novel_bookmarked)
                    BookmarkPrivacy.NONE -> stringResource(R.string.novel_bookmark)
                },
                active = privacy != BookmarkPrivacy.NONE,
                enabled = !isBookmarking,
                onClick = onBookmark,
                onLongClick = onBookmarkLongClick,
                modifier = Modifier.weight(1f),
                activeIconTint = MaterialTheme.extendedColors.favorite,
                // 只缩放图标，避免与按钮自身按压缩放叠加
                iconModifier = Modifier.graphicsLayer {
                    scaleX = favoriteBounce.value
                    scaleY = favoriteBounce.value
                },
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
