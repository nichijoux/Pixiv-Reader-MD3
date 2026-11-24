package com.pixiv.reader.feature.user.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import com.pixiv.reader.core.ui.component.input.AccentIconBadge
import com.pixiv.reader.core.ui.component.input.PillSegmentedRow
import com.pixiv.reader.core.ui.component.input.SettingsCardItem
import com.pixiv.reader.core.ui.component.input.pressScale
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing

/**
 * 容器取不透明 `surfaceContainer`（MD3 tonal 表层层级）：用透明色会与「我的」页的主题色渐变背景混在一起，削弱分组卡层级。
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

@Composable
private fun MeRowIcon(icon: ImageVector, accent: Color) {
    AccentIconBadge(icon = icon, accent = accent)
}

/**
 * [onClick] 非空时整行可点，点击带按压缩放 + SELECT 轻触觉（触觉语义全 App 统一）。
 * [accent] 传 [Color.Unspecified] 由本组件回退主题 primary，彩色风格关闭时再由 `resolveAccent` 统一。
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

@Composable
internal fun <T> MeSegmentedRow(
    icon: ImageVector,
    title: String,
    selected: T,
    options: List<Pair<T, Int>>,
    accent: Color = Color.Unspecified,
    subtitle: String? = null,
    onSelect: (T) -> Unit,
) {
    MeRow(icon = icon, title = title, subtitle = subtitle, accent = accent)
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
 * 菜单必须包在行尾值区的 Box 内：DropdownMenu 锚定最近父布局的 top-start，
 * 包在整行 Box 里会从左缘弹出而不是右对齐。
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
                    // 全 App 浮层统一口径：高圆角 + 抬高一层容器色
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
 * 触觉分两档：整行点击由 [MeRow] 发 SELECT；拨动开关本体另发 TOGGLE。
 * Switch 自身消费指针事件，不会同时触发整行回调，两档并存不重复轰炸。
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
                    haptic(PixivHaptics.HapticCue.TOGGLE)
                    onCheckedChange(it)
                },
            )
        },
        onClick = { onCheckedChange(!checked) },
    )
}
