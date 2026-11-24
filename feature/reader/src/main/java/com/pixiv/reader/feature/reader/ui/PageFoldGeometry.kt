package com.pixiv.reader.feature.reader.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.atan2
import kotlin.math.hypot

// 折页几何（书脊坐标系）：书脊 x=0，右页 [0,W]×[0,H]，左页 [-W,0]×[0,H]。
// 折痕为拖动点 A 与角点 B 的垂直平分线，A 侧为正面静止区，B 侧沿折痕反射为背面翻起区；
// 屏幕坐标由渲染层叠加 spineScreenX（双页 = W，单页 = 0）。

/** |A-B| 低于此值（px）视为静止未折叠。 */
internal const val FOLD_NULL_DISTANCE = 0.5f

/** |A-B| 最小距离（px），低于此值沿 AB 推开以防垂直平分线退化。 */
internal const val MIN_DRAG_DISTANCE = 1f

internal enum class TurnDirection { FORWARD, BACKWARD }

internal fun Float.isFinite(): Boolean = !isNaN() && !isInfinite()

internal fun Offset.isFinite(): Boolean = x.isFinite() && y.isFinite()

internal data class BookGeometry(val pageWidth: Float, val pageHeight: Float)

/** 折痕垂直平分线（[normal] 指向 B 侧；[spineHitY] 为与书脊 x=0 交点，允许落在页外作旋转中心）。 */
internal data class FoldLine(
    val midPoint: Offset,
    val normal: Offset,
    val angleDeg: Float,
    val spineHitY: Float?,
    val parallelToSpine: Boolean,
)

/** 折叠结果（书脊坐标）；[progress] 为 |A-B|/2W 归一化进度。 */
internal data class FoldResult(
    val fold: FoldLine,
    val flatPolygon: List<Offset>,
    val flapPolygon: List<Offset>,
    val flapReflected: List<Offset>,
    val progress: Float,
)

internal fun turnCorner(geom: BookGeometry, direction: TurnDirection, topCorner: Boolean): Offset =
    Offset(
        x = if (direction == TurnDirection.FORWARD) geom.pageWidth else -geom.pageWidth,
        y = if (topCorner) 0f else geom.pageHeight,
    )

internal fun clampDragPoint(geom: BookGeometry, corner: Offset, touch: Offset): Offset {
    var x = touch.x.coerceIn(-geom.pageWidth, geom.pageWidth)
    var y = touch.y.coerceIn(0f, geom.pageHeight)
    val dx = x - corner.x
    val dy = y - corner.y
    val d = hypot(dx, dy)
    if (d < MIN_DRAG_DISTANCE) {
        // A≈B 时垂直平分线退化：重合时沿指向书脊方向推开
        val ux = if (d > 0f) dx / d else -signOf(corner.x)
        val uy = if (d > 0f) dy / d else 0f
        x = corner.x + ux * MIN_DRAG_DISTANCE
        y = corner.y + uy * MIN_DRAG_DISTANCE
    }
    return Offset(x, y)
}

/** 符号函数（0 归一为 +1），用于角点重合时回退至指向书脊方向。 */
private fun signOf(v: Float): Float = if (v < 0f) -1f else 1f

internal fun computeFold(geom: BookGeometry, corner: Offset, touch: Offset): FoldResult? {
    val w = geom.pageWidth
    val h = geom.pageHeight
    val abX = corner.x - touch.x
    val abY = corner.y - touch.y
    val dist = hypot(abX, abY)
    if (!dist.isFinite() || dist < FOLD_NULL_DISTANCE) return null
    val nx = abX / dist
    val ny = abY / dist
    if (!nx.isFinite() || !ny.isFinite()) return null
    val mid = Offset((touch.x + corner.x) / 2f, (touch.y + corner.y) / 2f)

    // 折痕方向角（线方向垂直于法向 N）：d = (ny, -nx)
    val angleDeg = Math.toDegrees(atan2(-nx, ny).toDouble()).toFloat()

    // 折痕与书脊 x=0 交点允许落在页外（斜向折叠旋转中心）；平行书脊时无交点
    val dirX = ny
    val parallelToSpine = kotlin.math.abs(dirX) <= 1e-6f
    val spineHitY = if (!parallelToSpine) {
        val t = -mid.x / dirX
        mid.y + t * (-nx)
    } else {
        null
    }

    val pageRect = if (corner.x >= 0f) {
        listOf(Offset(0f, 0f), Offset(w, 0f), Offset(w, h), Offset(0f, h))
    } else {
        listOf(Offset(-w, 0f), Offset(0f, 0f), Offset(0f, h), Offset(-w, h))
    }

    // 半平面判定：s(X) = N·(X - M)，A 侧 s<0（正面），B 侧 s>0（翻起）
    fun side(p: Offset): Float = nx * (p.x - mid.x) + ny * (p.y - mid.y)

    val flat = clipPolygonHalfPlane(pageRect, ::side, keepNonPositive = true)
    val flap = clipPolygonHalfPlane(pageRect, ::side, keepNonPositive = false)
    val flapReflected = flap.map { reflectAcrossFold(it, mid, nx, ny) }

    val fold = FoldLine(
        midPoint = mid,
        normal = Offset(nx, ny),
        angleDeg = angleDeg,
        spineHitY = spineHitY,
        parallelToSpine = parallelToSpine,
    )
    val progress = (dist / (2f * w)).coerceIn(0f, 1f)
    return FoldResult(
        fold = fold,
        flatPolygon = flat,
        flapPolygon = flap,
        flapReflected = flapReflected,
        progress = progress,
    )
}

internal fun reflectAcrossFold(p: Offset, mid: Offset, nx: Float, ny: Float): Offset {
    val s = nx * (p.x - mid.x) + ny * (p.y - mid.y)
    return Offset(p.x - 2f * s * nx, p.y - 2f * s * ny)
}

/** Sutherland–Hodgman 凸多边形单边半平面裁剪。 */
internal fun clipPolygonHalfPlane(
    poly: List<Offset>,
    side: (Offset) -> Float,
    keepNonPositive: Boolean,
): List<Offset> {
    if (poly.isEmpty()) return emptyList()
    val out = mutableListOf<Offset>()
    for (i in poly.indices) {
        val cur = poly[i]
        val next = poly[(i + 1) % poly.size]
        val sc = side(cur)
        val sn = side(next)
        val curIn = if (keepNonPositive) sc <= 0f else sc >= 0f
        val nextIn = if (keepNonPositive) sn <= 0f else sn >= 0f
        if (curIn) out += cur
        if (curIn != nextIn) {
            val t = sc / (sc - sn)
            out += Offset(cur.x + (next.x - cur.x) * t, cur.y + (next.y - cur.y) * t)
        }
    }
    return out
}

internal fun polygonToPath(poly: List<Offset>): Path = Path().apply {
    if (poly.isEmpty()) return@apply
    moveTo(poly.first().x, poly.first().y)
    for (i in 1 until poly.size) lineTo(poly[i].x, poly[i].y)
    close()
}
