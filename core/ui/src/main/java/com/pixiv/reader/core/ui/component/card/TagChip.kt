package com.pixiv.reader.core.ui.component.card

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.resolveAccent

/**
 * 标签胶囊 chip（插画 / 小说组件共用唯一样式来源）：默认 secondaryContainer 背景 + 全圆胶囊，
 * 两个密度档——标准档（详情页标签区）与紧凑档（瀑布流卡片标签区）。
 * 传入 [accent] 时改为该色 12%（深色 18%）淡底 + 同色文字。
 *
 * @param text 展示文本（调用方自行拼 "#" 前缀或 "+N" 折叠）
 * @param onClick 点击回调；null 时纯展示不挂 clickable（如 "+N" 折叠 chip）
 * @param compact 紧凑档（卡片）：labelSmall + onSecondaryContainer + 小内边距；false 为标准档（详情页）
 * @param modifier 外部修饰
 * @param accent 胶囊点缀色；[Color.Unspecified] 保持 secondaryContainer 现状，
 *   否则经 [resolveAccent] 取色（关闭彩色风格时回退 primary）
 * @return 无返回值（UI 渲染）
 */
@Composable
fun TagChip(
    text: String,
    onClick: (() -> Unit)?,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    accent: Color = Color.Unspecified,
) {
    // 指定点缀色时：底色为同色低透明淡底、文字为解析后的点缀色；否则沿用 M3 容器角色
    val accentColor = if (accent == Color.Unspecified) null else resolveAccent(accent)
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val containerColor = accentColor?.copy(alpha = if (dark) 0.18f else 0.12f)
        ?: MaterialTheme.colorScheme.secondaryContainer
    val contentColor = accentColor ?: MaterialTheme.colorScheme.onSecondaryContainer
    Text(
        text = text,
        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
        color = contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(AppShapes.pill)
            .background(containerColor)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(
                horizontal = if (compact) Spacing.sm else Spacing.smPlus,
                vertical = if (compact) 3.dp else Spacing.xs,
            ),
    )
}
