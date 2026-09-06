package com.pixiv.reader.feature.user.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes

/** 我的页区块标题。 */
@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xsPlus),
    )
}

/** 我的页区块间间距。 */
@Composable
internal fun SectionSpacer() {
    Spacer(Modifier.height(20.dp))
}

/**
 * 设置分组面板（Material 3 Expressive）：相关设置聚为一张 28dp extraLarge 圆角卡，
 * 组内行用 [MeRowDivider] 分隔，替代旧「一条设置一张小卡」的碎片化布局。
 *
 * @param modifier 外部传入的 Modifier
 * @param content 组内内容（ColumnScope，通常为若干 [MeRow] 与 [MeRowDivider]）
 * @return 无返回值
 */
@Composable
internal fun MeGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(content = content)
    }
}

/**
 * 组内行分隔线：左侧缩进至文案起始位（跳过前置图标槽），Material 分组列表规范。
 *
 * @return 无返回值
 */
@Composable
internal fun MeRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = Spacing.lg + Sizes.s40 + Spacing.lg),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * 分组设置行前置图标：40dp tonal 圆槽（primary 12% 透明度底 + primary 20dp 图标）。
 *
 * @param icon 图标
 * @return 无返回值
 */
@Composable
private fun MeRowIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(Sizes.s40)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Sizes.s20),
        )
    }
}

/**
 * 分组设置行基础布局（Expressive 行类型学统一）：前置 tonal 图标 + 标题/支撑文本 +
 * 尾随控件槽，最小高度 56dp。[onClick] 非空时整行可点。
 *
 * 三类用法：开关行（trailing 传 [androidx.compose.material3.Switch]，onClick 整行切换）、
 * 值行（trailing 传 [MeValueTrailing] 或下拉锚点，onClick 展开）、导航行（onClick 跳转）。
 *
 * @param icon 前置图标（tonal 圆槽渲染）
 * @param title 主标题（bodyLarge）
 * @param modifier 外部传入的 Modifier
 * @param subtitle 支撑文本（bodySmall 次级色；null 不渲染）
 * @param subtitleMaxLines 支撑文本最大行数（超行省略；宽副文本如模板串传 1）
 * @param trailing 尾随控件槽（Switch / 值 + 箭头 / 行内动作按钮）
 * @param onClick 整行点击回调；null 则行不可点
 * @return 无返回值
 */
@Composable
internal fun MeRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleMaxLines: Int = 2,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MeRowIcon(icon)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.lg),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = subtitleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * 值行尾随：当前值文本 + 右箭头（语言 / 排序等「显示当前值、点击展开」的行）。
 *
 * @param value 当前值文案
 * @return 无返回值
 */
@Composable
internal fun MeValueTrailing(value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xxs),
        )
    }
}
