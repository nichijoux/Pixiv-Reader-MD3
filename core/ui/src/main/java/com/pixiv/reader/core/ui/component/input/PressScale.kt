package com.pixiv.reader.core.ui.component.input

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 按压弹簧缩放；需要与点击手势联动时须使用带 [MutableInteractionSource] 的重载。 */
@Composable
fun Modifier.pressScale(pressedScale: Float = 0.97f): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return pressScale(interactionSource, pressedScale)
}

/**
 * 复用点击手势的 [interactionSource] 实现按压弹簧缩放；必须与 `clickable`/`Card` 传入同一实例。
 * 基于 Modifier.Node 惰性创建 Animatable 并在常态（scale=1f）跳过 graphicsLayer 图层分配，
 * 避免瀑布流每张卡片预分配动画通道与硬件图层导致高速滚动 GC 卡顿。
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier = this then PressScaleElement(interactionSource, pressedScale)

private val PressSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium,
)

private data class PressScaleElement(
    val interactionSource: MutableInteractionSource,
    val pressedScale: Float,
) : ModifierNodeElement<PressScaleNode>() {
    override fun create(): PressScaleNode = PressScaleNode(interactionSource, pressedScale)

    override fun update(node: PressScaleNode) {
        node.update(interactionSource, pressedScale)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "pressScale"
        properties["pressedScale"] = pressedScale
    }
}

private class PressScaleNode(
    private var interactionSource: MutableInteractionSource,
    private var pressedScale: Float,
) : Modifier.Node(), LayoutModifierNode {

    private var collectJob: Job? = null
    private var animJob: Job? = null
    private var scaleAnim: Animatable<Float, AnimationVector1D>? = null
    private var currentScale: Float = 1f

    override fun onAttach() {
        startCollecting()
    }

    override fun onDetach() {
        collectJob?.cancel()
        collectJob = null
        animJob?.cancel()
        animJob = null
        currentScale = 1f
    }

    fun update(newSource: MutableInteractionSource, newPressedScale: Float) {
        pressedScale = newPressedScale
        if (interactionSource != newSource) {
            interactionSource = newSource
            if (isAttached) {
                startCollecting()
            }
        }
    }

    private fun startCollecting() {
        collectJob?.cancel()
        collectJob = coroutineScope.launch {
            var activePressCount = 0
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        activePressCount++
                        if (activePressCount == 1) animateScaleTo(pressedScale)
                    }

                    is PressInteraction.Release,
                    is PressInteraction.Cancel -> {
                        activePressCount = (activePressCount - 1).coerceAtLeast(0)
                        if (activePressCount == 0) animateScaleTo(1f)
                    }
                }
            }
        }
    }

    private fun animateScaleTo(target: Float) {
        animJob?.cancel()
        val anim = scaleAnim ?: Animatable(currentScale).also { scaleAnim = it }
        animJob = coroutineScope.launch {
            anim.animateTo(targetValue = target, animationSpec = PressSpring) {
                currentScale = value
                invalidatePlacement()
            }
            currentScale = target
            invalidatePlacement()
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            val s = currentScale
            if (s == 1f) {
                placeable.place(0, 0)
            } else {
                placeable.placeWithLayer(0, 0) {
                    scaleX = s
                    scaleY = s
                }
            }
        }
    }
}
