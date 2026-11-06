package com.pixiv.reader.core.ui.component.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.component.input.AccentIconBadge
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes

/**
 * 分组标题（全 App 统一）：默认 labelMedium + onSurfaceVariant，左侧 4dp、底部 6dp。
 *
 * 由 feature:user 的「我的页」区块标题下沉而来，供我的页 / 发现筛选面板 /
 * 阅读器设置面板等需要「轻量分组标签」的页面共用，保证同类标题排版一致。
 * 可选传入 [icon] 在标题左侧渲染 20dp 彩色图标徽标（阅读器设置面板六分组使用）；
 * 发现页等「页面级分区标题」可传 [style] 提升层级，避免降级为分组标签观感。
 *
 * @param text 标题文案（调用方经 stringResource 传入）
 * @param modifier 外部 Modifier（调用方可追加顶部间距等）
 * @param icon 标题左侧图标徽标；null 不渲染（保持纯文字标题）
 * @param accent 图标徽标点缀色（[Color.Unspecified] 时由徽标组件回退主题 primary）
 * @param style 标题文字样式（默认 labelMedium；页面级分区可传 titleMedium 等更高层级样式）
 * @param color 标题文字颜色（默认 onSurfaceVariant；页面级分区可传 onSurface 提升对比度）
 * @return 无返回值
 */
@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = Color.Unspecified,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = modifier.padding(start = Spacing.xs, bottom = Spacing.xsPlus),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        // 可选分组图标：统一用彩色图标徽标语汇，关闭彩色风格时自动回退单色
        if (icon != null) {
            AccentIconBadge(
                icon = icon,
                accent = accent,
                size = Sizes.s20,
                cornerRadius = 6.dp,
                iconSize = 13.dp,
            )
        }
        Text(
            text = text,
            style = style,
            color = color,
        )
    }
}

/**
 * 分组间间距（20dp）。
 *
 * @return 无返回值
 */
@Composable
fun SectionSpacer() {
    Spacer(Modifier.height(20.dp))
}
