package com.pixiv.reader.core.ui.component.image

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage

/** 最大缩放倍数 */
private const val MAX_SCALE = 6f

/** 双击放大倍数 */
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * 可缩放图片（自研，零依赖）。
 * - 单指：平移（仅缩放 > 1 时启用，避免与 Pager 滑动冲突）
 * - 双指：捏合缩放（1x ~ [MAX_SCALE]）
 * - 双击：放大 [DOUBLE_TAP_SCALE] / 恢复 1x
 * - 单击：onClick（可选）
 *
 * @param onZoomChanged 缩放状态变化回调（>1 时 Pager 应禁用滑动）
 */
@Composable
fun ZoomableImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    onZoomChanged: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(candidate: Offset): Offset {
        val maxX = (scale - 1f) * containerSize.width / 2f
        val maxY = (scale - 1f) * containerSize.height / 2f
        return Offset(
            x = candidate.x.coerceIn(-maxX, maxX),
            y = candidate.y.coerceIn(-maxY, maxY),
        )
    }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, MAX_SCALE)
        val factor = newScale / scale
        offset = clamp(offset * factor)
        scale = newScale
        offset = clamp(offset + panChange)
        onZoomChanged?.invoke(scale > 1f)
    }

    val isZoomed = scale > 1f

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick?.invoke() },
                    onDoubleTap = { pos ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                            onZoomChanged?.invoke(false)
                        } else {
                            scale = DOUBLE_TAP_SCALE
                            // 朝点击点方向缩放（近似）
                            offset = clamp(
                                Offset(
                                    (containerSize.width / 2f - pos.x) * 0.75f,
                                    (containerSize.height / 2f - pos.y) * 0.75f,
                                ),
                            )
                            onZoomChanged?.invoke(true)
                        }
                    },
                )
            },
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .then(
                    if (isZoomed) {
                        Modifier.transformable(transformableState)
                    } else {
                        Modifier
                    },
                ),
        )
    }
}
