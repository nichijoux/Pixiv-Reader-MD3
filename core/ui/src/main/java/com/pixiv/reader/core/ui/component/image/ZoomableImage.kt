package com.pixiv.reader.core.ui.component.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable

/** 判定「已放大」的阈值 */
private const val ZOOMED_EPSILON = 0.01f

/**
 * 可缩放图片（相册式，基于 telephoto `zoomable()`）。
 *
 * 展示语义：**尽可能撑满容器且保持图片完整**（等比缩放到撞到宽度或高度边界，
 * 即 `ContentScale.Fit`；黑边只在图片比例 ≠ 容器比例时出现）。
 *
 * 手势由 telephoto 库处理（与 HorizontalPager/VerticalPager 自动协作）：
 * - 双指捏合自由缩放（1x ~ 5x，双指中心为焦点），可缩小回完整态；
 * - 放大后单指平移（带惯性、边界约束）；未放大时手势不消费，Pager 翻页不受影响；
 * - 双击：完整态 ↔ 最大缩放切换（库内置动画）；单击：onClick（可选）。
 *
 * @param contentScale 展示方式，默认 [ContentScale.Fit]（完整优先）；
 *   传 [ContentScale.Crop] 可切换为「裁切撑满」语义
 * @param onZoomChanged 放大（zoomFraction > 0）时回调 true，调用方应禁用 Pager 滑动
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

    // 放大状态同步给调用方（查看器据此禁用 Pager 滑动；等值写入不触发重组）。
    // zoomFraction：0 = 最小缩放（完整态），1 = 最大缩放
    val zoomed = (zoomState.zoomFraction ?: 0f) > 0.01f
    SideEffect { onZoomChanged?.invoke(zoomed) }
}
