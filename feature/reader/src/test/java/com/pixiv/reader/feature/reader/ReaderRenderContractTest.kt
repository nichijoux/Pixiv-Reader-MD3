package com.pixiv.reader.feature.reader

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp
import com.pixiv.reader.feature.reader.state.PageElement
import com.pixiv.reader.feature.reader.state.stripParagraphIndent
import com.pixiv.reader.feature.reader.ui.bottomJustifyGapPx
import com.pixiv.reader.feature.reader.ui.justifyLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderRenderContractTest {

    @Test
    fun `全角空格缩进从正文剔除并返回段首字符数`() {
        val (cleaned, leading) = stripParagraphIndent("\u3000\u3000今天天气很好")
        assertEquals("今天天气很好", cleaned)
        assertEquals(2, leading)
    }

    @Test
    fun `普通空格与制表符同样剔除`() {
        assertEquals("你好世界" to 4, stripParagraphIndent("   \t你好世界"))
    }

    @Test
    fun `首尾空白同时剔除且段首计数只含段首`() {
        assertEquals("中间文本" to 1, stripParagraphIndent(" 中间文本　 "))
    }

    @Test
    fun `无缩进段落保持不变`() {
        assertEquals("正文" to 0, stripParagraphIndent("正文"))
    }

    @Test
    fun `全空白段落清空且段首计数为全长`() {
        assertEquals("" to 4, stripParagraphIndent("\u3000 \t\u3000"))
    }

    private val density = Density(1f, 1f)

    private fun line(text: String, extraPx: Float, letterSpacingEm: Float = 0f) =
        PageElement.TextLine(
            text = text,
            style = TextStyle(
                fontSize = 17.sp,
                letterSpacing = (17f * letterSpacingEm).sp,
                textAlign = TextAlign.Justify,
            ),
            startChar = 0,
            endChar = text.length,
            heightPx = 20,
            justifyExtraPx = extraPx,
        )

    @Test
    fun `有空格行按词距拉伸每个空格`() {
        val result = justifyLine(line("第一 行 测试", 30f), density)
        assertEquals(2, result.spanStyles.size)
        val expected = 30f / 17f / 2f
        result.spanStyles.forEach { span ->
            assertEquals(1, span.end - span.start)
            assertEquals(expected, span.item.letterSpacing!!.value, 1e-4f)
            assertEquals(TextUnitType.Em, span.item.letterSpacing!!.type)
        }
        assertEquals(2, result.spanStyles[0].start)
        assertEquals(4, result.spanStyles[1].start)
    }

    @Test
    fun `纯中文行按字符间距均匀拉伸且末字不加`() {
        val result = justifyLine(line("纯中文行测试", 30f), density)
        assertEquals(1, result.spanStyles.size)
        val span = result.spanStyles[0]
        assertEquals(0, span.start)
        assertEquals(5, span.end)
        assertEquals(30f / 17f / 5f, span.item.letterSpacing!!.value, 1e-4f)
    }

    @Test
    fun `单字符行无可拉伸间距`() {
        assertTrue(justifyLine(line("字", 30f), density).spanStyles.isEmpty())
    }

    @Test
    fun `基础字距计入跨度避免被覆盖丢失`() {
        val result = justifyLine(line("第一 行 测试", 30f, letterSpacingEm = 0.1f), density)
        val expected = 0.1f + 30f / 17f / 2f
        assertEquals(expected, result.spanStyles[0].item.letterSpacing!!.value, 1e-4f)
    }

    @Test
    fun `剩余不足一行时均分到行间空隙`() {
        assertEquals(20f / 27f, bottomJustifyGapPx(20f, 35f, 27), 1e-4f)
    }

    @Test
    fun `剩余大于等于一行高时不拉伸保留自然行距`() {
        assertEquals(0f, bottomJustifyGapPx(40f, 35f, 27))
        assertEquals(0f, bottomJustifyGapPx(35f, 35f, 27))
    }

    @Test
    fun `单行页或无限空隙不做微调`() {
        assertEquals(0f, bottomJustifyGapPx(10f, 35f, 0))
        assertEquals(0f, bottomJustifyGapPx(-5f, 35f, 27))
    }
}
