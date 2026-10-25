package com.pixiv.reader.core.ui.component.card

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing

/**
 * 标签胶囊 chip（插画 / 小说组件共用唯一样式来源）：secondaryContainer 背景 + 全圆胶囊，
 * 两个密度档——标准档（详情页标签区）与紧凑档（瀑布流卡片标签区）。
 *
 * @param text 展示文本（调用方自行拼 "#" 前缀或 "+N" 折叠）
 * @param onClick 点击回调；null 时纯展示不挂 clickable（如 "+N" 折叠 chip）
 * @param compact 紧凑档（卡片）：labelSmall + onSecondaryContainer + 小内边距；false 为标准档（详情页）
 * @param modifier 外部修饰
 * @return 无返回值（UI 渲染）
 */
@Composable
fun TagChip(
    text: String,
    onClick: (() -> Unit)?,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
        color = if (compact) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(AppShapes.pill)
            .background(MaterialTheme.colorScheme.secondaryContainer)
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
