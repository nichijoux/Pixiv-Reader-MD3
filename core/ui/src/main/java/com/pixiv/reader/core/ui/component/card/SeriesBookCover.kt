package com.pixiv.reader.core.ui.component.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.SettingAccentTeal
import com.pixiv.reader.core.ui.theme.resolveAccent

/**
 * 系列图标容器（彩色图标徽标语义）。
 *
 * 系列无封面 URL，用「点缀色淡底 + 同色 MenuBook 图标」容器作为占位
 * （与「插画无图 / 用户无头像」的兜底一致）。点缀色经 [resolveAccent] 取 Teal：
 * 彩色风格关闭时回退主题 primary；底色透明度按主题明暗自适应
 * （深色 18% / 浅色 12%），与 [com.pixiv.reader.core.ui.component.input.AccentIconBadge] 语汇一致。
 *
 * @param modifier 外部传入的 Modifier（建议指定尺寸，如 `size(48.dp)` / `size(64.dp)`）
 * @param iconSize 图标尺寸（默认 28dp）
 * @param shape 容器圆角（默认 [AppShapes.card]）
 * @return 无返回值
 */
@Composable
fun SeriesBookCover(
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp,
    shape: Shape = AppShapes.card,
) {
    // 点缀色统一经全局彩色风格开关解析：关闭时回退主题 primary
    val accent = resolveAccent(SettingAccentTeal)
    // 深色下提高淡底透明度：12% × 高亮色叠深底色差过小，色块几乎不可见
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val containerAlpha = if (dark) 0.18f else 0.12f
    Box(
        modifier = modifier
            .clip(shape)
            .background(accent.copy(alpha = containerAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(iconSize),
        )
    }
}
