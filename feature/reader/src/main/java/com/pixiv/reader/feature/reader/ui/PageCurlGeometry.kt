package com.pixiv.reader.feature.reader.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.abs
import kotlin.math.hypot

// ── 卷页几何（翻译自 legado calcPoints） ──────────────────────────────────────

/** 坐标是否有限（防御 NaN/Infinity 导致 Shader 崩溃）。 */
internal fun Float.isFinite(): Boolean = !isNaN() && !isInfinite()

internal fun Offset.isFinite(): Boolean = x.isFinite() && y.isFinite()

/** 卷页几何点集（由 calcCurlPoints 一次性算出，供路径构建与绘制使用）。 */
internal data class CurlPoints(
    val start1: Offset,
    val control1: Offset,
    val vertex1: Offset,
    val end1: Offset,
    val start2: Offset,
    val control2: Offset,
    val vertex2: Offset,
    val end2: Offset,
    val touch: Offset,
    val corner: Offset,
    val touchToCornerDis: Float,
)

/** 计算卷页全部几何点（legado calcPoints 移植）。 */
internal fun calcCurlPoints(
    touchX: Float,
    touchY: Float,
    cornerX: Float,
    cornerY: Float,
    viewWidth: Float,
    viewHeight: Float,
): CurlPoints {
    var tx = touchX
    var ty = touchY
    var mx = (tx + cornerX) / 2f
    var my = (ty + cornerY) / 2f
    // 除零保护：corner 与中点重合时退化为中点的垂线计算，避免 NaN
    var c1x = if (abs(cornerX - mx) > 1e-4f) {
        mx - (cornerY - my) * (cornerY - my) / (cornerX - mx)
    } else {
        mx
    }
    var c1y = cornerY
    var c2x = cornerX
    var c2y = calcC2Y(mx, my, cornerX, cornerY)
    var s1x = c1x - (cornerX - c1x) / 2f
    var s1y = cornerY

    // 固定左边上下两个点（边界修正）
    if (tx > 0f && tx < viewWidth) {
        if (s1x < 0f || s1x > viewWidth) {
            if (s1x < 0f) s1x = viewWidth - s1x
            val f1 = abs(cornerX - tx)
            val f2 = viewWidth * f1 / s1x
            tx = abs(cornerX - f2)
            val f3 = abs(cornerX - tx) * abs(cornerY - ty) / f1
            ty = abs(cornerY - f3)
            mx = (tx + cornerX) / 2f
            my = (ty + cornerY) / 2f
            c1x = if (abs(cornerX - mx) > 1e-4f) {
                mx - (cornerY - my) * (cornerY - my) / (cornerX - mx)
            } else {
                mx
            }
            c1y = cornerY
            c2x = cornerX
            c2y = calcC2Y(mx, my, cornerX, cornerY)
            s1x = c1x - (cornerX - c1x) / 2f
        }
    }
    val s2x = cornerX
    val s2y = c2y - (cornerY - c2y) / 2f

    val touchToCornerDis = hypot(tx - cornerX, ty - cornerY)

    val e1 = getCross(Offset(tx, ty), Offset(c1x, c1y), Offset(s1x, s1y), Offset(s2x, s2y))
    val e2 = getCross(Offset(tx, ty), Offset(c2x, c2y), Offset(s1x, s1y), Offset(s2x, s2y))

    return CurlPoints(
        start1 = Offset(s1x, s1y),
        control1 = Offset(c1x, c1y),
        vertex1 = Offset((s1x + 2 * c1x + e1.x) / 4f, (2 * c1y + s1y + e1.y) / 4f),
        end1 = e1,
        start2 = Offset(s2x, s2y),
        control2 = Offset(c2x, c2y),
        vertex2 = Offset((s2x + 2 * c2x + e2.x) / 4f, (2 * c2y + s2y + e2.y) / 4f),
        end2 = e2,
        touch = Offset(tx, ty),
        corner = Offset(cornerX, cornerY),
        touchToCornerDis = touchToCornerDis,
    )
}

internal fun calcC2Y(mx: Float, my: Float, cornerX: Float, cornerY: Float): Float {
    val f4 = cornerY - my
    return if (abs(f4) <= 1e-4f) {
        my - (cornerX - mx) * (cornerX - mx) / 0.1f
    } else {
        my - (cornerX - mx) * (cornerX - mx) / (cornerY - my)
    }
}

/** 直线 P1P2 与 P3P4 的交点。 */
internal fun getCross(p1: Offset, p2: Offset, p3: Offset, p4: Offset): Offset {
    val denom = (p1.x - p2.x) * (p3.y - p4.y) - (p1.y - p2.y) * (p3.x - p4.x)
    if (abs(denom) < 1e-6f) return p3
    val a = p1.x * p2.y - p1.y * p2.x
    val b = p3.x * p4.y - p3.y * p4.x
    val x = (a * (p3.x - p4.x) - (p1.x - p2.x) * b) / denom
    val y = (a * (p3.y - p4.y) - (p1.y - p2.y) * b) / denom
    return Offset(x, y)
}

// ── 路径构建（翻译自 legado mPath0 / 纸背三角） ─────────────────────────────

/** mPath0：被卷起的区域（贝塞尔曲线围成）。 */
internal fun curlPath0(p: CurlPoints): Path = Path().apply {
    moveTo(p.start1.x, p.start1.y)
    quadraticTo(p.control1.x, p.control1.y, p.end1.x, p.end1.y)
    lineTo(p.touch.x, p.touch.y)
    lineTo(p.end2.x, p.end2.y)
    quadraticTo(p.control2.x, p.control2.y, p.start2.x, p.start2.y)
    lineTo(p.corner.x, p.corner.y)
    close()
}

/** 纸背三角：vertex2 → vertex1 → end1 → touch → end2。 */
internal fun curlBackPath(p: CurlPoints): Path = Path().apply {
    moveTo(p.vertex2.x, p.vertex2.y)
    lineTo(p.vertex1.x, p.vertex1.y)
    lineTo(p.end1.x, p.end1.y)
    lineTo(p.touch.x, p.touch.y)
    lineTo(p.end2.x, p.end2.y)
    close()
}

/** 下一页露出三角：start1 → vertex1 → vertex2 → start2 → corner。 */
internal fun curlNextTri(p: CurlPoints): Path = Path().apply {
    moveTo(p.start1.x, p.start1.y)
    lineTo(p.vertex1.x, p.vertex1.y)
    lineTo(p.vertex2.x, p.vertex2.y)
    lineTo(p.start2.x, p.start2.y)
    lineTo(p.corner.x, p.corner.y)
    close()
}
