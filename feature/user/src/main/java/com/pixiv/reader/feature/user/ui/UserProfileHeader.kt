package com.pixiv.reader.feature.user.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.Profile
import com.pixiv.api.model.User
import com.pixiv.reader.core.ui.component.UserAvatar
import com.pixiv.reader.core.ui.component.skeletonPulseColor
import com.pixiv.reader.feature.user.R
import com.pixiv.reader.feature.user.state.UserSection

/** 用户主页头部：头像 / 名称 / @account / 关注·拉黑按钮 / 签名 / 统计格（可点击）。 */
@Composable
internal fun UserHeader(
    user: User,
    profile: Profile?,
    isFollowed: Boolean,
    isFollowing: Boolean,
    isBlocked: Boolean,
    isBlocking: Boolean,
    onToggleFollow: () -> Unit,
    onToggleBlock: () -> Unit,
    onScrollToSection: (UserSection) -> Unit,
    onOpenUserBookmarks: () -> Unit,
    onOpenUserFollowing: () -> Unit,
    onOpenAvatar: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(
                name = user.name,
                avatarUrl = user.profile_image_urls?.best(),
                modifier = Modifier.size(64.dp),
                onClick = { user.profile_image_urls?.best()?.let(onOpenAvatar) },
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
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
            // 关注 / 拉黑 双按钮（移除三点下拉）
            FilledTonalButton(
                onClick = onToggleFollow,
                enabled = !isFollowing,
            ) {
                Text(if (isFollowed) stringResource(R.string.user_following) else stringResource(R.string.user_follow))
            }
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
        val comment = user.comment
        if (!comment.isNullOrBlank()) {
            Text(
                text = comment,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        // 统计格：插画 / 小说 / 收藏 / 关注（可点击）
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
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
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(color),
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(color),
                )
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(0.3f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .size(56.dp, 34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(color),
                )
            }
        }
    }
}
