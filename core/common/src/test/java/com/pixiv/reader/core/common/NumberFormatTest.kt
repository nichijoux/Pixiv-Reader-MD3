package com.pixiv.reader.core.common.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class NumberFormatTest {

    @Test
    fun `below threshold keeps raw digits`() {
        assertEquals("0", formatCount(0, Locale.CHINESE))
        assertEquals("999", formatCount(999, Locale.CHINESE))
        assertEquals("9999", formatCount(9_999, Locale.CHINESE))
        // en 阈值为 1000，1500 显示 1.5K
        assertEquals("999", formatCount(999, Locale.ENGLISH))
    }

    @Test
    fun `ten thousand uses 万 unit in zh`() {
        assertEquals("1万", formatCount(10_000, Locale.CHINESE))
        assertEquals("12.4万", formatCount(124_000, Locale.CHINESE))
        assertEquals("124万", formatCount(1_240_000, Locale.CHINESE))
        assertEquals("9999.9万", formatCount(99_999_000, Locale.CHINESE))
    }

    @Test
    fun `hundred million uses 亿 unit in zh`() {
        assertEquals("1亿", formatCount(100_000_000, Locale.CHINESE))
        assertEquals("1.2亿", formatCount(123_456_789, Locale.CHINESE))
    }

    @Test
    fun `en uses K M B units`() {
        assertEquals("1K", formatCount(1_000, Locale.ENGLISH))
        assertEquals("10K", formatCount(10_000, Locale.ENGLISH))
        assertEquals("12.4K", formatCount(12_400, Locale.ENGLISH))
        assertEquals("1M", formatCount(1_000_000, Locale.ENGLISH))
        assertEquals("1.2M", formatCount(1_240_000, Locale.ENGLISH))
        assertEquals("1.2B", formatCount(1_234_567_890, Locale.ENGLISH))
    }

    @Test
    fun `formatCountForNovel unifies behavior and trims trailing zero`() {
        // 统一去尾零（修复历史 formatCountForNovel 显示 "1.0万" 的问题）
        assertEquals("1万", formatCountForNovel(10_000, Locale.CHINESE))
        assertEquals("1.5K", formatCountForNovel(1_500, Locale.ENGLISH))
        assertEquals("1M", formatCountForNovel(1_000_000, Locale.ENGLISH))
    }
}