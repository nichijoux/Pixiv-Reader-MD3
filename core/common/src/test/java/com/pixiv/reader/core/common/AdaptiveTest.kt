package com.pixiv.reader.core.common.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveTest {

    @Test
    fun `compact below 600`() {
        assertEquals(WindowSizeClass.Compact, classifyWindowWidth(0))
        assertEquals(WindowSizeClass.Compact, classifyWindowWidth(360))
        assertEquals(WindowSizeClass.Compact, classifyWindowWidth(599))
    }

    @Test
    fun `medium from 600 to 839`() {
        assertEquals(WindowSizeClass.Medium, classifyWindowWidth(600))
        assertEquals(WindowSizeClass.Medium, classifyWindowWidth(768))
        assertEquals(WindowSizeClass.Medium, classifyWindowWidth(839))
    }

    @Test
    fun `expanded from 840`() {
        assertEquals(WindowSizeClass.Expanded, classifyWindowWidth(840))
        assertEquals(WindowSizeClass.Expanded, classifyWindowWidth(1280))
    }

    @Test
    fun `rail used on medium and expanded`() {
        assertFalse(WindowSizeClass.Compact.useRail())
        assertTrue(WindowSizeClass.Medium.useRail())
        assertTrue(WindowSizeClass.Expanded.useRail())
    }
}
