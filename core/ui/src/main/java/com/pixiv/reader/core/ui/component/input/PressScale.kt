package com.pixiv.reader.core.ui.component.input

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 按压弹簧缩放修饰器：按下时整体微缩到 [pressedScale]，松手按 MotionScheme 快弹簧回弹至 1f。
 *
 * ## 交互语义
 * 按压物理解释（press-down 物理暗示）：卡片/按钮在手指按下瞬间轻微收缩，形成「被按下去」
 * 的体积感，松手弹性回弹；与 Material ripple 指示并存——ripple 负责「触摸点扩散」，
 * 缩放负责「物体受压」，两者叠加不冲突。
 *
 * ## 动效规格
 * 时长与曲线取自 [MaterialTheme.motionScheme] 的 `fastSpatialSpec`（在组合期捕获，
 * 与项目其他动效 token 一致），不使用固定 tween 时长。
 *
 * 注意：缩放只在 [pressedScale] 与 1f 之间取值，非按下态不产生额外布局开销。
 *
 * @param pressedScale 按下时的目标缩放比例（默认 0.97f；数值越小收缩越明显）
 * @return 追加了按压缩放的 Modifier
 */
@Composable
fun Modifier.pressScale(pressedScale: Float = 0.97f): Modifier {
    // 自建 interactionSource：需要与点击手势共用同一 source 时请用带 source 的重载
    val interactionSource = remember { MutableInteractionSource() }
    return pressScale(interactionSource, pressedScale)
}

/**
 * 按压弹簧缩放修饰器（复用调用方已有的 [interactionSource]）。
 *
 * 传入的 source 必须与同一元素上的 `clickable` / `combinedClickable` / `Card(onClick)`
 * 使用的是同一个实例，否则收集不到按压态、缩放不会发生。
 *
 * @param interactionSource 与点击手势共享的交互源（提供 pressed 态）
 * @param pressedScale 按下时的目标缩放比例（默认 0.97f）
 * @return 追加了按压缩放的 Modifier
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    // 组合期捕获动效规格：不可动画 lambda 内读取 MaterialTheme
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spec,
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
