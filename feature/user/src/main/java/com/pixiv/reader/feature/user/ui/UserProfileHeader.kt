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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.Profile
import com.pixiv.api.model.User
import com.pixiv.reader.core.ui.component.card.UserAvatar
import com.pixiv.reader.core.ui.component.feedback.skeletonPulseColor
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.feature.user.R
import com.pixiv.reader.feature.user.state.UserSection

/** 简介默认截断行数（防过长简介挤压下方分区内容）。 */
private const val MAX_COMMENT_LINES = 4

/**
 * 用户主页头部第一行（常驻不折叠）：头像 / 名称 / @account / 关注·拉黑按钮。
 * 下滑时本行保持可见，仅下方 [UserHeaderDetails]（简介 + 统计格）收起。
 *
 * @param user 用户实体
 * @param isFollowed 是否已关注
 * @param isFollowing 关注请求进行中（防重复点击）
 * @param isBlocked 是否已拉黑
 * @param isBlocking 拉黑请求进行中
 * @param onToggleFollow 关注/取关回调（公开关注）
 * @param onFollowPrivately 私密关注回调（关注按钮长按触发）
 * @param onToggleBlock 拉黑/解除拉黑回调
 * @param onOpenAvatar 头像点击（打开全屏大图）
 * @return 无返回值
 */
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
        // 关注 / 拉黑 双按钮（关注按钮自绘以支持长按 = 私密关注）
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
 * 关注按钮（自绘替代 FilledTonalButton 以支持长按手势）：
 * 视觉对齐 M3 tonal button（secondaryContainer 底 + onSecondaryContainer 字 + 全圆角胶囊）；
 * 点击 = 公开关注 / 取关，长按 = 私密关注（未关注态生效，进行中禁用）。
 *
 * @param isFollowed 是否已关注
 * @param isFollowing 关注请求进行中（禁用防重复点击）
 * @param onToggleFollow 点击回调（公开关注/取关）
 * @param onFollowPrivately 长按回调（私密关注）
 * @return 无返回值
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

/**
 * 用户主页头部可折叠区：签名简介（可展开/收起）+ 统计格（可点击）。
 * 下滑列表时整块向上收起，常驻的 [UserHeaderProfileRow] 与分区 Tab 不受影响。
 *
 * @param user 用户实体（取简介文案）
 * @param profile 用户统计（插画/小说/公开收藏/关注数）
 * @param onScrollToSection 点击统计格滑动切换分区
 * @param onOpenUserBookmarks 打开该用户公开收藏
 * @param onOpenUserFollowing 打开该用户关注列表
 * @return 无返回值
 */
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
            // 简介限高：默认 4 行截断 + 展开/收起（防过长简介挤压下方分区内容；短简介无按钮）
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
        // 统计格：插画 / 小说 / 收藏 / 关注（可点击）
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.mdPlus),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatItem(stringResource(R.string.user_stat_illust), profile?.total_illusts) {
                onScrollToSection(UserSection.ILLUST)
            }
            StatItem(stringResource(R.string.user_stat_novel), profile?.total_novels) {
                onScrollToSection(UserSection.NOVEL)
            }
            StatItem(stringResource(R.string.user_stat_bookmark), profile?.total_bookmarks_public) {
                onOpenUserBookmarks()
            }
            StatItem(stringResource(R.string.user_stat_follow), profile?.total_follow_users) {
                onOpenUserFollowing()
            }
        }
    }
}

/** 单个统计格（数值 + 标签，可点击）。 */
@Composable
private fun StatItem(
    label: String,
    value: Int?,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(AppShapes.card)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.xsPlus),
    ) {
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

/** 加载骨架：头部（头像/名称/按钮/统计）+ Tab 条 + 瀑布流占位，呼吸脉冲。 */
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
                    .clip(RoundedCornerShape(32.dp))
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
                    .clip(RoundedCornerShape(18.dp))
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
