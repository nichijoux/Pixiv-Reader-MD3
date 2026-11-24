package com.pixiv.reader.feature.follow.ui

import androidx.compose.foundation.background
import com.pixiv.reader.core.common.ui.WindowSizeClass
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixiv.api.model.UserPreview
import com.pixiv.reader.core.ui.component.card.UserAvatar
import com.pixiv.reader.core.ui.component.layout.LocalNavBarBottomPadding
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.feature.follow.R

/**
 * 左列关注用户列表。列宽（手机 60dp / 平板 168dp）与 [FollowRoute] 的 pane 可用性判定耦合，改动需同步。
 */
@Composable
internal fun FollowUserColumn(
    users: List<UserPreview>,
    selectedUserId: Long?,
    isLoadingUsers: Boolean,
    windowClass: WindowSizeClass,
    onSelectUser: (Long?) -> Unit,
    onLoadMoreUsers: () -> Unit,
) {
    val isCompact = windowClass == WindowSizeClass.Compact
    val columnWidth = if (isCompact) 60.dp else 168.dp
    Box(
        modifier = Modifier
            .width(columnWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                top = Spacing.xsPlus,
                bottom = Spacing.xsPlus + LocalNavBarBottomPadding.current + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
        ) {
            item(key = "all") {
                UserColumnItem(
                    name = stringResource(R.string.follow_all_users),
                    avatarUrl = null,
                    isAll = true,
                    selected = selectedUserId == null,
                    windowClass = windowClass,
                    onClick = { onSelectUser(null) },
                )
            }
            items(users, key = { it.user?.id ?: it.hashCode() }) { preview ->
                val user = preview.user ?: return@items
                UserColumnItem(
                    name = user.name.orEmpty(),
                    avatarUrl = user.profile_image_urls?.best(),
                    isAll = false,
                    selected = selectedUserId == user.id,
                    windowClass = windowClass,
                    onClick = { onSelectUser(user.id) },
                )
            }
            if (users.isNotEmpty() && !isLoadingUsers) {
                item(key = "load_more_users") {
                    LaunchedEffect(Unit) { onLoadMoreUsers() }
                    Box(modifier = Modifier.fillMaxWidth().height(36.dp))
                }
            }
        }
    }
}

/**
 * 「全部」或某个关注用户；未选中时降亮度。
 */
@Composable
private fun UserColumnItem(
    name: String,
    avatarUrl: String?,
    isAll: Boolean,
    selected: Boolean,
    windowClass: WindowSizeClass,
    onClick: () -> Unit,
) {
    val isCompact = windowClass == WindowSizeClass.Compact
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.outline
    }
    val content: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(if (isCompact) 40.dp else 48.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainer,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isAll) {
                Icon(
                    imageVector = Icons.Filled.GridView,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Sizes.s22),
                )
            } else {
                UserAvatar(
                    name = name,
                    avatarUrl = avatarUrl,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (selected) Modifier else Modifier.alpha(0.45f)),
                )
            }
        }
        if (!isCompact) {
            Text(
                text = name,
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = Spacing.smPlus),
            )
        }
    }

    val baseModifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(vertical = 9.dp)

    if (isCompact) {
        Column(
            modifier = baseModifier.padding(horizontal = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            content()
            Text(
                text = name,
                color = contentColor,
                // 字号走 type role（labelSmall 11sp）：原先 9.5sp 低于 M3 最小字阶，小屏可读性差
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xxs),
            )
        }
    } else {
        Row(
            modifier = baseModifier.padding(horizontal = Spacing.mdPlus),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}
