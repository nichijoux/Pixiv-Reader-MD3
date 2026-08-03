package com.pixiv.reader.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberFormatTest {

    @Test
    fun `below ten thousand keeps raw digits`() {
        assertEquals("0", formatCount(0))
        assertEquals("999", formatCount(999))
        assertEquals("9999", formatCount(9_999))
    }

    @Test
    fun `ten thousand uses 万 unit`() {
        assertEquals("1万", formatCount(10_000))
        assertEquals("12.4万", formatCount(124_000))
        assertEquals("124万", formatCount(1_240_000))
        assertEquals("9999.9万", formatCount(99_999_000))
    }

    @Test
    fun `hundred million uses 亿 unit`() {
        assertEquals("1亿", formatCount(100_000_000))
        assertEquals("1.2亿", formatCount(123_456_789))
    }
}
