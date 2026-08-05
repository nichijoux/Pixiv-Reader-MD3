package com.pixiv.reader.feature.novel

import com.pixiv.api.model.Novel
import com.pixiv.reader.core.novel.NovelBlock
import com.pixiv.reader.core.novel.NovelDocument
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelExporterTest {

    private fun sampleNovel(id: Long = 123L, title: String = "测试小说"): Novel =
        Novel(id = id, title = title)

    private fun document(vararg blocks: NovelBlock): NovelDocument {
        val textLength = blocks.filterIsInstance<NovelBlock.Paragraph>().sumOf { it.text.length }
        return NovelDocument(blocks = blocks.toList(), fullText = "", textLength = textLength)
    }

    // ── 工具函数 ─────────────────────────────────────────────────────────────

    @Test
    fun `sanitizeFileName 替换文件系统非法字符`() {
        // 输入：a / b \ c : d ? . txt（`?` 也属非法字符，一并替换）
        assertEquals("a_b_c_d_.txt", sanitizeFileName("a/b\\c:d?.txt"))
        assertEquals("novel", sanitizeFileName(""))
        assertEquals("novel", sanitizeFileName("   "))
        assertFalse(sanitizeFileName("标题/系列").contains('/'))
    }

    @Test
    fun `escapeXml 转义特殊字符`() {
        assertEquals("&lt;a&gt;&amp;&quot;&apos;", escapeXml("<a>&\"'"))
    }

    // ── TXT ──────────────────────────────────────────────────────────────────

    @Test
    fun `buildTxt 输出标题作者并跳过插图`() {
        val txt = buildTxt(
            listOf(sampleNovel() to document(
                NovelBlock.Paragraph("第一段"),
                NovelBlock.Image("https://example.com/1.jpg"),
                NovelBlock.Quote("引用内容"),
            )),
            seriesTitle = null,
        )
        assertTrue(txt.contains("测试小说"))
        assertTrue(txt.contains("第一段"))
        assertTrue(txt.contains("> 引用内容"))
        assertFalse(txt.contains("example.com"))
    }

    @Test
    fun `buildTxt 系列多章包含分隔线与章节标题`() {
        val chapters = listOf(
            sampleNovel(1L, "第一章") to document(NovelBlock.Paragraph("内容一")),
            sampleNovel(2L, "第二章") to document(NovelBlock.Paragraph("内容二")),
        )
        val txt = buildTxt(chapters, seriesTitle = "系列标题")
        assertTrue(txt.contains("系列标题"))
        assertTrue(txt.contains("【第一章】"))
        assertTrue(txt.contains("【第二章】"))
        assertTrue(txt.contains("内容一"))
        assertTrue(txt.contains("内容二"))
    }

    // ── EPUB ─────────────────────────────────────────────────────────────────

    @Test
    fun `buildEpub 生成标准 epub3 zip 结构`() {
        val chapters = listOf(
            sampleNovel(1L, "第一章") to document(
                NovelBlock.Paragraph("内容一"),
                NovelBlock.Image("https://example.com/1.jpg"),
            ),
        )
        val bytes = buildEpub(
            chapters = chapters,
            seriesTitle = null,
            images = listOf(EpubImage("cover.jpg", byteArrayOf(1, 2, 3))),
        )

        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entries = 0
            var first = ""
            var hasContainer = false
            var hasOpf = false
            var hasChapter = false
            var hasCover = false
            var hasNav = false
            var generator = ""
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entries == 0) first = entry.name
                when (entry.name) {
                    "META-INF/container.xml" -> hasContainer = true
                    "OEBPS/content.opf" -> hasOpf = true
                    "OEBPS/chapter_0.xhtml" -> hasChapter = true
                    "OEBPS/images/cover.jpg" -> hasCover = true
                    "OEBPS/nav.xhtml" -> hasNav = true
                }
                val content = zip.readBytes().toString(Charsets.UTF_8)
                if (entry.name == "OEBPS/chapter_0.xhtml") generator = content
                entries++
            }
            assertEquals("mimetype", first)
            assertTrue(hasContainer)
            assertTrue(hasOpf)
            assertTrue(hasChapter)
            assertTrue(hasCover)
            assertTrue(hasNav)
            // 条目：mimetype + container.xml + content.opf + nav.xhtml + chapter_0.xhtml + images/cover.jpg
            assertEquals(6, entries)
            // 插图在 xhtml 中引用（示例图在 images 中缺失时不生成 img；这里校验图片存在性）
            assertTrue(generator.contains("第一章"))
        }
    }

    @Test
    fun `buildEpub 章节内嵌图片引用成功下载的图片`() {
        val chapters = listOf(
            sampleNovel(1L, "第一章") to document(
                NovelBlock.Paragraph("正文"),
                NovelBlock.Image("https://example.com/1.jpg"),
                NovelBlock.Image("https://example.com/2.jpg"),
            ),
        )
        // 只有第 0 章第 1 张图下载成功（img_0_0.jpg 缺失）
        val bytes = buildEpub(
            chapters = chapters,
            seriesTitle = null,
            images = listOf(EpubImage("img_0_1.jpg", byteArrayOf(9))),
        )
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var chapterXhtml = ""
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "OEBPS/chapter_0.xhtml") {
                    chapterXhtml = zip.readBytes().toString(Charsets.UTF_8)
                }
            }
            assertTrue(chapterXhtml.contains("""src="images/img_0_1.jpg""""))
            assertFalse(chapterXhtml.contains("img_0_0.jpg"))
        }
    }
}
