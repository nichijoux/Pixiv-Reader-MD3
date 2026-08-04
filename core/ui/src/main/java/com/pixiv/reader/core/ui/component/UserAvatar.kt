package com.pixiv.reader.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 用户头像（通用组件）。
 *
 * ## UI 设计方式
 * - 有 [avatarUrl]：`PixivImage` 按传入 [shape]（默认圆形）裁剪加载
 * - URL 缺失（pixiv 各接口头像尺寸字段组合不固定）：显示**用户名首字母圆形兜底**
 *   （`primaryContainer` 底色 + 首字符 + `onPrimaryContainer` 文字），避免空白灰块
 * 颜色取自 `MaterialTheme`，支持深浅色主题。
 *
 * @param name 用户名（用于兜底显示首字符与无障碍描述）
 * @param avatarUrl 头像 URL，建议传 `profile_image_urls?.best()`（自动 fallback 各尺寸）
 * @param modifier 外部传入的 Modifier（通常指定尺寸 `Modifier.size(...)`）
 * @param shape 头像形状（默认圆形，可传圆角）
 * @param onClick 非 null 时头像可点击（如点击跳用户主页）
 */
@Composable
fun UserAvatar(
    name: String?,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.foundation.shape.CircleShape,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) modifier.clip(shape).clickable(onClick = onClick) else modifier
    if (!avatarUrl.isNullOrBlank()) {
        PixivImage(
            url = avatarUrl,
            contentDescription = name,
            modifier = clickModifier.clip(shape),
        )
    } else {
        Box(
            modifier = clickModifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name?.firstOrNull()?.toString() ?: "?",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
            )
        }
    }
}
