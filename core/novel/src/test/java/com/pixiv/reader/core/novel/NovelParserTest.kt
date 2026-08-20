package com.pixiv.reader.core.novel.parser

import com.pixiv.reader.core.novel.model.NovelBlock
import com.pixiv.reader.core.novel.model.blockContaining
import com.pixiv.reader.core.novel.model.percentageAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelParserTest {

    private val structuredHtml = """
        <html><body>
        <div id="wrapper">
          <section class="novel-body">
            <div class="novel-pub-info"><h1>小说标题</h1></div>
            <div class="novel-view">
              <div class="novel-content">
                <p>第一段文字，这里是一些内容。</p>
                <p>第二段文字，<br>里面有软换行。</p>
                <h2>第一章</h2>
                <p>章节正文内容。</p>
                <figure class="novel-image"><img src="https://i.pximg.net/img-original/img/a.jpg" alt="插图"></figure>
                <hr>
                <p>最后一小段。</p>
              </div>
            </div>
          </section>
        </div>
        </body></html>
    """.trimIndent()

    @Test
    fun `解析结构化 HTML 得到段落 标题 图片 分隔线`() {
        val doc = NovelParser.parse(structuredHtml)

        val types = doc.blocks.map { it::class.simpleName }
        assertEquals(listOf("Paragraph", "Paragraph", "Heading", "Paragraph", "Image", "Separator", "Paragraph"), types)

        val first = doc.blocks[0] as NovelBlock.Paragraph
        // 解析层不再硬编码缩进前缀（缩进由阅读器设置 textIndent 控制）
        assertFalse(first.text.startsWith(NovelParser.PARAGRAPH_INDENT))
        assertTrue(first.text.contains("第一段文字"))

        val heading = doc.blocks[2] as NovelBlock.Heading
        assertEquals("第一章", heading.text)
        assertEquals(2, heading.level)

        val image = doc.blocks[4] as NovelBlock.Image
        assertTrue(image.url.contains("i.pximg.net"))
        assertEquals("插图", image.caption)

        val paragraphWithBr = doc.blocks[1] as NovelBlock.Paragraph
        assertTrue(paragraphWithBr.text.contains("里面有软换行"))
    }

    @Test
    fun `段落内 br 视为同一段落的软换行`() {
        val doc = NovelParser.parse("<body><div class='novel-content'><p>甲<br>乙</p></div></body>")
        val p = doc.blocks[0] as NovelBlock.Paragraph
        assertTrue(p.text.contains("甲"))
        assertTrue(p.text.contains("乙"))
    }

    @Test
    fun `全文文本按顺序连接且字符区间正确`() {
        val doc = NovelParser.parse(structuredHtml)

        val textBlocks = doc.blocks.filter { it.isTextBlock }
        val expected = textBlocks.joinToString("\n") { block ->
            when (block) {
                is NovelBlock.Paragraph -> block.text
                is NovelBlock.Heading -> block.text
                is NovelBlock.Quote -> block.text
                else -> ""
            }
        }
        assertEquals(expected, doc.fullText)
        assertEquals(expected.length, doc.textLength)

        // 每个文本块的区间必须与全文实际位置一致
        textBlocks.forEach { block ->
            val blockText = when (block) {
                is NovelBlock.Paragraph -> block.text
                is NovelBlock.Heading -> block.text
                is NovelBlock.Quote -> block.text
                else -> ""
            }
            assertEquals(blockText, doc.fullText.substring(block.startChar, block.endChar))
        }

        // 图片/分隔线不占字符区间
        doc.blocks.filter { !it.isTextBlock }.forEach {
            assertEquals(0, it.startChar)
            assertEquals(0, it.endChar)
        }
    }

    @Test
    fun `块区间连续且不重叠`() {
        val doc = NovelParser.parse(structuredHtml)
        val ranges = doc.blocks.filter { it.isTextBlock }.map { it.startChar to it.endChar }
        var cursor = 0
        ranges.forEach { (start, end) ->
            assertEquals(cursor, start)
            assertTrue(end > start)
            cursor = end + 1 // 每个文本块后有一个 \n 分隔符
        }
    }

    @Test
    fun `无结构化内容时退化文本兜底`() {
        val html = "<html><body><div>第一段内容\n\n第二段内容</div></body></html>"
        val doc = NovelParser.parse(html)
        assertTrue(doc.blocks.isNotEmpty())
        assertTrue(doc.fullText.isNotBlank())
        assertTrue(doc.fullText.contains("第一段内容"))
    }

    @Test
    fun `空字符串返回空文档`() {
        val doc = NovelParser.parse("")
        assertTrue(doc.blocks.isEmpty())
        assertEquals(0, doc.textLength)
    }

    @Test
    fun `字符偏移映射到对应块`() {
        val doc = NovelParser.parse(structuredHtml)
        val second = doc.blocks[1] as NovelBlock.Paragraph
        val offset = second.startChar + 1
        val block = doc.blockContaining(offset)
        assertEquals(second, block)
    }

    @Test
    fun `百分比换算在 0 到 100 之间`() {
        val doc = NovelParser.parse(structuredHtml)
        assertEquals(0, doc.percentageAt(0))
        assertEquals(100, doc.percentageAt(doc.textLength))
        assertTrue(doc.percentageAt(doc.textLength / 2) in 1..99)
    }

    @Test
    fun `div 结构段落也能被解析`() {
        val html = "<body><div class='novel-content'><div class='novel-paragraph'>甲段落</div><div class='novel-paragraph'>乙段落</div></div></body>"
        val doc = NovelParser.parse(html)
        assertTrue(doc.blocks.size >= 2)
        val texts = doc.blocks.filterIsInstance<NovelBlock.Paragraph>().map { it.text }
        assertTrue(texts.any { it.contains("甲段落") })
        assertTrue(texts.any { it.contains("乙段落") })
    }

    @Test
    fun `纯 div 无 p 结构走全文兜底`() {
        val html = "<html><body><div>第一段内容</div><div>第二段内容</div></body></html>"
        val doc = NovelParser.parse(html)
        assertTrue(doc.fullText.isNotBlank())
        assertTrue(doc.fullText.contains("第一段内容"))
        assertTrue(doc.fullText.contains("第二段内容"))
    }

    @Test
    fun `script 与 style 内容被排除`() {
        val html = "<html><body><div><script>var x = '隐藏脚本';</script><p>正文可见</p></div></body></html>"
        val doc = NovelParser.parse(html)
        assertTrue(doc.fullText.contains("正文可见"))
        assertFalse(doc.fullText.contains("隐藏脚本"))
    }

    @Test
    fun `React 页面内嵌 JSON 兜底提取正文`() {
        val html = "<html><body><div id='root'></div>" +
            "<script>window.__INITIAL_STATE__={\"novel\":{\"content\":\"这是第一段正文内容，描写了故事的开始。\\n这是第二段正文内容，故事逐渐展开。\\n这是第三段正文内容，留下了悬念。\"}}</script>" +
            "</body></html>"
        val doc = NovelParser.parse(html)
        assertTrue(doc.fullText.contains("这是第一段正文内容"))
        assertTrue(doc.fullText.contains("这是第二段正文内容"))
        assertTrue(doc.fullText.contains("这是第三段正文内容"))
    }

    @Test
    fun `正文中的插图标记被切分为图片块`() {
        val html = "<html><body><div id='root'></div>" +
            "<script>window.pixiv={\"novel\":{\"text\":\"开头一段。\\n[uploadedimage:01.png]\\n中间一段。\\n[pixivimage:123456]\\n结尾一段。\"}}</script>" +
            "</body></html>"
        val imageUrls = mapOf("uploadedimage:01.png" to "https://i.pximg.net/novel-upload-original/img/2025/01/01/00/00/00/01.png")
        val doc = NovelParser.parse(html, imageUrls)

        val types = doc.blocks.map { it::class.simpleName }
        assertEquals(
            listOf("Paragraph", "Image", "Paragraph", "Image", "Paragraph"),
            types,
        )

        val images = doc.blocks.filterIsInstance<NovelBlock.Image>()
        // uploadedimage 用传入映射解析为真实 URL
        assertEquals("https://i.pximg.net/novel-upload-original/img/2025/01/01/00/00/00/01.png", images[0].url)
        // pixivimage 无映射时保留标记协议串，由上层异步解析
        assertEquals("pixivimage:123456", images[1].url)

        // 文本块不含标记，且图片不占用全文区间
        assertFalse(doc.fullText.contains("[uploadedimage"))
        assertFalse(doc.fullText.contains("[pixivimage"))
        assertTrue(doc.fullText.contains("开头一段"))
        assertTrue(doc.fullText.contains("结尾一段"))
        assertEquals(0, images[0].startChar)
        assertEquals(0, images[0].endChar)
    }

    @Test
    fun `段落中间嵌入图片时文本前后均保留`() {
        val html = "<html><body><div id='root'></div>" +
            "<script>window.pixiv={\"novel\":{\"text\":\"前半部分文字[uploadedimage:02.png]后半部分文字\"}}</script>" +
            "</body></html>"
        val imageUrls = mapOf("uploadedimage:02.png" to "https://i.pximg.net/x/02.png")
        val doc = NovelParser.parse(html, imageUrls)

        val types = doc.blocks.map { it::class.simpleName }
        assertEquals(listOf("Paragraph", "Image", "Paragraph"), types)
        val first = doc.blocks[0] as NovelBlock.Paragraph
        assertTrue(first.text.contains("前半部分文字"))
        val last = doc.blocks[2] as NovelBlock.Paragraph
        assertTrue(last.text.contains("后半部分文字"))
        // 进度全文按顺序拼接且不含标记
        assertTrue(doc.fullText.contains("前半部分文字"))
        assertTrue(doc.fullText.contains("后半部分文字"))
    }
}
