package com.pixiv.reader.feature.user.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.Profile
import com.pixiv.api.model.User
import com.pixiv.reader.core.ui.component.card.UserAvatar
import com.pixiv.reader.core.ui.component.feedback.skeletonPulseColor
import com.pixiv.reader.core.ui.component.input.AccentIconBadge
import com.pixiv.reader.core.ui.theme.SettingAccentBlue
import com.pixiv.reader.core.ui.theme.SettingAccentGreen
import com.pixiv.reader.core.ui.theme.SettingAccentPink
import com.pixiv.reader.core.ui.theme.SettingAccentViolet
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.feature.user.R
import com.pixiv.reader.feature.user.state.UserSection

/** 简介默认截断行数：过长简介会挤压下方分区内容。 */
private const val MAX_COMMENT_LINES = 4

@Composable
internal fun UserHeaderProfileRow(
    user: User,
    isFollowed: Boolean,
    isFollowing: Boolean,
    isBlocked: Boolean,
    isBlocking: Boolean,
    onToggleFollow: () -> Unit,
    onFollowPrivately: () -> Unit,
    onToggleBlock: () -> Unit,
    onOpenAvatar: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
            .padding(top = Spacing.sm, bottom = Spacing.sm),
    ) {
        UserAvatar(
            name = user.name,
            avatarUrl = user.profile_image_urls?.best(),
            modifier = Modifier.size(Sizes.s64),
            onClick = { user.profile_image_urls?.best()?.let(onOpenAvatar) },
        )
        Column(modifier = Modifier.padding(start = Spacing.md).weight(1f)) {
            Text(
                text = user.name.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!user.account.isNullOrBlank()) {
                Text(
                    text = "@${user.account}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // 关注按钮自绘：需要长按 = 私密关注，M3 按钮不暴露长按
        FollowButton(
            isFollowed = isFollowed,
            isFollowing = isFollowing,
            onToggleFollow = onToggleFollow,
            onFollowPrivately = onFollowPrivately,
        )
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(
            onClick = onToggleBlock,
            enabled = !isBlocking,
            colors = if (isBlocked) {
                ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            } else {
                ButtonDefaults.outlinedButtonColors()
            },
        ) {
            Text(if (isBlocked) stringResource(R.string.user_unblock) else stringResource(R.string.user_block))
        }
    }
}

/**
 * 自绘以支持长按 = 私密关注（FilledTonalButton 只暴露 onClick），视觉对齐 M3 tonal button。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FollowButton(
    isFollowed: Boolean,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
    onFollowPrivately: () -> Unit,
) {
    // 禁用态配色对齐 M3 disabled tonal button（onSurface 12% 底 + 38% 字）
    val container = if (isFollowing) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val content = if (isFollowing) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Box(
        modifier = Modifier
            .clip(AppShapes.pill)
            .background(container)
            .combinedClickable(
                enabled = !isFollowing,
                onClick = onToggleFollow,
                onLongClick = onFollowPrivately,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(if (isFollowed) R.string.user_following else R.string.user_follow),
            style = MaterialTheme.typography.labelLarge,
            color = content,
        )
    }
}

@Composable
internal fun UserHeaderDetails(
    user: User,
    profile: Profile?,
    onScrollToSection: (UserSection) -> Unit,
    onOpenUserBookmarks: () -> Unit,
    onOpenUserFollowing: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)) {
        val comment = user.comment
        if (!comment.isNullOrBlank()) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            var truncated by remember { mutableStateOf(false) }
            val clamped = !expanded
            Text(
                text = comment,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (clamped) MAX_COMMENT_LINES else Int.MAX_VALUE,
                overflow = if (clamped) TextOverflow.Ellipsis else TextOverflow.Clip,
                modifier = Modifier
                    .padding(top = Spacing.smPlus)
                    .animateContentSize(),
                onTextLayout = { layout: TextLayoutResult ->
                    if (clamped) truncated = layout.hasVisualOverflow
                },
            )
            if (truncated || expanded) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(if (expanded) R.string.user_comment_collapse else R.string.user_comment_expand),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.mdPlus),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatItem(
                label = stringResource(R.string.user_stat_illust),
                value = profile?.total_illusts,
                icon = Icons.Filled.Image,
                accent = SettingAccentViolet,
            ) {
                onScrollToSection(UserSection.ILLUST)
            }
            StatItem(
                label = stringResource(R.string.user_stat_novel),
                value = profile?.total_novels,
                icon = Icons.AutoMirrored.Filled.MenuBook,
                accent = SettingAccentGreen,
            ) {
                onScrollToSection(UserSection.NOVEL)
            }
            StatItem(
                label = stringResource(R.string.user_stat_bookmark),
                value = profile?.total_bookmarks_public,
                icon = Icons.Filled.BookmarkBorder,
                accent = SettingAccentPink,
            ) {
                onOpenUserBookmarks()
            }
            StatItem(
                label = stringResource(R.string.user_stat_follow),
                value = profile?.total_follow_users,
                icon = Icons.Filled.Person,
                accent = SettingAccentBlue,
            ) {
                onOpenUserFollowing()
            }
        }
    }
}

/**
 * 图标槽固定尺寸并留对称间距：否则四格文字基线会因有无图标而错位，折叠裁剪区依赖稳定高度。
 */
@Composable
private fun StatItem(
    label: String,
    value: Int?,
    icon: ImageVector? = null,
    accent: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(AppShapes.card)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.xsPlus),
    ) {
        if (icon != null) {
            AccentIconBadge(
                icon = icon,
                accent = accent,
                size = Sizes.s24,
                cornerRadius = 8.dp,
                iconSize = 14.dp,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
        }
        Text(
            text = value?.toString() ?: "-",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun UserProfileSkeleton() {
    val color = skeletonPulseColor(label = "userSkeleton")
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(Sizes.s64)
                    .clip(AppShapes.circle)
                    .background(color),
            )
            Column(modifier = Modifier.padding(start = Spacing.md).weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(18.dp)
                        .clip(AppShapes.small)
                        .background(color),
                )
                Box(
                    modifier = Modifier
                        .padding(top = Spacing.sm)
                        .fillMaxWidth(0.3f)
                        .height(12.dp)
                        .clip(AppShapes.tiny)
                        .background(color),
                )
            }
            Box(
                modifier = Modifier
                    .size(72.dp, 36.dp)
                    .clip(AppShapes.pill)
                    .background(color),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.smPlus),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .size(56.dp, 34.dp)
                        .clip(AppShapes.cardSmall)
                        .background(color),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(AppShapes.cardSmall)
                        .background(color),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.smPlus),
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(3f / 4f)
                        .clip(AppShapes.card)
                        .background(color),
                )
            }
        }
    }
}
