package com.pixiv.reader.core.ui.component.input

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.component.feedback.PixivHaptics
import com.pixiv.reader.core.ui.component.feedback.rememberHapticCue
import com.pixiv.reader.core.ui.theme.AppShapes

/** 胶囊分段轨道高度（含滑块内边距）。 */
private val PillTrackHeight = 40.dp

/** 滑块与轨道边缘的间距（上下左右统一）。 */
private val PillEdgeThickness = 4.dp

/**
 * iOS 风胶囊分段选择器（全 App 分段控件统一口径）。
 *
 * ## 视觉结构
 * 圆角胶囊轨道（[AppShapes.pill]）内叠一块同形状滑块：轨道底色按主题明暗自适应
 * （深色 `surfaceContainerLow` / 浅色 `surfaceContainerHigh`），滑块取反一级
 * （深色 `surfaceContainerHighest` / 浅色 `surface`），保证动态取色下也有清晰层级。
 * 无描边、不依赖 M3 [androidx.compose.material3.SegmentedButton]。
 *
 * ## 交互
 * 选项等分轨道宽度；点击可选项先发 [PixivHaptics.HapticCue.SELECT] 触觉再回调 [onSelect]。
 * 选中态 = 滑块 + `primary` 文字 + [FontWeight.Medium]；未选 = `onSurfaceVariant`；
 * 禁用 = `onSurfaceVariant` 40% 透明度且不可点。标签复用 [SegmentedButtonLabel]
 * 的单行缩字能力，长文案（英文）不会换行把轨道撑高。
 *
 * 布局为等宽分段 + LTR（项目语言 zh / zh-TW / en 均无 RTL 语序需求）。
 *
 * @param T 选项值类型（枚举 / 索引等）
 * @param options 选项列表（值 to 文案资源），顺序即分段顺序
 * @param selected 当前选中值；不在 [options] 内时不渲染滑块（仍可选新值）
 * @param onSelect 选中回调（已响应触觉后触发）
 * @param modifier 外部传入的 Modifier（宽度由调用方决定，内部 fillMaxWidth）
 * @param optionEnabled 各选项是否可点（默认全部可用）
 * @return 无返回值
 */
@Composable
fun <T> PillSegmentedRow(
    options: List<Pair<T, Int>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionEnabled: (T) -> Boolean = { true },
) {
    // 空列表直接不渲染：避免后续按选项数做等分计算时除零
    if (options.isEmpty()) return

    val haptic = rememberHapticCue()
    val colorScheme = MaterialTheme.colorScheme
    // 按 surface 亮度判定明暗：动态取色（dynamicColor=true）下同样成立
    val dark = colorScheme.surface.luminance() < 0.5f
    val trackColor = if (dark) colorScheme.surfaceContainerLow else colorScheme.surfaceContainerHigh
    val thumbColor = if (dark) colorScheme.surfaceContainerHighest else colorScheme.surface
    val selectedIndex = options.indexOfFirst { it.first == selected }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(PillTrackHeight)
            .clip(AppShapes.pill)
            .background(trackColor),
    ) {
        // 滑块宽 = (轨道宽 - 左右边距) / 选项数；轨道宽在这里已知，无需 onSizeChanged 换算
        val thumbWidth = (maxWidth - PillEdgeThickness * 2) / options.size
        if (selectedIndex >= 0) {
            // 偏移动画走 expressive fastSpatialSpec：切段时滑块横向滑入目标位置
            val targetOffset = PillEdgeThickness + thumbWidth * selectedIndex
            val offset by animateDpAsState(
                targetValue = targetOffset,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "pillThumbOffset",
            )
            Box(
                modifier = Modifier
                    .offset(x = offset)
                    .padding(vertical = PillEdgeThickness)
                    .width(thumbWidth)
                    .fillMaxHeight()
                    .shadow(elevation = 1.dp, shape = AppShapes.pill)
                    .clip(AppShapes.pill)
                    .background(thumbColor),
            )
        }
        // 选项层叠满轨道：每项等宽、整高可点（点击区域覆盖滑块）
        Row(modifier = Modifier.fillMaxSize()) {
            options.forEach { (value, labelRes) ->
                val enabled = optionEnabled(value)
                val isSelected = value == selected
                // 无涟漪点击源：胶囊分段以滑块位移表达状态，涟漪会与滑块动效打架
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(
                            if (enabled) {
                                Modifier.clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                ) {
                                    // 触觉先行，再交给业务回调切状态（与全 App 分段控件一致）
                                    haptic(PixivHaptics.HapticCue.SELECT)
                                    onSelect(value)
                                }
                            } else {
                                Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
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
