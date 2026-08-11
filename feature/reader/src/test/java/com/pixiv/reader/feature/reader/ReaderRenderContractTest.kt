package com.pixiv.reader.feature.reader

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pixiv.reader.feature.reader.state.PageElement
import com.pixiv.reader.feature.reader.state.stripParagraphIndent
import com.pixiv.reader.feature.reader.ui.bottomJustifyGapPx
import com.pixiv.reader.feature.reader.ui.justifyLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 段落渲染契约测试：
 * - [stripParagraphIndent]：段首/段尾空白（含全角空格 U+3000）剔除，缩进只由设置驱动
 * - [justifyLine]：两端对齐富余宽度分布到词距/字距（legado textFullJustify 语义）
 */
class ReaderRenderContractTest {

    // ── 缩进：去掉正文空格，只保留设置缩进 ──

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

    // ── 两端对齐：富余宽度分布 ──

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
        // 空格位于索引 2 和 4；每个空格词距 + 30/17/2 em
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
        // 6 字符 5 个间距：跨度覆盖 [0, 5)，每个间距 + 30/17/5 em
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
        // 基础字距 0.1em（字号 17sp × 0.1）；富余 30px 分到 2 个空格
        val result = justifyLine(line("第一 行 测试", 30f, letterSpacingEm = 0.1f), density)
        val expected = 0.1f + 30f / 17f / 2f
        assertEquals(expected, result.spanStyles[0].item.letterSpacing!!.value, 1e-4f)
    }

    // ── 底部对齐：行距设置必须直接生效（回归：无条件拉伸会把行距吞掉）──

    @Test
    fun `剩余不足一行时均分到行间空隙`() {
        // 富余 20px、末行高 35px、28 行（27 个空隙）→ 每个空隙约 0.74px 微调
        assertEquals(20f / 27f, bottomJustifyGapPx(20f, 35f, 27), 1e-4f)
    }

    @Test
    fun `剩余大于等于一行高时不拉伸保留自然行距`() {
        // 关键契约：富余 >= 末行高 → 0，行距完全由设置决定（否则渲染行距恒 = 页高/行数）
        assertEquals(0f, bottomJustifyGapPx(40f, 35f, 27))
        assertEquals(0f, bottomJustifyGapPx(35f, 35f, 27))
    }

    @Test
    fun `单行页或无限空隙不做微调`() {
        assertEquals(0f, bottomJustifyGapPx(10f, 35f, 0))
        assertEquals(0f, bottomJustifyGapPx(-5f, 35f, 27))
    }
}
