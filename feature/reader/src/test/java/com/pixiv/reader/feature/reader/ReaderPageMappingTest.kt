package com.pixiv.reader.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPageMappingTest {

    private val pages = listOf(
        ReaderPage.Text(startChar = 0, endChar = 100, annotated = androidx.compose.ui.text.AnnotatedString("a")),
        ReaderPage.Text(startChar = 100, endChar = 250, annotated = androidx.compose.ui.text.AnnotatedString("b")),
        ReaderPage.Text(startChar = 250, endChar = 400, annotated = androidx.compose.ui.text.AnnotatedString("c")),
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
}
