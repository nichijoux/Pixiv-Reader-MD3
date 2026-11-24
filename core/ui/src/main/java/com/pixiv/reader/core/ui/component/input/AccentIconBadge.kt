package com.pixiv.reader.core.ui.component.input

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.theme.resolveAccent

/** 彩色点缀图标徽标（淡底 + 同色图标）；[accent] 经 [resolveAccent] 解析，关闭彩色风格时回退 primary。 */
@Composable
fun AccentIconBadge(
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    cornerRadius: Dp = 12.dp,
    iconSize: Dp = 20.dp,
    contentDescription: String? = null,
) {
    val resolved = resolveAccent(accent)
    // 深色下提高容器透明度：12% × 高亮度色叠深底色差过小，色块几乎不可见
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val containerAlpha = if (dark) 0.18f else 0.12f
    Box(
        modifier = modifier
            .size(size)
            .background(resolved.copy(alpha = containerAlpha), RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = resolved,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun AccentIconBadgeCircle(
    icon: ImageVector,
    accent: Color,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    modifier: Modifier = Modifier,
) = AccentIconBadge(
    icon = icon,
    accent = accent,
    modifier = modifier,
    size = size,
    cornerRadius = size / 2,
    iconSize = iconSize,
)
