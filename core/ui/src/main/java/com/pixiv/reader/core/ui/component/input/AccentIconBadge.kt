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

/**
 * 彩色点缀图标徽标（全 App 统一「淡底 + 同色图标」语汇）。
 *
 * 取代此前并存的四种写法（MeRowIcon 方形 12% / ConfirmDialog 圆形 8% /
 * DiscoverEntryCard 圆形 10% / ReadLaterEmpty 圆形 8%）。
 * 点缀色统一经 [resolveAccent]：彩色风格关闭时自动回退主题单色。
 * 容器透明度按主题明暗自适应——深色下 12% 淡底几乎不可见，提高到 18% 保证色块存在。
 *
 * @param icon 图标
 * @param accent 点缀色（关闭彩色风格时回退 primary）
 * @param modifier 外部 Modifier
 * @param size 容器边长（默认 40dp）
 * @param cornerRadius 圆角半径（默认 12dp；传 0.dp 为直角，圆形场景传 size / 2 或用 [AccentIconBadgeCircle]）
 * @param iconSize 图标边长（默认 20dp）
 * @param contentDescription 无障碍描述（装饰性图标传 null）
 * @return 无返回值
 */
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
    // 点缀色统一经全局开关解析：关闭彩色风格时回退主题 primary
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

/**
 * 全圆形态的 [AccentIconBadge] 便捷包装（colorScheme 圆形图标底场景）。
 *
 * @param icon 图标
 * @param accent 点缀色（关闭彩色风格时经 [resolveAccent] 回退 primary）
 * @param size 容器边长（默认 40dp；圆角自动取 size / 2）
 * @param iconSize 图标边长（默认 20dp）
 * @param modifier 外部 Modifier
 * @return 无返回值
 */
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
    // 圆角取边长一半即全圆
    cornerRadius = size / 2,
    iconSize = iconSize,
)
