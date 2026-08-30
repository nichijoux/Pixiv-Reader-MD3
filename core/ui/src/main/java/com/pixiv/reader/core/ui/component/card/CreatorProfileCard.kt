package com.pixiv.reader.core.ui.component.card

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pixiv.reader.core.ui.R
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes

/** 创作者档案数据（用户搜索 / 浏览历史用户类共用）。 */
data class CreatorProfile(
    val id: Long,
    val name: String,
    val avatarUrl: String?,
    val covers: List<String> = emptyList(),
    val isFollowed: Boolean = false,
)

/**
 * 创作者档案卡片（Material 主题自适应，用户搜索结果/历史共用）。
 *
 * ## UI 设计方式
 * 纵向 Column 分两段：
 * - **封面区**（Row，高 120dp）：3 张代表作横排 `weight(1f)` 均分，`ContentScale.Crop` 边缘到边缘裁剪
 * - **信息区**（Row，14dp 内边距）：64dp 圆形头像（2dp 主题色边框 + `offset(y=-24dp)` 负垂直偏移
 *   重叠封面区，产生"头像盖在封面上"的层次感）+ 用户名（`titleMedium` 半粗，`weight(1f)`）
 *   + `OutlinedButton` 关注按钮（主题色描边/文字）
 * 颜色全部取自 `MaterialTheme`，尺寸相对布局，不硬编码。
 *
 * ## 交互
 * 关注按钮点击**先翻转本地状态再回调** [onToggleFollow]（参数为目标状态），外部执行 API；
 * 整卡 [onClick] 进入用户主页。
 *
 * @param profile 创作者数据（id/name/avatarUrl/covers/isFollowed）
 * @param onToggleFollow 关注切换回调（true=已关注）
 * @param onClick 整卡点击（打开用户主页）
 * @param modifier 外部传入的 Modifier
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
            .clip(AppShapes.large)
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
                .padding(start = Spacing.mdPlus, end = Spacing.mdPlus, bottom = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 64dp 圆形头像：2dp 主题色边框，上移 24dp 重叠封面区
            Box(modifier = Modifier.offset(y = (-24).dp)) {
                AsyncImage(
                    model = profile.avatarUrl,
                    contentDescription = profile.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(Sizes.s64)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            }
            Spacer(Modifier.width(Spacing.md))
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
                Text(if (followed) stringResource(R.string.following) else stringResource(R.string.follow))
            }
        }
    }
}
