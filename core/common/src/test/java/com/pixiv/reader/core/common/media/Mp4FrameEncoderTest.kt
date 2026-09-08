package com.pixiv.reader.core.common.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** RGB→YUV 转换与帧延时→PTS 累计的纯函数单测（MediaCodec 编码本体需真机验证）。 */
class Mp4FrameEncoderTest {

    // ── rgbIntToYuv：BT.601 limited range 参考点 ──────────────────────────────

    @Test
    fun `黑色映射到 limited range 下限`() {
        val yuv = rgbIntToYuv(0xFF000000.toInt())
        assertEquals(16, yuv[0])
        assertEquals(128, yuv[1])
        assertEquals(128, yuv[2])
    }

    @Test
    fun `白色映射到 limited range 上限`() {
        val yuv = rgbIntToYuv(0xFFFFFFFF.toInt())
        assertEquals(235, yuv[0])
        assertEquals(128, yuv[1])
        assertEquals(128, yuv[2])
    }

    @Test
    fun `纯红色V分量显著大于U分量`() {
        val yuv = rgbIntToYuv(0xFFFF0000.toInt())
        assertTrue("Y 应在中间调", yuv[0] in 60..90)
        assertTrue("U 应低于中性", yuv[1] < 128)
        assertTrue("V 应高于中性", yuv[2] > 128)
    }

    @Test
    fun `纯蓝色U分量显著大于V分量`() {
        val yuv = rgbIntToYuv(0xFF0000FF.toInt())
        assertTrue("Y 应在中间调", yuv[0] in 40..50)
        assertTrue("U 应高于中性", yuv[1] > 128)
        assertTrue("V 应低于中性", yuv[2] < 128)
    }

    @Test
    fun `所有分量都 clamp 在 0 到 255`() {
        val samples = listOf(
            0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF0000.toInt(),
            0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0x80123456.toInt(), 0x00ABCDEF.toInt(),
        )
        samples.forEach { pixel ->
            rgbIntToYuv(pixel).forEach { value ->
                assertTrue("分量越界: $value (pixel=$pixel)", value in 0..255)
            }
        }
    }

    // ── buildFramePresentationTimesUs ────────────────────────────────────────

    @Test
    fun `首帧pts为零且逐帧累计延时`() {
        val pts = buildFramePresentationTimesUs(intArrayOf(100, 50, 80))
        assertArrayEquals(longArrayOf(0L, 100_000L, 150_000L), pts)
    }

    @Test
    fun `非正延时按 1ms 兜底保证pts递增`() {
        val pts = buildFramePresentationTimesUs(intArrayOf(0, -5, 40))
        assertArrayEquals(longArrayOf(0L, 1_000L, 2_000L), pts)
    }

    @Test
    fun `空数组返回空pts`() {
        assertTrue(buildFramePresentationTimesUs(IntArray(0)).isEmpty())
    }

    @Test
    fun `单帧pts为零`() {
        assertArrayEquals(longArrayOf(0L), buildFramePresentationTimesUs(intArrayOf(80)))
    }
}
