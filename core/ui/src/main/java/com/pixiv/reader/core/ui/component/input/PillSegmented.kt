package com.pixiv.reader.core.ui.component.input

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.component.feedback.PixivHaptics
import com.pixiv.reader.core.ui.component.feedback.rememberHapticCue
import com.pixiv.reader.core.ui.theme.AppShapes

private val PillTrackHeight = 40.dp

/** 视觉轨道 40dp，但触控热区保持 M3 最小 48dp。 */
private val PillTouchTargetHeight = 48.dp

private val PillEdgeThickness = 4.dp

private const val PillStateLayerAlpha = 0.12f

/**
 * 胶囊分段选择器（全 App 统一分段实现，不依赖 M3 SegmentedButton）。
 * [selected] 不在 [options] 内时不渲染滑块；布局按等宽 LTR 切分。
 */
@Composable
fun <T> PillSegmentedRow(
    options: List<Pair<T, Int>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionEnabled: (T) -> Boolean = { true },
) {
    if (options.isEmpty()) return

    val haptic = rememberHapticCue()
    val colorScheme = MaterialTheme.colorScheme
    // 按 surface 亮度判定明暗，保证动态取色（dynamicColor=true）下同样成立
    val dark = colorScheme.surface.luminance() < 0.5f
    val trackColor = if (dark) colorScheme.surfaceContainerLow else colorScheme.surfaceContainerHigh
    val thumbColor = if (dark) colorScheme.surfaceContainerHighest else colorScheme.surface
    val selectedIndex = options.indexOfFirst { it.first == selected }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(PillTouchTargetHeight),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PillTrackHeight)
                .align(Alignment.CenterStart)
                .clip(AppShapes.pill)
                .background(trackColor),
        )
        val thumbWidth = (maxWidth - PillEdgeThickness * 2) / options.size
        if (selectedIndex >= 0) {
            val targetOffset = PillEdgeThickness + thumbWidth * selectedIndex
            val offset by animateDpAsState(
                targetValue = targetOffset,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "pillThumbOffset",
            )
            Box(
                modifier = Modifier
                    .offset(
                        x = offset,
                        y = (PillTouchTargetHeight - PillTrackHeight) / 2 + PillEdgeThickness,
                    )
                    .width(thumbWidth)
                    .height(PillTrackHeight - PillEdgeThickness * 2)
                    .clip(AppShapes.pill)
                    // MD3 用 tonal 层级区分表面：滑块已取反一级容器色，不叠投影
                    .background(thumbColor),
            )
        }
        Row(modifier = Modifier.fillMaxSize()) {
            options.forEach { (value, labelRes) ->
                val enabled = optionEnabled(value)
                val isSelected = value == selected
                // 禁用涟漪避免与滑块位移动效冲突，改用下方 12% onSurface state layer 提供按压/聚焦反馈
                val interactionSource = remember { MutableInteractionSource() }
                val pressed by interactionSource.collectIsPressedAsState()
                val focused by interactionSource.collectIsFocusedAsState()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = isSelected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            interactionSource = interactionSource,
                            indication = null,
                        ) {
                            haptic(PixivHaptics.HapticCue.SELECT)
                            onSelect(value)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (enabled && (pressed || focused)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(PillTrackHeight)
                                .clip(AppShapes.pill)
                                .background(colorScheme.onSurface.copy(alpha = PillStateLayerAlpha)),
                        )
                    }
                    SegmentedButtonLabel(
                        text = stringResource(labelRes),
                        color = when {
                            !enabled -> colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            isSelected -> colorScheme.primary
                            else -> colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (isSelected) FontWeight.Medium else null,
                    )
                }
            }
        }
    }
}
