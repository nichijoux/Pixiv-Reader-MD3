package com.pixiv.reader.feature.reader

import androidx.compose.ui.geometry.Offset
import com.pixiv.reader.feature.reader.ui.BookGeometry
import com.pixiv.reader.feature.reader.ui.TurnDirection
import com.pixiv.reader.feature.reader.ui.clampDragPoint
import com.pixiv.reader.feature.reader.ui.computeFold
import com.pixiv.reader.feature.reader.ui.turnCorner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class PageFoldGeometryTest {

    private val geom = BookGeometry(pageWidth = 300f, pageHeight = 500f)

    private fun polygonArea(poly: List<Offset>): Float {
        var s = 0f
        for (i in poly.indices) {
            val cur = poly[i]
            val next = poly[(i + 1) % poly.size]
            s += cur.x * next.y - next.x * cur.y
        }
        return abs(s) / 2f
    }

    @Test
    fun `折痕是拖动点与角点的垂直平分线`() {
        val corner = turnCorner(geom, TurnDirection.FORWARD, topCorner = false)
        val touch = Offset(80f, 260f)
        val fold = computeFold(geom, corner, touch)!!.fold
        val dir = Offset(fold.normal.y, -fold.normal.x)
        val samples = listOf(fold.midPoint, fold.midPoint + dir * 77f, fold.midPoint - dir * 43f)
        for (p in samples) {
            val dA = hypot(p.x - touch.x, p.y - touch.y)
            val dB = hypot(p.x - corner.x, p.y - corner.y)
            assertTrue("点到 A/B 距离应相等: $dA vs $dB", abs(dA - dB) < 0.01f)
        }
    }

    @Test
    fun `静止态与退化输入返回null`() {
        val corner = turnCorner(geom, TurnDirection.FORWARD, topCorner = true)
        assertNull(computeFold(geom, corner, corner))
        assertNull(computeFold(geom, corner, corner + Offset(0.3f, 0f)))
        assertNull(computeFold(geom, corner, Offset(Float.NaN, 0f)))
    }

    @Test
    fun `拖动点被夹回书页范围`() {
        val corner = turnCorner(geom, TurnDirection.FORWARD, topCorner = false)
        val clamped1 = clampDragPoint(geom, corner, Offset(9999f, 8888f))
        assertEquals(geom.pageWidth - 1f, clamped1.x, 1e-3f)
        assertEquals(geom.pageHeight, clamped1.y)
        val clamped2 = clampDragPoint(geom, corner, Offset(-9999f, -50f))
        assertEquals(-geom.pageWidth, clamped2.x)
        assertEquals(0f, clamped2.y)
        val clamped3 = clampDragPoint(geom, corner, corner)
        val d = hypot(clamped3.x - corner.x, clamped3.y - corner.y)
        assertTrue("夹取后与角点距离应不小于最小值", d >= 1f - 1e-3f)
    }

    @Test
    fun `正反两面半平面裁剪互补且不重叠`() {
        val corner = turnCorner(geom, TurnDirection.FORWARD, topCorner = false)
        val touch = Offset(-40f, 180f)
        val fold = computeFold(geom, corner, touch)!!
        val nx = fold.fold.normal.x
        val ny = fold.fold.normal.y
        val m = fold.fold.midPoint
        fun side(p: Offset) = nx * (p.x - m.x) + ny * (p.y - m.y)
        assertTrue(fold.flatPolygon.isNotEmpty())
        assertTrue(fold.flapPolygon.isNotEmpty())
        fold.flatPolygon.forEach { assertTrue("flat 点应在 A 侧", side(it) <= 1e-3f) }
        fold.flapPolygon.forEach { assertTrue("flap 点应在 B 侧", side(it) >= -1e-3f) }
        val total = polygonArea(fold.flatPolygon) + polygonArea(fold.flapPolygon)
        assertEquals(geom.pageWidth * geom.pageHeight, total, 0.5f)
    }

    @Test
    fun `进度在端点为0和1且随拖动单调`() {
        val corner = turnCorner(geom, TurnDirection.FORWARD, topCorner = false)
        val start = computeFold(geom, corner, corner + Offset(-2f, 0f))!!.progress
        assertEquals(0f, start, 0.01f)
        val end = computeFold(geom, corner, Offset(-geom.pageWidth, corner.y))!!.progress
        assertEquals(1f, end, 0.01f)
        var last = -1f
        for (x in 300 downTo -300 step 25) {
            val p = computeFold(geom, corner, Offset(x.toFloat(), corner.y))?.progress ?: continue
            assertTrue("进度应单调不减: $last -> $p", p >= last - 1e-4f)
            last = p
        }
    }

    @Test
    fun `向后翻与向前翻关于书脊镜像对称`() {
        val fwCorner = turnCorner(geom, TurnDirection.FORWARD, topCorner = false)
        val bwCorner = turnCorner(geom, TurnDirection.BACKWARD, topCorner = false)
        val ax = 120f
        val ay = 300f
        val fw = computeFold(geom, fwCorner, Offset(ax, ay))!!
        val bw = computeFold(geom, bwCorner, Offset(-ax, ay))!!
        assertEquals(-fw.fold.midPoint.x, bw.fold.midPoint.x, 1e-3f)
        assertEquals(fw.fold.midPoint.y, bw.fold.midPoint.y, 1e-3f)
        val fwFlatMirrored = fw.flatPolygon.map { Offset(-it.x, it.y) }
        assertEquals(fwFlatMirrored.size, bw.flatPolygon.size)
        // 裁剪起点不同可能导致顶点顺序不同，改按最近点比对集合
        for (p in fwFlatMirrored) {
            val minDist = bw.flatPolygon.minOf { hypot(it.x - p.x, it.y - p.y) }
            assertTrue("镜像点应落在向后翻的 flat 多边形上: $p", minDist < 1e-2f)
        }
    }

    @Test
    fun `翻起部分反射后几何合法`() {
        val corner = turnCorner(geom, TurnDirection.FORWARD, topCorner = false)
        for (x in -280..280 step 40) {
            for (y in 20..480 step 60) {
                val a = clampDragPoint(geom, corner, Offset(x.toFloat(), y.toFloat()))
                val fold = computeFold(geom, corner, a) ?: continue
                assertTrue(fold.flapReflected.isNotEmpty())
                for (p in fold.flapReflected) {
                    assertTrue("反射点应有限: $p", p.isFiniteX() && p.isFiniteY())
                    assertTrue("反射点越界: $p", p.x in -3f * geom.pageWidth..3f * geom.pageWidth)
                    assertTrue("反射点越界: $p", p.y in -2f * geom.pageHeight..3f * geom.pageHeight)
                }
                assertTrue(fold.progress in 0f..1f)
            }
        }
    }

    @Test
    fun `全翻末帧背面恰好铺满对页槽位`() {
        val corner = turnCorner(geom, TurnDirection.FORWARD, topCorner = false)
        val fold = computeFold(geom, corner, Offset(-geom.pageWidth, corner.y))!!
        val reflected = fold.flapReflected
        assertEquals(4, reflected.size)
        val xs = reflected.map { it.x }.sorted()
        val ys = reflected.map { it.y }.sorted()
        assertEquals(-geom.pageWidth, xs.first(), 1e-3f)
        assertEquals(0f, xs.last(), 1e-3f)
        assertEquals(0f, ys.first(), 1e-3f)
        assertEquals(geom.pageHeight, ys.last(), 1e-3f)
    }

    private fun Offset.isFiniteX(): Boolean = !x.isNaN() && !x.isInfinite()
    private fun Offset.isFiniteY(): Boolean = !y.isNaN() && !y.isInfinite()
}
