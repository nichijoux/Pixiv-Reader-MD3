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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.component.input.SettingsCardItem
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

/**
 * 分组行尾导航箭头（onSurfaceVariant、无障碍描述置空）：导航行 / 外链行的统一尾随块。
 *
 * @param icon 尾随图标（默认 KeyboardArrowRight；外链行传 OpenInNew）
 * @return 无返回值
 */
@Composable
internal fun MeArrowTrailing(
    icon: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 数据驱动的导航分组卡：[MeGroupCard] + 逐行 [MeRow]（行间 [MeRowDivider] 分隔），
 * 行尾统一 [MeArrowTrailing]。收敛 MeRoute「用户内容管理 / pixiv 生态」两组逐字相同的循环渲染。
 *
 * @param items 设置条目列表（icon / title / description / trailingIcon / onClick）
 * @return 无返回值
 */
@Composable
internal fun MeItemGroup(items: List<SettingsCardItem>) {
    MeGroupCard {
        items.forEachIndexed { index, item ->
            if (index > 0) MeRowDivider()
            MeRow(
                icon = item.icon,
                title = item.title,
                subtitle = item.description.takeIf { it.isNotBlank() },
                trailing = { MeArrowTrailing(item.trailingIcon) },
                onClick = item.onClick,
            )
        }
    }
}

/**
 * 宽控件行：标题行（[MeRow]）+ 全宽单选分段选择（Expressive，选项均分占满）。
 *
 * @param T 选项值类型（枚举等）
 * @param icon 标题行前置图标
 * @param title 标题行文案
 * @param selected 当前选中值
 * @param options 选项列表（值 to 文案资源）
 * @param onSelect 选择回调
 * @return 无返回值
 */
@Composable
internal fun <T> MeSegmentedRow(
    icon: ImageVector,
    title: String,
    selected: T,
    options: List<Pair<T, Int>>,
    onSelect: (T) -> Unit,
) {
    MeRow(icon = icon, title = title)
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md),
    ) {
        options.forEachIndexed { index, (value, labelRes) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}

/**
 * 值行 + 下拉菜单：行尾显示当前值（[MeValueTrailing]），点击整行展开选项菜单。
 * 菜单锚点 = 行尾值区：DropdownMenu 锚定最近父布局的 top-start，包在值区 Box 内
 * 才会从行尾右对齐展开（包在整行 Box 会从左缘弹出）。
 * 选中后先回调 [onSelect] 再收起菜单（两者均为同步状态写，先后顺序不影响最终表现）。
 *
 * @param T 选项值类型（枚举 / 存储值字符串等）
 * @param icon 行前置图标
 * @param title 行标题
 * @param currentValue 当前值文案（行尾展示）
 * @param options 选项列表（值 to 文案资源）
 * @param onSelect 选中回调（收起菜单前触发）
 * @return 无返回值
 */
@Composable
internal fun <T> MeDropdownRow(
    icon: ImageVector,
    title: String,
    currentValue: String,
    options: List<Pair<T, Int>>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    MeRow(
        icon = icon,
        title = title,
        trailing = {
            Box {
                MeValueTrailing(currentValue)
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    options.forEach { (value, labelRes) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(labelRes)) },
                            onClick = {
                                onSelect(value)
                                expanded = false
                            },
                        )
                    }
                }
            }
        },
        onClick = { expanded = true },
    )
}

/**
 * 开关行：行尾 [Switch]，整行可点切换（行点击 = 开关取反，与开关自身点击同效）。
 *
 * @param icon 行前置图标
 * @param title 行标题
 * @param subtitle 支撑文本（null 不渲染）
 * @param checked 当前开关状态
 * @param onCheckedChange 开关切换回调（整行点击传取反值，开关自身传目标值）
 * @return 无返回值
 */
@Composable
internal fun MeSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    MeRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        onClick = { onCheckedChange(!checked) },
    )
}
