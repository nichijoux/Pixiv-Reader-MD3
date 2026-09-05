package com.pixiv.reader.feature.reader

import com.pixiv.reader.feature.reader.state.ReaderPage
import com.pixiv.reader.feature.reader.state.buildSpreads
import com.pixiv.reader.feature.reader.state.estimateCharFromOfficialPage
import com.pixiv.reader.feature.reader.state.estimateOfficialPage
import com.pixiv.reader.feature.reader.state.pageIndexForChar
import com.pixiv.reader.feature.reader.state.spreadIndexForChar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderPageMappingTest {

    private val pages = listOf(
        ReaderPage(startChar = 0, endChar = 100, elements = emptyList()),
        ReaderPage(startChar = 100, endChar = 250, elements = emptyList()),
        ReaderPage(startChar = 250, endChar = 400, elements = emptyList()),
    )

    @Test
    fun `字符偏移定位到所在页`() {
        assertEquals(0, pages.pageIndexForChar(0))
        assertEquals(0, pages.pageIndexForChar(99))
        assertEquals(1, pages.pageIndexForChar(100))
        assertEquals(1, pages.pageIndexForChar(200))
        assertEquals(2, pages.pageIndexForChar(399))
    }

    @Test
    fun `越界偏移就近落页`() {
        assertEquals(0, pages.pageIndexForChar(-10))
        assertEquals(2, pages.pageIndexForChar(100000))
    }

    @Test
    fun `官方页码按比例换算且夹取范围`() {
        assertEquals(1, estimateOfficialPage(0, 400, 10))
        assertEquals(10, estimateOfficialPage(400, 400, 10))
        assertEquals(4, estimateOfficialPage(160, 400, 10))
        assertEquals(1, estimateOfficialPage(0, 0, 0))
    }

    @Test
    fun `官方页码反推字符偏移`() {
        assertEquals(0, estimateCharFromOfficialPage(1, 400, 10))
        assertEquals(120, estimateCharFromOfficialPage(4, 400, 10))
        assertEquals(0, estimateCharFromOfficialPage(0, 400, 10))
    }

    // ── 跨页（双页）配对与定位 ──

    @Test
    fun `双页模式两两配对且末页落单右半留白`() {
        val spreads = buildSpreads(pages, columns = 2)
        assertEquals(2, spreads.size)
        // 跨页 0：页 0 + 页 1
        assertEquals(0, spreads[0].startChar)
        assertEquals(250, spreads[0].endChar)
        assertEquals(pages[0], spreads[0].left)
        assertEquals(pages[1], spreads[0].right)
        // 跨页 1：页 2 落单 → right 为 null（右半留白），区间只到页 2 终点
        assertEquals(pages[2], spreads[1].left)
        assertNull(spreads[1].right)
        assertEquals(250, spreads[1].startChar)
        assertEquals(400, spreads[1].endChar)
        assertEquals(2, spreads[0].columns)
    }

    @Test
    fun `单页模式逐页包装为整宽跨页`() {
        val spreads = buildSpreads(pages, columns = 1)
        assertEquals(3, spreads.size)
        assertEquals(pages[0], spreads[0].left)
        assertNull(spreads[0].right)
        assertEquals(1, spreads[0].columns)
        assertEquals(0, spreads[0].startChar)
        assertEquals(100, spreads[0].endChar)
    }

    @Test
    fun `空页列表配对为空跨页列表`() {
        assertEquals(0, buildSpreads(emptyList(), columns = 1).size)
        assertEquals(0, buildSpreads(emptyList(), columns = 2).size)
    }

    @Test
    fun `字符偏移定位到所在跨页`() {
        val spreads = buildSpreads(pages, columns = 2)
        assertEquals(0, spreads.spreadIndexForChar(0))
        assertEquals(0, spreads.spreadIndexForChar(99))
        assertEquals(0, spreads.spreadIndexForChar(150))
        // 跨页 1 只有左页（250..400）
        assertEquals(1, spreads.spreadIndexForChar(250))
        assertEquals(1, spreads.spreadIndexForChar(399))
    }

    @Test
    fun `越界偏移就近落跨页`() {
        val spreads = buildSpreads(pages, columns = 2)
        assertEquals(0, spreads.spreadIndexForChar(-10))
        assertEquals(1, spreads.spreadIndexForChar(100000))
    }
}
