package com.pixiv.reader.core.novel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelDocumentCodecTest {

    @Test
    fun `encode 后 decode 可还原全部块类型`() {
        val document = NovelDocument(
            blocks = listOf(
                NovelBlock.Paragraph("第一段"),
                NovelBlock.Heading("标题", level = 3),
                NovelBlock.Quote("引用"),
                NovelBlock.Image("https://example.com/a.jpg", "图注"),
                NovelBlock.Separator(),
            ),
            fullText = "第一段\n标题\n引用",
            textLength = 9,
        )
        val restored = NovelDocumentCodec.decode(NovelDocumentCodec.encode(document))
        assertNotNull(restored)
        assertEquals(5, restored!!.blocks.size)
        assertEquals("第一段", (restored.blocks[0] as NovelBlock.Paragraph).text)
        assertEquals(3, (restored.blocks[1] as NovelBlock.Heading).level)
        assertEquals("引用", (restored.blocks[2] as NovelBlock.Quote).text)
        assertEquals("https://example.com/a.jpg", (restored.blocks[3] as NovelBlock.Image).url)
        assertEquals("图注", (restored.blocks[3] as NovelBlock.Image).caption)
        assertTrue((restored.blocks[4] as NovelBlock.Separator).symbol.isNotBlank())
        assertEquals(document.fullText, restored.fullText)
        assertEquals(document.textLength, restored.textLength)
    }

    @Test
    fun `decode 非法 JSON 返回 null`() {
        assertNull(NovelDocumentCodec.decode("not-json"))
    }

    @Test
    fun `空文档编解码保持为空`() {
        val restored = NovelDocumentCodec.decode(NovelDocumentCodec.encode(NovelDocument.EMPTY))
        assertNotNull(restored)
        assertTrue(restored!!.blocks.isEmpty())
        assertEquals("", restored.fullText)
    }

    @Test
    fun `图片块 caption 缺失时还原为 null`() {
        val document = NovelDocument(
            blocks = listOf(NovelBlock.Image("https://x/i.jpg")),
            fullText = "",
        )
        val restored = NovelDocumentCodec.decode(NovelDocumentCodec.encode(document))
        assertNotNull(restored)
        val img = restored!!.blocks[0] as NovelBlock.Image
        assertNull(img.caption)
    }
}
