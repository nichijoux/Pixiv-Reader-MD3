package com.pixiv.reader.feature.user.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.component.feedback.PixivHaptics
import com.pixiv.reader.core.ui.component.feedback.rememberHapticCue
import com.pixiv.reader.core.ui.component.input.PillSegmentedRow
import com.pixiv.reader.core.ui.component.input.SettingsCardItem
import com.pixiv.reader.core.ui.component.input.pressScale
import com.pixiv.reader.core.ui.theme.AppShapes
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
 * 组内行去分割线、以留白与前置点位颜色区分层级。
 * 容器为 92% 不透明度，让页面主题色渐变隐约透出（见 MeRoute 的渐变背景）。
 *
 * @param modifier 外部传入的 Modifier
 * @param content 组内内容（ColumnScope，通常为若干 [MeRow]）
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
            // 半透明卡让页面渐变隐约透出，保持分组卡与背景的层次而非硬边切分
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
        ),
    ) {
        Column(content = content)
    }
}

/**
 * 分组设置行前置图标：40dp 圆角方形容器（12dp 圆角）+ 点缀色 12% 透明度底 + 同色 20dp 图标。
 *
 * @param icon 图标
 * @param accent 点缀色（容器淡底与图标 tint 同源；调用方已解析兜底色）
 * @return 无返回值
 */
@Composable
private fun MeRowIcon(icon: ImageVector, accent: Color) {
    Box(
        modifier = Modifier
            .size(Sizes.s40)
            .background(
                color = accent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(Sizes.s20),
        )
    }
}

/**
 * 分组设置行基础布局（Expressive 行类型学统一）：前置多彩点缀图标 + 标题/支撑文本 +
 * 尾随控件槽，最小高度 56dp。[onClick] 非空时整行可点，点击带按压缩放与 SELECT 轻触觉。
 *
 * 三类用法：开关行（trailing 传 [androidx.compose.material3.Switch]，onClick 整行切换）、
 * 值行（trailing 传 [MeValueTrailing] 或下拉锚点，onClick 展开）、导航行（onClick 跳转）。
 *
 * @param icon 前置图标（点缀色圆角方槽渲染）
 * @param title 主标题（bodyLarge）
 * @param modifier 外部传入的 Modifier
 * @param subtitle 支撑文本（bodySmall 次级色；null 不渲染）
 * @param subtitleMaxLines 支撑文本最大行数（超行省略；宽副文本如模板串传 1）
 * @param accent 前置图标点缀色（[Color.Unspecified] 回退主题 primary）
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
    accent: Color = Color.Unspecified,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    // 未指定点缀色时回退主题主色，保证所有行都有可见的前置点位
    val iconAccent = if (accent == Color.Unspecified) MaterialTheme.colorScheme.primary else accent
    // 触觉回调在组合期无条件获取（避免在条件分支里调用 @Composable）
    val haptic = rememberHapticCue()
    // 行手势与按压缩放共享同一 interactionSource，否则收不到 pressed 态
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (onClick != null) {
                    Modifier
                        .pressScale(interactionSource = interactionSource)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            onClick = {
                                // 选中类轻触觉：与视觉按压反馈同步
                                haptic(PixivHaptics.HapticCue.SELECT)
                                onClick()
                            },
                        )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.mdPlus),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MeRowIcon(icon = icon, accent = iconAccent)
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
 * 数据驱动的导航分组卡：[MeGroupCard] + 逐行 [MeRow]（行间靠留白分隔，无分割线），
 * 行尾统一 [MeArrowTrailing]。收敛 MeRoute「用户内容管理 / pixiv 生态」两组逐字相同的循环渲染。
 *
 * @param items 设置条目列表（icon / title / description / trailingIcon / accent / onClick）
 * @return 无返回值
 */
@Composable
internal fun MeItemGroup(items: List<SettingsCardItem>) {
    MeGroupCard {
        items.forEach { item ->
            MeRow(
                icon = item.icon,
                title = item.title,
                subtitle = item.description.takeIf { it.isNotBlank() },
                accent = item.accent,
                trailing = { MeArrowTrailing(item.trailingIcon) },
                onClick = item.onClick,
            )
        }
    }
}

/**
 * 宽控件行：标题行（[MeRow]）+ 全宽胶囊单选分段（[PillSegmentedRow]，全 App 统一口径）。
 * 长文案由分段标签自身缩字处理，不会换行把轨道撑高错位。
 *
 * @param T 选项值类型（枚举等）
 * @param icon 标题行前置图标
 * @param title 标题行文案
 * @param selected 当前选中值
 * @param options 选项列表（值 to 文案资源）
 * @param accent 标题行图标点缀色（[Color.Unspecified] 回退主题 primary）
 * @param onSelect 选择回调
 * @return 无返回值
 */
@Composable
internal fun <T> MeSegmentedRow(
    icon: ImageVector,
    title: String,
    selected: T,
    options: List<Pair<T, Int>>,
    accent: Color = Color.Unspecified,
    onSelect: (T) -> Unit,
) {
    MeRow(icon = icon, title = title, accent = accent)
    PillSegmentedRow(
        options = options,
        selected = selected,
        onSelect = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md),
    )
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
 * @param accent 行图标点缀色（[Color.Unspecified] 回退主题 primary）
 * @param onSelect 选中回调（收起菜单前触发）
 * @return 无返回值
 */
@Composable
internal fun <T> MeDropdownRow(
    icon: ImageVector,
    title: String,
    currentValue: String,
    options: List<Pair<T, Int>>,
    accent: Color = Color.Unspecified,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    MeRow(
        icon = icon,
        title = title,
        accent = accent,
        trailing = {
            Box {
                MeValueTrailing(currentValue)
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    // 全 App 统一浮层：高圆角 + 抬高一层容器色
                    shape = AppShapes.large,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
 * 触觉：整行点击由 [MeRow] 统一发 SELECT；拨动开关本体时另发 TOGGLE
 * （Switch 自身消费指针事件，不会同时触发整行回调，两档并存不重复轰炸）。
 *
 * @param icon 行前置图标
 * @param title 行标题
 * @param subtitle 支撑文本（null 不渲染）
 * @param checked 当前开关状态
 * @param accent 行图标点缀色（[Color.Unspecified] 回退主题 primary）
 * @param onCheckedChange 开关切换回调（整行点击传取反值，开关自身传目标值）
 * @return 无返回值
 */
@Composable
internal fun MeSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    accent: Color = Color.Unspecified,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptic = rememberHapticCue()
    MeRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        accent = accent,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = {
                    // 开关本体拨动：TOGGLE 档触觉（与整行点击的 SELECT 档区分）
                    haptic(PixivHaptics.HapticCue.TOGGLE)
                    onCheckedChange(it)
                },
            )
        },
        onClick = { onCheckedChange(!checked) },
    )
}
