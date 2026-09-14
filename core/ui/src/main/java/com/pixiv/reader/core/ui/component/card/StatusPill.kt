package com.pixiv.reader.core.ui.component.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.core.ui.theme.Spacing

/**
 * 状态药丸徽标（AssistChip 视觉，扁平无交互）：胶囊底色 + 单行小字。
 * 系列连载态 / 已追更与 FANBOX 费用·受限·R-18·支持关系徽标共用；
 * FANBOX 紧凑卡传 labelSmall + xsPlus/xxs 内边距，默认形态为系列卡规格。
 *
 * @param text 徽标文案
 * @param container 底色（容器色）
 * @param content 文字色
 * @param modifier 外部 Modifier
 * @param style 文字样式（默认 labelMedium）
 * @param horizontalPadding 水平内边距（默认 Spacing.smPlus）
 * @param verticalPadding 垂直内边距（默认 3.dp）
 * @return 无返回值（渲染 Composable）
 */
@Composable
fun StatusPill(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    horizontalPadding: Dp = Spacing.smPlus,
    verticalPadding: Dp = 3.dp,
) {
    Text(
        text = text,
        style = style,
        color = content,
        modifier = modifier
            .clip(AppShapes.pill)
            .background(container)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
    )
}

/**
 * 图标计数 / 统计单项：小图标 + 短文本横排居中（图标与文字同色）。
 * 系列封面信息条（白字小图标）与 FANBOX 点赞·评论计数（次级色）共用。
 *
 * @param icon 图标
 * @param text 文本（计数或统计文案）
 * @param modifier 外部 Modifier
 * @param tint 图标与文字颜色（默认 onSurfaceVariant；封面上叠加传白色）
 * @param iconSize 图标尺寸（默认 Sizes.s16；封面信息条用 12.dp）
 * @param style 文字样式（默认 labelMedium；封面信息条用 labelSmall）
 * @return 无返回值（渲染 Composable）
 */
@Composable
fun IconCount(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconSize: Dp = Sizes.s16,
    style: TextStyle = MaterialTheme.typography.labelMedium,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
        Text(
            text = text,
            style = style,
            color = tint,
            modifier = Modifier.padding(start = Spacing.xxs),
        )
    }
}
