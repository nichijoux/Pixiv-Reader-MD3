package com.pixiv.reader.core.ui.component.layout

import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.common.config.BottomNavBarStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomBarAutoHideControllerTest {

    private fun createController() = BottomBarAutoHideController(
        hideThresholdPx = 100f,
        showThresholdPx = 60f,
    )

    @Test
    fun `初始状态可见且向下微滑未达阈值时保持显示`() {
        val controller = createController()
        assertTrue(controller.isVisible)

        controller.onScrollDelta(consumedY = -50f, availableY = 0f)
        controller.onScrollDelta(consumedY = -49f, availableY = 0f)
        assertTrue(controller.isVisible)

        controller.onScrollDelta(consumedY = -1f, availableY = 0f)
        assertFalse(controller.isVisible)
    }

    @Test
    fun `隐藏后反向回滚达到显示阈值时重新展开`() {
        val controller = createController()
        controller.onScrollDelta(consumedY = -100f, availableY = 0f)
        assertFalse(controller.isVisible)

        controller.onScrollDelta(consumedY = 59f, availableY = 0f)
        assertFalse(controller.isVisible)

        controller.onScrollDelta(consumedY = 1f, availableY = 0f)
        assertTrue(controller.isVisible)
    }

    @Test
    fun `滚动方向反转会清零累计位移防止抖动误触发`() {
        val controller = createController()

        controller.onScrollDelta(consumedY = -80f, availableY = 0f)
        controller.onScrollDelta(consumedY = 20f, availableY = 0f)

        controller.onScrollDelta(consumedY = -80f, availableY = 0f)
        assertTrue(controller.isVisible)

        controller.onScrollDelta(consumedY = -20f, availableY = 0f)
        assertFalse(controller.isVisible)
    }

    @Test
    fun `不可滚动或触底继续上滑过冲时不隐藏底栏`() {
        val controller = createController()

        controller.onScrollDelta(consumedY = 0f, availableY = -300f)
        assertTrue(controller.isVisible)
    }

    @Test
    fun `隐藏态在列表顶部继续下拉过冲时可呼出底栏`() {
        val controller = createController()
        controller.onScrollDelta(consumedY = -120f, availableY = 0f)
        assertFalse(controller.isVisible)

        controller.onScrollDelta(consumedY = 0f, availableY = 30f)
        assertFalse(controller.isVisible)
        controller.onScrollDelta(consumedY = 0f, availableY = 30f)
        assertTrue(controller.isVisible)
    }

    @Test
    fun `show 方法强制恢复显示并重置未满阈值的累计位移`() {
        val controller = createController()

        controller.onScrollDelta(consumedY = -90f, availableY = 0f)
        controller.show()
        assertTrue(controller.isVisible)

        controller.onScrollDelta(consumedY = -20f, availableY = 0f)
        assertTrue(controller.isVisible)

        controller.onScrollDelta(consumedY = -80f, availableY = 0f)
        assertFalse(controller.isVisible)
        controller.show()
        assertTrue(controller.isVisible)
    }

    @Test
    fun `computeNavBarBottomPadding 按屏幕形态与底栏样式返回正确净空`() {
        assertEquals(
            64.dp,
            computeNavBarBottomPadding(
                useRail = false,
                showBottomBar = true,
                style = BottomNavBarStyle.DOCKED,
            ),
        )
        assertEquals(
            76.dp,
            computeNavBarBottomPadding(
                useRail = false,
                showBottomBar = true,
                style = BottomNavBarStyle.FLOATING_CAPSULE,
            ),
        )
        assertEquals(
            0.dp,
            computeNavBarBottomPadding(
                useRail = false,
                showBottomBar = false,
                style = BottomNavBarStyle.DOCKED,
            ),
        )
        assertEquals(
            0.dp,
            computeNavBarBottomPadding(
                useRail = false,
                showBottomBar = false,
                style = BottomNavBarStyle.FLOATING_CAPSULE,
            ),
        )
        assertEquals(
            0.dp,
            computeNavBarBottomPadding(
                useRail = true,
                showBottomBar = true,
                style = BottomNavBarStyle.DOCKED,
            ),
        )
        assertEquals(
            0.dp,
            computeNavBarBottomPadding(
                useRail = true,
                showBottomBar = true,
                style = BottomNavBarStyle.FLOATING_CAPSULE,
            ),
        )
    }

    @Test
    fun `BottomNavBarStyle_from 正确反序列化合法值与非法兜底`() {
        assertEquals(BottomNavBarStyle.DOCKED, BottomNavBarStyle.from(0))
        assertEquals(BottomNavBarStyle.FLOATING_CAPSULE, BottomNavBarStyle.from(1))
        assertEquals(BottomNavBarStyle.DOCKED, BottomNavBarStyle.from(-1))
        assertEquals(BottomNavBarStyle.DOCKED, BottomNavBarStyle.from(99))
    }

    @Test
    fun `computeDetailActionBarBottomPadding 与主壳底栏净空几何一致`() {
        assertEquals(
            computeNavBarBottomPadding(
                useRail = false,
                showBottomBar = true,
                style = BottomNavBarStyle.DOCKED,
            ),
            computeDetailActionBarBottomPadding(BottomNavBarStyle.DOCKED),
        )
        assertEquals(
            computeNavBarBottomPadding(
                useRail = false,
                showBottomBar = true,
                style = BottomNavBarStyle.FLOATING_CAPSULE,
            ),
            computeDetailActionBarBottomPadding(BottomNavBarStyle.FLOATING_CAPSULE),
        )
    }
}
