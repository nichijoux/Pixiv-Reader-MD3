package com.pixiv.reader.core.ui.component.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable

private const val ZOOMED_EPSILON = 0.01f

/** 基于 telephoto 的可缩放图片；放大时通过 [onZoomChanged] 回调 true 供外层 Pager 禁用翻页。 */
@Composable
fun ZoomableImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    onZoomChanged: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val zoomState = rememberZoomableState()

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier
            .clipToBounds()
            .zoomable(
                state = zoomState,
                onClick = { onClick?.invoke() },
            ),
    )

    val zoomed = (zoomState.zoomFraction ?: 0f) > 0.01f
    SideEffect { onZoomChanged?.invoke(zoomed) }
}
