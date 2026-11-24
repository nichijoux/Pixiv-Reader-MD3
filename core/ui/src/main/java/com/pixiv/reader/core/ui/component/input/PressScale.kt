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

/** 按压弹簧缩放；需要与点击手势联动时须使用带 [MutableInteractionSource] 的重载。 */
@Composable
fun Modifier.pressScale(pressedScale: Float = 0.97f): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return pressScale(interactionSource, pressedScale)
}

/** 复用点击手势的 [interactionSource] 实现按压弹簧缩放；必须与 `clickable`/`Card` 传入同一实例，否则无法收集按压态。 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
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
