package com.pixiv.reader.core.novel.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlToPlainTextTest {

    @Test
    fun `br 与 a 标签被转换为纯文本与换行`() {
        val html = "第一行文字。<br/>第二行文字。<a href=\"https://example.com\">链接文字</a>。"
        val text = htmlToPlainText(html)
        assertTrue(text.contains("第一行文字"))
        assertTrue(text.contains("第二行文字"))
        assertTrue(text.contains("链接文字"))
        assertFalse(text.contains("<br"))
        assertFalse(text.contains("<a"))
        assertTrue(text.contains("\n"))
    }

    @Test
    fun `块级段落保留段落换行`() {
        val html = "<p>段落一</p><p>段落二</p>"
        val text = htmlToPlainText(html)
        assertTrue(text.contains("段落一"))
        assertTrue(text.contains("段落二"))
        assertTrue(text.contains("\n"))
    }

    @Test
    fun `空字符串返回空`() {
        assertEquals("", htmlToPlainText(""))
        assertEquals("", htmlToPlainText("   "))
    }

    @Test
    fun `不包含原始标签与实体`() {
        val html = "<div>文本&amp;符号<br>下一行</div>"
        val text = htmlToPlainText(html)
        assertTrue(text.contains("文本&符号"))
        assertTrue(text.contains("下一行"))
        assertFalse(text.contains("&amp;"))
    }
}
