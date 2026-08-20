package com.pixiv.reader.core.novel.parser

import com.pixiv.reader.core.novel.model.NovelBlock
import com.pixiv.reader.core.novel.model.percentageAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** MarkdownNovelParser：标题/引用/分隔线/段落解析与字符区间。 */
class MarkdownNovelParserTest {

    @Test
    fun `解析标题引用分隔线和段落`() {
        val md = """
            # 第一章

            这是正文第一段。

            ## 小节

            > 这是一句引用

            正文第二段。

            ---

            结尾段。
        """.trimIndent()

        val doc = MarkdownNovelParser.parse(md)

        assertEquals(7, doc.blocks.size)
        assertEquals("第一章", (doc.blocks[0] as NovelBlock.Heading).text)
        assertEquals(1, (doc.blocks[0] as NovelBlock.Heading).level)
        assertEquals("这是正文第一段。", (doc.blocks[1] as NovelBlock.Paragraph).text)
        assertEquals("小节", (doc.blocks[2] as NovelBlock.Heading).text)
        assertEquals(2, (doc.blocks[2] as NovelBlock.Heading).level)
        assertEquals("这是一句引用", (doc.blocks[3] as NovelBlock.Quote).text)
        assertEquals("正文第二段。", (doc.blocks[4] as NovelBlock.Paragraph).text)
        assertTrue(doc.blocks[5] is NovelBlock.Separator)
        assertEquals("结尾段。", (doc.blocks[6] as NovelBlock.Paragraph).text)
    }

    @Test
    fun `标题行首空格容忍且纯井号行视为段落`() {
        val doc = MarkdownNovelParser.parse("# 标题\n## 子标题\n#\n正文")
        assertEquals("标题", (doc.blocks[0] as NovelBlock.Heading).text)
        assertEquals(1, (doc.blocks[0] as NovelBlock.Heading).level)
        assertEquals("子标题", (doc.blocks[1] as NovelBlock.Heading).text)
        assertEquals(2, (doc.blocks[1] as NovelBlock.Heading).level)
        // "#" 无正文 → 不算标题，作为普通段落
        assertEquals("#", (doc.blocks[2] as NovelBlock.Paragraph).text)
    }

    @Test
    fun `空行与纯空白被忽略`() {
        val doc = MarkdownNovelParser.parse("第一段\n\n\n   \n第二段\n")
        assertEquals(2, doc.blocks.size)
    }

    @Test
    fun `全文字符区间连续递增且进度可换算`() {
        val doc = MarkdownNovelParser.parse("# 标题\n第一段\n第二段")
        assertTrue(doc.blocks.first().startChar == 0)
        assertTrue(doc.blocks.last().endChar == doc.textLength)
        assertEquals(0, doc.percentageAt(0))
        assertEquals(100, doc.percentageAt(doc.textLength))
    }

    @Test
    fun `空文本返回空文档`() {
        val doc = MarkdownNovelParser.parse("")
        assertTrue(doc.blocks.isEmpty())
        assertEquals("", doc.fullText)
    }
}
