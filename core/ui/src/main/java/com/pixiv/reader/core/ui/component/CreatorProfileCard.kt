package com.pixiv.reader.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** 创作者档案数据。 */
data class CreatorProfile(
    val id: Long,
    val name: String,
    val avatarUrl: String?,
    val covers: List<String> = emptyList(),
    val isFollowed: Boolean = false,
)

/**
 * 创作者档案卡片（Material 主题色，与其他组件保持一致）：
 * - 圆角卡片（surfaceContainer 背景）
 * - 顶部 3 张封面横排（高 120dp，边缘到边缘裁剪）
 * - 底部：64dp 圆形头像（负垂直偏移重叠封面）+ 用户名 + OutlinedButton 关注按钮
 * 关注状态在组件内维护，切换时回调 [onToggleFollow]（外部执行 API）。
 */
@Composable
fun CreatorProfileCard(
    profile: CreatorProfile,
    onToggleFollow: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var followed by remember(profile.id) { mutableStateOf(profile.isFollowed) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
    ) {
        // 顶部：3 封面横排（裁剪到边缘）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            profile.covers.take(3).forEach { url ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // 底部：头像（负偏移重叠封面）+ 用户名 + 关注按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 64dp 圆形头像：2dp 主题色边框，上移 24dp 重叠封面区
            Box(modifier = Modifier.offset(y = (-24).dp)) {
                AsyncImage(
                    model = profile.avatarUrl,
                    contentDescription = profile.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = profile.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            OutlinedButton(
                onClick = {
                    followed = !followed
                    onToggleFollow(followed)
                },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.height(40.dp),
            ) {
                Text(if (followed) "已关注" else "关注")
            }
        }
    }
}
