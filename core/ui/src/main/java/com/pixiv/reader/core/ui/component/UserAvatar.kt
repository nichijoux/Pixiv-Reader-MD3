package com.pixiv.reader.core.ui.component

import androidx.compose.foundation.background
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
 * 用户头像：有头像 URL 时用 [PixivImage] 加载；
 * URL 缺失（pixiv 响应头像尺寸字段不固定）时显示用户名首字母圆形兜底，避免空白灰块。
 *
 * @param avatarUrl 建议传 `profile_image_urls?.best()`（自动 fallback 各尺寸）。
 */
@Composable
fun UserAvatar(
    name: String?,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.foundation.shape.CircleShape,
) {
    if (!avatarUrl.isNullOrBlank()) {
        PixivImage(
            url = avatarUrl,
            contentDescription = name,
            modifier = modifier.clip(shape),
        )
    } else {
        Box(
            modifier = modifier
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
