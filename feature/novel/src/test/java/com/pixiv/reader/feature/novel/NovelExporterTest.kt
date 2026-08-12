package com.pixiv.reader.feature.novel

import com.pixiv.api.model.Novel
import com.pixiv.reader.core.common.NovelFileNameTemplate
import com.pixiv.reader.core.common.renderNovelFileName
import com.pixiv.reader.core.common.sanitizeFileName
import com.pixiv.reader.core.novel.NovelBlock
import com.pixiv.reader.core.novel.NovelDocument
import com.pixiv.reader.feature.novel.data.DocxImage
import com.pixiv.reader.feature.novel.data.EpubImage
import com.pixiv.reader.feature.novel.data.buildDocx
import com.pixiv.reader.feature.novel.data.buildEpub
import com.pixiv.reader.feature.novel.data.buildMarkdown
import com.pixiv.reader.feature.novel.data.buildTocHierarchy
import com.pixiv.reader.feature.novel.data.buildTxt
import com.pixiv.reader.feature.novel.data.docxHeading
import com.pixiv.reader.feature.novel.data.docxImage
import com.pixiv.reader.feature.novel.data.escapeXml
import com.pixiv.reader.feature.novel.data.formatChapters
import com.pixiv.reader.feature.novel.data.imageDimensions
import com.pixiv.reader.feature.novel.data.mimeFromUrl
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
    fun `renderNovelFileName 默认模板与系列回退`() {
        // 默认模板 {series}_{id}：非系列回退本作标题
        assertEquals(
            "测试小说_123",
            renderNovelFileName(NovelFileNameTemplate.DEFAULT, "测试小说", "作者", 123L, null),
        )
        // 系列：用系列标题
        assertEquals(
            "示例系列_123",
            renderNovelFileName(NovelFileNameTemplate.DEFAULT, "测试小说", "作者", 123L, "示例系列"),
        )
    }

    @Test
    fun `renderNovelFileName 全部占位符替换`() {
        val template = "{title}@{author}_{id}_{series}"
        assertEquals(
            "测试小说@作者_123_示例系列",
            renderNovelFileName(template, "测试小说", "作者", 123L, "示例系列"),
        )
        // 作者缺失 → 空串
        assertEquals(
            "测试小说@_123_示例系列",
            renderNovelFileName(template, "测试小说", null, 123L, "示例系列"),
        )
    }

    @Test
    fun `renderNovelFileName 日期与收藏数占位符`() {
        val template = "{date}_{favcount}"
        // 日期取 ISO 前 10 位
        assertEquals(
            "2023-01-15_1200",
            renderNovelFileName(
                template,
                "测试小说", null, 123L, null,
                publishDate = "2023-01-15T12:00:00+09:00",
                favoriteCount = 1200,
            ),
        )
        // 缺失 → 空串
        assertEquals(
            "_",
            renderNovelFileName(template, "测试小说", null, 123L, null),
        )
    }

    @Test
    fun `renderNovelFileName 清洗非法字符与空白回退`() {
        // 标题含非法字符 → 清洗
        assertEquals(
            "测试_小说_123",
            renderNovelFileName(NovelFileNameTemplate.DEFAULT, "测试/小说", null, 123L, null),
        )
        // 模板不含占位符且为空白 → 回退默认
        assertEquals(
            "测试小说_123",
            renderNovelFileName("   ", "测试小说", null, 123L, null),
        )
    }

    @Test
    fun `escapeXml 转义特殊字符`() {
        assertEquals("&lt;a&gt;&amp;&quot;&apos;", escapeXml("<a>&\"'"))
    }

    // ── TXT ──────────────────────────────────────────────────────────────────

    @Test
    fun `buildTxt 输出标题作者并跳过插图`() {
        val txt = buildTxt(
            listOf(
                sampleNovel() to document(
                    NovelBlock.Paragraph("第一段"),
                    NovelBlock.Image("https://example.com/1.jpg"),
                    NovelBlock.Quote("引用内容"),
                )
            ),
            seriesTitle = null,
        )
        assertTrue(txt.contains("测试小说"))
        assertTrue(txt.contains("第一段"))
        assertTrue(txt.contains("> 引用内容"))
        assertFalse(txt.contains("example.com"))
    }

    // ── MARKDOWN ─────────────────────────────────────────────────────────────

    @Test
    fun `buildMarkdown 保留标题层级与引用并跳过插图`() {
        val md = buildMarkdown(
            listOf(
                sampleNovel() to document(
                    NovelBlock.Paragraph("第一段"),
                    NovelBlock.Image("https://example.com/1.jpg"),
                    NovelBlock.Quote("引用内容"),
                    NovelBlock.Heading("小节", 3),
                    NovelBlock.Separator(),
                )
            ),
            seriesTitle = null,
        )
        assertTrue(md.contains("# 测试小说"))
        assertTrue(md.contains("第一段"))
        assertTrue(md.contains("> 引用内容"))
        assertTrue(md.contains("### 小节"))
        assertTrue(md.contains("---"))
        assertFalse(md.contains("example.com"))
    }

    // ── DOCX ─────────────────────────────────────────────────────────────────

    @Test
    fun `buildDocx 生成最小 ooxml 容器与段落文本`() {
        val chapters = listOf(
            sampleNovel(1L, "第一章") to document(
                NovelBlock.Paragraph("内容一"),
                NovelBlock.Image("https://example.com/1.jpg"),
                NovelBlock.Quote("引用内容"),
            ),
        )
        val bytes = buildDocx(chapters, seriesTitle = "系列标题")

        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            val names = mutableListOf<String>()
            var docXml = ""
            var stylesXml = ""
            while (true) {
                val entry = zip.nextEntry ?: break
                names.add(entry.name)
                val content = zip.readBytes().toString(Charsets.UTF_8)
                if (entry.name == "word/document.xml") docXml = content
                if (entry.name == "word/styles.xml") stylesXml = content
            }
            assertEquals(
                listOf("[Content_Types].xml", "_rels/.rels", "word/document.xml", "word/_rels/document.xml.rels", "word/styles.xml"),
                names,
            )
            // 单本：书名 Title 样式（无独立章节标题）
            assertTrue(docXml.contains("""<w:pStyle w:val="Title"/>"""))
            assertTrue(docXml.contains("系列标题"))
            assertTrue(docXml.contains("内容一"))
            assertTrue(docXml.contains("引用内容"))
            // 插图不输出
            assertFalse(docXml.contains("example.com"))
            // 样式表含 Heading1/Heading2
            assertTrue(stylesXml.contains("Heading1"))
            assertTrue(stylesXml.contains("Heading2"))
        }
    }

    @Test
    fun `buildDocx 卷章节使用 Heading1 Heading2 层级`() {
        val chapters = listOf(
            sampleNovel(1L, "第一卷 相遇篇") to document(NovelBlock.Paragraph("卷内容")),
            sampleNovel(2L, "第一章 出发") to document(NovelBlock.Paragraph("章内容一")),
            sampleNovel(3L, "第二章 相遇") to document(NovelBlock.Paragraph("章内容二")),
            sampleNovel(4L, "番外 夏日") to document(NovelBlock.Paragraph("番外内容")),
        )
        val bytes = buildDocx(chapters, seriesTitle = "系列标题")
        var docXml = ""
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val content = zip.readBytes().toString(Charsets.UTF_8)
                if (entry.name == "word/document.xml") docXml = content
            }
        }
        // 书名 Title、卷 Heading1、卷下章节 Heading2（番外在卷后非卷 → 也归入卷下）
        assertTrue(docXml.contains("""<w:pStyle w:val="Title"/>"""))
        val h1Count = Regex("""<w:pStyle w:val="Heading1"/>""").findAll(docXml).count()
        val h2Count = Regex("""<w:pStyle w:val="Heading2"/>""").findAll(docXml).count()
        assertEquals(1, h1Count) // 第一卷
        assertEquals(3, h2Count) // 第一章 + 第二章 + 番外
        assertTrue(docXml.contains("第一卷 相遇篇"))
        assertTrue(docXml.contains("第一章 出发"))
        assertTrue(docXml.contains("番外 夏日"))
    }

    @Test
    fun `buildTocHierarchy 卷下含子章节且卷前章节为顶层`() {
        val chapters = listOf(
            sampleNovel(1L, "序章 开端") to document(),
            sampleNovel(2L, "第一卷 相遇篇") to document(),
            sampleNovel(3L, "第一章 出发") to document(),
            sampleNovel(4L, "第二章 相遇") to document(),
            sampleNovel(5L, "第二卷 离别篇") to document(),
            sampleNovel(6L, "第三章 告别") to document(),
        )
        val toc = buildTocHierarchy(chapters)
        assertEquals(3, toc.size)
        // 序章：顶层
        assertEquals("序章 开端", toc[0].title)
        assertFalse(toc[0].isVolume)
        assertTrue(toc[0].children.isEmpty())
        // 第一卷：含第一/二章
        assertEquals("第一卷 相遇篇", toc[1].title)
        assertTrue(toc[1].isVolume)
        assertEquals(listOf(3 to "第一章 出发", 4 to "第二章 相遇"), toc[1].children)
        // 第二卷：含第三章
        assertEquals("第二卷 离别篇", toc[2].title)
        assertTrue(toc[2].isVolume)
        assertEquals(listOf(6 to "第三章 告别"), toc[2].children)
    }

    @Test
    fun `docxHeading 标题加粗带字号且转义特殊字符`() {
        assertTrue(docxHeading("A&B", 1).contains("<w:b/>"))
        assertTrue(docxHeading("A&B", 1).contains("A&amp;B"))
        assertTrue(docxHeading("标题", 2).contains("<w:sz w:val=\"32\"/>"))
    }

    // ── EPUB ─────────────────────────────────────────────────────────────────

    private val sampleCss = """@font-face { font-family: "宋体"; }
p { text-indent: 2em; }"""

    @Test
    fun `buildEpub 生成标准 epub3 zip 结构`() {
        val chapters = listOf(
            sampleNovel(1L, "第一章 奈菲妮丝篇") to document(
                NovelBlock.Paragraph("内容一"),
                NovelBlock.Image("https://example.com/1.jpg"),
            ),
        )
        val bytes = buildEpub(
            chapters = chapters,
            seriesTitle = null,
            images = listOf(EpubImage("cover.jpg", byteArrayOf(1, 2, 3))),
            css = sampleCss,
        )

        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entries = 0
            var first = ""
            val names = mutableListOf<String>()
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entries == 0) first = entry.name
                names.add(entry.name)
                zip.readBytes()
                entries++
            }
            assertEquals("mimetype", first)
            assertTrue(names.contains("META-INF/container.xml"))
            assertTrue(names.contains("OEBPS/content.opf"))
            assertTrue(names.contains("OEBPS/nav.xhtml"))
            assertTrue(names.contains("OEBPS/toc.ncx"))
            assertTrue(names.contains("OEBPS/Styles/Main.css"))
            assertTrue(names.contains("OEBPS/Text/Section0.xhtml"))
            assertTrue(names.contains("OEBPS/Text/Section1.xhtml"))
            assertTrue(names.contains("OEBPS/Images/cover.jpg"))
            // 条目：mimetype + container + opf + nav + ncx + css + Section0 + Section1 + cover
            assertEquals(9, entries)
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
            css = sampleCss,
        )
        val xhtml = readChapter(bytes, 1)
        assertTrue(xhtml.contains("""src="../Images/img_0_1.jpg""""))
        assertFalse(xhtml.contains("img_0_0.jpg"))
    }

    @Test
    fun `buildEpub 章标题拆第N章徽标与章题并引用样式`() {
        val chapters = listOf(
            sampleNovel(1L, "第一章 奈菲妮丝篇") to document(NovelBlock.Paragraph("内容")),
        )
        val bytes = buildEpub(chapters, null, emptyList(), sampleCss)
        val xhtml = readChapter(bytes, 1)
        assertTrue(xhtml.contains("""<span class="Title-num">第一章</span><br/>"""))
        assertTrue(xhtml.contains("""<span class="Title-text">奈菲妮丝篇</span>"""))
        assertTrue(xhtml.contains("""<link href="../Styles/Main.css""""))
    }

    @Test
    fun `buildEpub 章标题前缀支持序章番外与第N卷`() {
        val chapters = listOf(
            sampleNovel(1L, "序章 起始之地") to document(NovelBlock.Paragraph("内容一")),
            sampleNovel(2L, "番外 夏日祭") to document(NovelBlock.Paragraph("内容二")),
            sampleNovel(3L, "第2卷 新篇") to document(NovelBlock.Paragraph("内容三")),
        )
        val bytes = buildEpub(chapters, null, emptyList(), sampleCss)
        val x1 = readChapter(bytes, 1)
        val x2 = readChapter(bytes, 2)
        val x3 = readChapter(bytes, 3)
        assertTrue(x1.contains("""<span class="Title-num">序章</span><br/>"""))
        assertTrue(x1.contains("""<span class="Title-text">起始之地</span>"""))
        assertTrue(x2.contains("""<span class="Title-num">番外</span><br/>"""))
        assertTrue(x2.contains("""<span class="Title-text">夏日祭</span>"""))
        assertTrue(x3.contains("""<span class="Title-num">第2卷</span><br/>"""))
        assertTrue(x3.contains("""<span class="Title-text">新篇</span>"""))
    }

    @Test
    fun `buildEpub 书名页与样式文件存在`() {
        val chapters = listOf(
            sampleNovel(1L, "测试小说") to document(NovelBlock.Paragraph("内容")),
        )
        val bytes = buildEpub(chapters, "系列标题", emptyList(), sampleCss)
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var section0 = ""
            var css = ""
            while (true) {
                val entry = zip.nextEntry ?: break
                val content = zip.readBytes().toString(Charsets.UTF_8)
                when (entry.name) {
                    "OEBPS/Text/Section0.xhtml" -> section0 = content
                    "OEBPS/Styles/Main.css" -> css = content
                }
            }
            assertTrue(section0.contains("<h1>系列标题</h1>"))
            assertTrue(css.contains(sampleCss))
        }
    }

    @Test
    fun `buildEpub 段落去全角缩进与分隔符渲染为破折号段落`() {
        val chapters = listOf(
            sampleNovel(1L, "第一章 奈菲妮丝篇") to document(
                NovelBlock.Paragraph("\u3000\u3000首段内容"),
                NovelBlock.Separator("——————"),
            ),
        )
        val bytes = buildEpub(chapters, null, emptyList(), sampleCss)
        val xhtml = readChapter(bytes, 1)
        assertTrue(xhtml.contains("<p>首段内容</p>"))
        assertFalse(xhtml.contains("\u3000\u3000"))
        assertTrue(xhtml.contains("<p>——————</p>"))
        assertFalse(xhtml.contains("<hr/>"))
    }

    @Test
    fun `buildEpub 卷章目录嵌套`() {
        val chapters = listOf(
            sampleNovel(1L, "第一卷 相遇篇") to document(NovelBlock.Paragraph("卷内容")),
            sampleNovel(2L, "第一章 出发") to document(NovelBlock.Paragraph("内容一")),
            sampleNovel(3L, "第二章 相遇") to document(NovelBlock.Paragraph("内容二")),
            sampleNovel(4L, "番外 夏日") to document(NovelBlock.Paragraph("内容三")),
        )
        val bytes = buildEpub(chapters, "系列标题", emptyList(), sampleCss)
        var nav = ""
        var ncx = ""
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val content = zip.readBytes().toString(Charsets.UTF_8)
                when (entry.name) {
                    "OEBPS/nav.xhtml" -> nav = content
                    "OEBPS/toc.ncx" -> ncx = content
                }
            }
        }
        // nav：卷为外层 li，卷下章节（含卷后的番外）在嵌套 ol 内
        assertTrue(nav.contains("""<li><a href="Text/Section1.xhtml">第一卷 相遇篇</a>"""))
        assertTrue(nav.contains("""<li><a href="Text/Section2.xhtml">第一章 出发</a></li>"""))
        assertTrue(nav.contains("""<li><a href="Text/Section3.xhtml">第二章 相遇</a></li>"""))
        assertTrue(nav.contains("""<li><a href="Text/Section4.xhtml">番外 夏日</a></li>"""))
        // 卷与子章节顺序：卷在前，子章节紧随其后，顶层章节最后
        val volIdx = ncx.indexOf("第一卷 相遇篇")
        val ch1Idx = ncx.indexOf("第一章 出发")
        val ch2Idx = ncx.indexOf("第二章 相遇")
        val fanIdx = ncx.indexOf("番外 夏日")
        assertTrue(volIdx != -1 && ch1Idx != -1 && ch2Idx != -1 && fanIdx != -1)
        assertTrue(ch1Idx > volIdx && ch2Idx > ch1Idx && fanIdx > ch2Idx)
        assertTrue(ncx.contains("""<content src="Text/Section1.xhtml"/>"""))
        assertTrue(ncx.contains("""<content src="Text/Section2.xhtml"/>"""))
    }

    // ── formatChapters（导出前格式化，对齐 format_novel） ──────────────────────

    @Test
    fun `formatChapters 合并硬换行段落`() {
        val chapters = listOf(
            sampleNovel(1L, "第一章") to document(
                NovelBlock.Paragraph("这是第一行"),
                NovelBlock.Paragraph("这是第二行。"),
                NovelBlock.Paragraph("独立段落。"),
            ),
        )
        val blocks = formatChapters(chapters)[0].second.blocks
        // 前两行合并为一段（末字符 。），第三行独立
        assertEquals(2, blocks.size)
        val first = blocks[0] as NovelBlock.Paragraph
        assertTrue(first.text.contains("这是第一行这是第二行。"))
        assertTrue(first.text.startsWith("\u3000\u3000"))
        assertEquals("独立段落。", (blocks[1] as NovelBlock.Paragraph).text.removePrefix("\u3000\u3000"))
    }

    @Test
    fun `formatChapters 卷章连续重排`() {
        val chapters = listOf(
            sampleNovel(1L, "第一章") to document(
                NovelBlock.Paragraph("第一卷 相遇篇"),
                NovelBlock.Paragraph("第一章 出发"),
                NovelBlock.Paragraph("第二章 相遇"),
                NovelBlock.Paragraph("序章 开端"),
            ),
            sampleNovel(2L, "第二章") to document(
                NovelBlock.Paragraph("第三章 告别"),
            ),
        )
        val formatted = formatChapters(chapters)
        val headings1 = formatted[0].second.blocks.filterIsInstance<NovelBlock.Heading>().map { it.text }
        val headings2 = formatted[1].second.blocks.filterIsInstance<NovelBlock.Heading>().map { it.text }
        // 照搬重排：忽略原标题编号，序章并入章节序号；章号跨章节连续
        assertEquals(listOf("第1卷 相遇篇", "第1章 出发", "第2章 相遇", "第3章 开端"), headings1)
        assertEquals(listOf("第4章 告别"), headings2)
    }

    @Test
    fun `formatChapters 标点规范化与词替换`() {
        val chapters = listOf(
            sampleNovel(1L, "第一章") to document(
                NovelBlock.Paragraph("hello, world: test!!"),
                NovelBlock.Paragraph("前穴被入侵了.."),
                NovelBlock.Paragraph("屁穴和后穴都要。"),
            ),
        )
        val texts = formatChapters(chapters)[0].second.blocks
            .filterIsInstance<NovelBlock.Paragraph>()
            .map { it.text.removePrefix("\u3000\u3000") }
        assertTrue(texts[0].contains("hello， world： test！！"))
        assertTrue(texts[1].contains("蜜穴被入侵了……"))
        assertTrue(texts[2].contains("菊穴和菊穴都要。"))
    }

    @Test
    fun `formatChapters 应用简繁转换器`() {
        val chapters = listOf(
            sampleNovel(1L, "第一章") to document(
                NovelBlock.Paragraph("繁體中文測試。"),
            ),
        )
        // 默认不转换（单测不依赖 OpenCC 原生库）；传入转换器时生效
        val untouched = formatChapters(chapters)[0].second.blocks
        assertTrue((untouched[0] as NovelBlock.Paragraph).text.contains("繁體中文測試。"))
        val converted = formatChapters(chapters) { it.replace("繁體", "简体").replace("測試", "测试") }[0].second.blocks
        assertTrue((converted[0] as NovelBlock.Paragraph).text.contains("简体中文测试。"))
    }

    @Test
    fun `formatChapters 保留图片分隔线与引用`() {
        val chapters = listOf(
            sampleNovel(1L, "第一章") to document(
                NovelBlock.Paragraph("第一段。"),
                NovelBlock.Image("https://example.com/1.jpg"),
                NovelBlock.Separator("——————"),
                NovelBlock.Quote("quote, ok!"),
                NovelBlock.Paragraph("第二段。"),
            ),
        )
        val blocks = formatChapters(chapters)[0].second.blocks
        assertTrue(blocks.any { it is NovelBlock.Image })
        assertTrue(blocks.any { it is NovelBlock.Separator })
        val quote = blocks.filterIsInstance<NovelBlock.Quote>().single()
        assertTrue(quote.text.contains("quote， ok！"))
    }

    // ── 内嵌图片（PDF/DOCX） ────────────────────────────────────────────────

    @Test
    fun `imageDimensions 解析 PNG 头宽高`() {
        // 最小 PNG：签名 8 字节 + IHDR（宽高 @16..23 = 100×50）
        val png = ByteArray(24).apply {
            set(0, 0x89.toByte()); set(1, 'P'.code.toByte()); set(2, 'N'.code.toByte()); set(3, 'G'.code.toByte())
            set(16, 0); set(17, 0); set(18, 0); set(19, 100)
            set(20, 0); set(21, 0); set(22, 0); set(23, 50)
        }
        assertEquals(100 to 50, imageDimensions(png))
    }

    @Test
    fun `imageDimensions 解析 JPEG SOF 宽高`() {
        // SOI + SOF0（length=17：高 60 宽 120，3 分量）
        val jpeg = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xC0.toByte(), 0x00, 0x11,
            0x08, 0x00, 0x3C, 0x00, 0x78, 0x03,
            0x01, 0x22, 0x00, 0x02, 0x11, 0x00, 0x03, 0x11, 0x00,
        )
        assertEquals(120 to 60, imageDimensions(jpeg))
    }

    @Test
    fun `imageDimensions 无法识别返回 null`() {
        assertEquals(null, imageDimensions(ByteArray(4) { 0 }))
    }

    @Test
    fun `mimeFromUrl 按扩展名推断`() {
        assertEquals("image/jpeg", mimeFromUrl("https://i.pximg.net/img-master/img/1/2/a.jpg"))
        assertEquals("image/png", mimeFromUrl("https://example.com/a.PNG?x=1"))
        assertEquals("image/webp", mimeFromUrl("https://example.com/a.webp"))
        assertEquals("image/jpeg", mimeFromUrl("https://example.com/noext"))
    }

    @Test
    fun `docxImage 生成 drawing 与缩放`() {
        val maxCx = 9026L * 635L
        // 1000×500px → 像素×9525 EMU 后超可用宽 → 缩放到可用宽，cy 等比例
        val img = DocxImage("image1.jpg", ByteArray(4), "image/jpeg", 1000, 500)
        val xml = docxImage(img, "rId2")
        assertTrue(xml.contains("""r:embed="rId2""""))
        assertTrue(xml.contains("""<wp:extent cx="$maxCx" cy="${maxCx / 2}""""))
        // 小图（20×10px）不缩放：cx = 20×9525
        val small = DocxImage("image2.jpg", ByteArray(4), "image/jpeg", 20, 10)
        val xmlSmall = docxImage(small, "rId3")
        assertTrue(xmlSmall.contains("""<wp:extent cx="${20L * 9525L}" cy="${10L * 9525L}""""))
    }

    @Test
    fun `buildDocx 内嵌插图写入 media 与关系`() {
        val chapters = listOf(
            sampleNovel(1L) to document(
                NovelBlock.Paragraph("第一段。"),
                NovelBlock.Image("https://example.com/1.jpg"),
            ),
        )
        val img = DocxImage("image1.jpg", byteArrayOf(0xFF.toByte(), 0xD8.toByte()), "image/jpeg", 100, 50)
        val bytes = buildDocx(chapters, seriesTitle = null, images = listOf(img))
        var docXml = ""
        var docRels = ""
        var contentTypes = ""
        var hasMedia = false
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val content = zip.readBytes().toString(Charsets.UTF_8)
                when (entry.name) {
                    "word/document.xml" -> docXml = content
                    "word/_rels/document.xml.rels" -> docRels = content
                    "[Content_Types].xml" -> contentTypes = content
                    "word/media/image1.jpg" -> hasMedia = true
                }
            }
        }
        assertTrue(hasMedia)
        assertTrue(docXml.contains("""r:embed="rId2""""))
        assertTrue(docXml.contains("wp:extent"))
        assertTrue("图片关系应在文档级 rels", docRels.contains("""Target="media/image1.jpg""""))
        assertTrue(contentTypes.contains("""Extension="jpg""""))
    }

    @Test
    fun `buildMarkdown 插图以 data URI 内嵌`() {
        val chapters = listOf(
            sampleNovel(1L) to document(
                NovelBlock.Paragraph("第一段。"),
                NovelBlock.Image("https://example.com/1.jpg"),
                NovelBlock.Paragraph("第二段。"),
            ),
        )
        val img = DocxImage("image1.jpg", byteArrayOf(0xFF.toByte(), 0xD8.toByte()), "image/jpeg", 10, 5)
        val md = buildMarkdown(chapters, seriesTitle = null, images = listOf(img))
        assertTrue("md 应含 data URI 图片", md.contains("""![插图](data:image/jpeg;base64,/9g=)"""))
        // 无图片时不输出 data URI
        assertFalse(buildMarkdown(chapters, null).contains("data:"))
    }

    /** 读取指定章节 xhtml。 */
    private fun readChapter(bytes: ByteArray, section: Int): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val content = zip.readBytes().toString(Charsets.UTF_8)
                if (entry.name == "OEBPS/Text/Section$section.xhtml") return content
            }
        }
        return ""
    }
}

