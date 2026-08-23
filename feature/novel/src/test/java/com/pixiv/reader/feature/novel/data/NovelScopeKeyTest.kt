package com.pixiv.reader.feature.novel.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 下载范围键派生：区分同一小说的单本/整系列/部分分册下载索引条目。 */
class NovelScopeKeyTest {

    @Test
    fun `single download uses empty key`() {
        assertEquals("", novelScopeKey(seriesId = null, chapterIds = null))
        assertEquals("", novelScopeKey(seriesId = null, chapterIds = emptyList()))
        // 非法 seriesId（<=0）按单本处理
        assertEquals("", novelScopeKey(seriesId = 0L, chapterIds = null))
        assertEquals("", novelScopeKey(seriesId = -5L, chapterIds = listOf(1L)))
    }

    @Test
    fun `full series download uses series key`() {
        assertEquals("series", novelScopeKey(seriesId = 42L, chapterIds = null))
        assertEquals("series", novelScopeKey(seriesId = 42L, chapterIds = emptyList()))
    }

    @Test
    fun `partial download uses partial key`() {
        assertEquals("partial", novelScopeKey(seriesId = 42L, chapterIds = listOf(7L, 8L)))
    }
}
