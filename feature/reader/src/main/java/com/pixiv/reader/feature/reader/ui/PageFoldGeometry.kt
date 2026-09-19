package com.pixiv.reader.feature.reader.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.atan2
import kotlin.math.hypot

// ── 折页几何引擎（书脊坐标系，纯函数可单测） ─────────────────────────────────
//
// 坐标系（真实书页模型）：书脊 x=0；右页 [0,W]×[0,H]；左页 [-W,0]×[0,H]。
// 翻页 = 被翻页沿动态折痕做平面折叠：
// - 折痕 = 拖动点 A 与角点 B（被翻页自由边角）的垂直平分线（规则六）
// - 折痕 A 侧半平面 ∩ 页矩形 = 正面静止部分（flat）
// - 折痕 B 侧半平面 ∩ 页矩形 沿折痕反射 = 背面翻起部分的当前位置（flapReflected）
// - 全翻（折痕 = 书脊）时背面恰好平铺到对页槽位且可读；零翻（A=B）时无折叠
//
// 屏幕坐标换算由渲染层负责：screenX = bookX + spineScreenX
// （双页 spineScreenX = W，单页 = 0，单页向后翻的虚拟左页在屏幕外）。

/** 折痕判定最小距离（px）：|A-B| 低于此值视为未折叠（静止态）。 */
internal const val FOLD_NULL_DISTANCE = 0.5f

/** 拖动点与角点的最小距离（px）：低于此值沿 AB 方向推开，保证垂直平分线可解。 */
internal const val MIN_DRAG_DISTANCE = 1f

/** 翻页方向：向前（右页往左翻）/ 向后（左页往右翻）。 */
internal enum class TurnDirection { FORWARD, BACKWARD }

/** Float 有限性（防御 NaN/Infinity 导致路径/画刷异常）。 */
internal fun Float.isFinite(): Boolean = !isNaN() && !isInfinite()

/** Offset 有限性。 */
internal fun Offset.isFinite(): Boolean = x.isFinite() && y.isFinite()

/**
 * 书页几何（书脊坐标系）。
 *
 * @param pageWidth 单页宽 W（px）
 * @param pageHeight 页高 H（px）
 */
internal data class BookGeometry(val pageWidth: Float, val pageHeight: Float)

/**
 * 折痕：拖动点 A 与角点 B 的垂直平分线（规则六）。
 *
 * 方程 N·(X - M) = 0：M = AB 中点，N = normalize(B - A)（指向 B 侧/被翻侧）。
 *
 * @property midPoint 线上一点（AB 中点）
 * @property normal 单位法向（A→B 方向）
 * @property angleDeg 折痕线方向角（度，画布反射变换用）
 * @property spineHitY 折痕与书脊（x=0）交点 y（**可为页外值**，复合旋转的旋转中心用）
 * @property parallelToSpine 折痕是否平行于书脊（纯横向折叠，无交点；复合变换退化为平移）
 */
internal data class FoldLine(
    val midPoint: Offset,
    val normal: Offset,
    val angleDeg: Float,
    val spineHitY: Float?,
    val parallelToSpine: Boolean,
)

/**
 * 折叠结果（多边形均为书脊坐标）。
 *
 * @property fold 折痕
 * @property flatPolygon 正面可见部分 = 页矩形 ∩ A 侧半平面（可为空：全翻末帧）
 * @property flapPolygon 已翻起部分 = 页矩形 ∩ B 侧半平面（反射前）
 * @property flapReflected 翻起部分沿折痕反射后的当前位置（背面可见区）
 * @property progress 归一化翻页进度 0..1（= |A-B| / 2W，对拖动距离单调）
 */
internal data class FoldResult(
    val fold: FoldLine,
    val flatPolygon: List<Offset>,
    val flapPolygon: List<Offset>,
    val flapReflected: List<Offset>,
    val progress: Float,
)

/**
 * 角点 B：被翻页自由边（外侧缘）的角点，由翻页方向与按下点半屏位置决定。
 * 向前翻 = 右页外缘 (W, cy)；向后翻 = 左页外缘 (-W, cy)（单页向后同为虚拟左页外缘）。
 *
 * @param geom 书页几何
 * @param direction 翻页方向
 * @param topCorner true = 上角（cy=0），false = 下角（cy=H）
 * @return 角点 B（书脊坐标）
 */
internal fun turnCorner(geom: BookGeometry, direction: TurnDirection, topCorner: Boolean): Offset =
    Offset(
        x = if (direction == TurnDirection.FORWARD) geom.pageWidth else -geom.pageWidth,
        y = if (topCorner) 0f else geom.pageHeight,
    )

/**
 * 拖动点合法化（规则五）：
 * 1. 矩形夹取：x ∈ [-W, W]（书页可达范围，含跨书脊），y ∈ [0, H]；
 * 2. 与角点 B 保持最小距离 [MIN_DRAG_DISTANCE]——垂直平分线在 A≈B 时退化，
 *    距离不足时沿 AB 方向（退化时沿指向书脊方向）推开。
 *
 * @param geom 书页几何
 * @param corner 角点 B
 * @param touch 原始拖动点（书脊坐标）
 * @return 合法化后的拖动点
 */
internal fun clampDragPoint(geom: BookGeometry, corner: Offset, touch: Offset): Offset {
    var x = touch.x.coerceIn(-geom.pageWidth, geom.pageWidth)
    var y = touch.y.coerceIn(0f, geom.pageHeight)
    val dx = x - corner.x
    val dy = y - corner.y
    val d = hypot(dx, dy)
    if (d < MIN_DRAG_DISTANCE) {
        // 距离不足：有方向沿原方向推开，无方向（重合）沿指向书脊的水平方向推开
        val ux = if (d > 0f) dx / d else -signOf(corner.x)
        val uy = if (d > 0f) dy / d else 0f
        x = corner.x + ux * MIN_DRAG_DISTANCE
        y = corner.y + uy * MIN_DRAG_DISTANCE
    }
    return Offset(x, y)
}

/** 符号函数（0 归一为 +1）：书脊方向推开的回退方向用。 */
private fun signOf(v: Float): Float = if (v < 0f) -1f else 1f

/**
 * 计算折叠（规则六~八）：折痕 = AB 垂直平分线，页矩形被折痕分为
 * 正面静止部分（A 侧）与翻起部分（B 侧，反射后为背面当前位置）。
 *
 * @param geom 书页几何
 * @param corner 角点 B（[turnCorner] 产出；据其符号选择被翻页矩形：右页/左页）
 * @param touch 拖动点 A（应先经 [clampDragPoint] 合法化）
 * @return 折叠结果；|A-B| 过小（静止态）或几何非有限时返回 null
 */
internal fun computeFold(geom: BookGeometry, corner: Offset, touch: Offset): FoldResult? {
    val w = geom.pageWidth
    val h = geom.pageHeight
    val abX = corner.x - touch.x
    val abY = corner.y - touch.y
    val dist = hypot(abX, abY)
    // 静止态 / 非法输入：无折叠
    if (!dist.isFinite() || dist < FOLD_NULL_DISTANCE) return null
    val nx = abX / dist
    val ny = abY / dist
    if (!nx.isFinite() || !ny.isFinite()) return null
    val mid = Offset((touch.x + corner.x) / 2f, (touch.y + corner.y) / 2f)

    // 折痕方向角（线方向垂直于法向 N）：d = (ny, -nx)
    val angleDeg = Math.toDegrees(atan2(-nx, ny).toDouble()).toFloat()

    // 折痕与书脊（x=0）交点：X = M + t·d 且 X.x = 0。交点允许落在页外
    // （斜向折叠的旋转中心常在页外），仅当折痕平行于书脊（d.x≈0）时无交点
    val dirX = ny
    val parallelToSpine = kotlin.math.abs(dirX) <= 1e-6f
    val spineHitY = if (!parallelToSpine) {
        val t = -mid.x / dirX
        mid.y + t * (-nx)
    } else {
        null
    }

    // 被翻页矩形：B 在右 → 右页 [0,W]；B 在左 → 左页 [-W,0]
    val pageRect = if (corner.x >= 0f) {
        listOf(Offset(0f, 0f), Offset(w, 0f), Offset(w, h), Offset(0f, h))
    } else {
        listOf(Offset(-w, 0f), Offset(0f, 0f), Offset(0f, h), Offset(-w, h))
    }

    // 半平面判定：s(X) = N·(X - M)，A 侧 s<0（正面静止），B 侧 s>0（翻起）
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

/**
 * 点沿折痕反射（规则六镜像）：p' = p - 2·s·N，其中 s = N·(p - M)。
 */
internal fun reflectAcrossFold(p: Offset, mid: Offset, nx: Float, ny: Float): Offset {
    val s = nx * (p.x - mid.x) + ny * (p.y - mid.y)
    return Offset(p.x - 2f * s * nx, p.y - 2f * s * ny)
}

/**
 * 凸多边形单边半平面裁剪（Sutherland–Hodgman）：按带符号侧值保留折线一侧，
 * 边界穿越处按侧值线性插值补交点。
 *
 * @param poly 凸多边形顶点（逆/顺时针一致即可）
 * @param side 点的带符号侧值（0 = 恰在折线上）
 * @param keepNonPositive true 保留侧值 ≤ 0 的一侧；false 保留 ≥ 0 的一侧
 * @return 裁剪后的多边形顶点（可能为空）
 */
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
            // 边穿越折痕（侧值变号）：按侧值线性插值求交点，t ∈ [0,1]
            val t = sc / (sc - sn)
            out += Offset(cur.x + (next.x - cur.x) * t, cur.y + (next.y - cur.y) * t)
        }
    }
    return out
}

/**
 * 多边形 → 裁剪路径（空多边形返回空 Path，clipPath 空路径 = 不绘制任何内容）。
 */
internal fun polygonToPath(poly: List<Offset>): Path = Path().apply {
    if (poly.isEmpty()) return@apply
    moveTo(poly.first().x, poly.first().y)
    for (i in 1 until poly.size) lineTo(poly[i].x, poly[i].y)
    close()
}
