package com.pixiv.reader.feature.follow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.pixiv.reader.feature.follow.R

/**
 * 左列关注用户列表（手机窄版 / 平板宽版）。
 *
 * ## UI 设计
 * - 顶部「全部」项（网格图标）；下方为关注用户（头像 + 名称，触底加载更多）
 * - **选中高亮**：`primaryContainer` 底 + 左侧 `primary` 指示条 + 头像全彩外圈描边
 * - **未选中灰色低亮**：头像降透明度 + 名称用 `outline` 色（原型 v5 约定）
 * - 手机（窄版）：头像 + 名称竖排、名称单行 9.5sp 截断；平板（宽版）：头像 + 名称横排完整
 */
@Composable
internal fun FollowUserColumn(
    users: List<UserPreview>,
    selectedUserId: Long?,
    isLoadingUsers: Boolean,
    isCompact: Boolean,
    onSelectUser: (Long?) -> Unit,
    onLoadMoreUsers: () -> Unit,
) {
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
                // 沉浸式：仅内容让开状态栏，Box 背景 surfaceContainerLow 延伸至状态栏后
                .statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp),
        ) {
            // 顶部「全部」
            item(key = "all") {
                UserColumnItem(
                    name = stringResource(R.string.follow_all_users),
                    avatarUrl = null,
                    isAll = true,
                    selected = selectedUserId == null,
                    isCompact = isCompact,
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
                    isCompact = isCompact,
                    onClick = { onSelectUser(user.id) },
                )
            }
            // 触底加载更多关注用户
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
 * 单条用户项（「全部」或某用户）。点击选中；未选中灰色低亮。
 */
@Composable
private fun UserColumnItem(
    name: String,
    avatarUrl: String?,
    isAll: Boolean,
    selected: Boolean,
    isCompact: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.outline
    }
    // 横向内容（平板）/ 竖向内容（手机）
    val content: @Composable () -> Unit = {
        // 头像区：圆形（全部 = 网格图标兜底）
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
                    modifier = Modifier.size(22.dp),
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
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }

    val baseModifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
        .padding(vertical = 9.dp)

    if (isCompact) {
        // 手机窄版：头像 + 名称竖排居中
        Column(
            modifier = baseModifier.padding(horizontal = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            content()
            Text(
                text = name,
                color = contentColor,
                fontSize = 9.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            )
        }
    } else {
        // 平板宽版：头像 + 名称横排
        Row(
            modifier = baseModifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}
