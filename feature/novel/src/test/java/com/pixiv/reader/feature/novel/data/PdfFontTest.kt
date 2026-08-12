package com.pixiv.reader.feature.novel.data

import com.tom_roush.fontbox.ttf.TTFParser
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PDF 字体链路 JVM 单测（无 Android 依赖）：
 * - 打包字体文件本身：glyf 轮廓（pdfbox 可嵌入）、无 CFF 表、关键字符覆盖
 * - 完整 PDF 生成：加载字体 → 渲染中文+❤ → save → 重新打开校验
 * - 工具函数：符号字体目录扫描、CFF 轮廓检测
 */
class PdfFontTest {

    private val cjkFontResource = "/pdf_fonts/DroidSansFallbackFull.ttf"
    private val symbolFontResource = "/pdf_fonts/DejaVuSans.ttf"

    private fun resourceFile(res: String): File {
        val url = javaClass.getResource(res)
        assertNotNull("测试资源缺失 $res", url)
        return File(url!!.toURI())
    }

    /** 构造含 "CFF " 表的最小 sfnt（模拟 CFF/OTF 轮廓字体）。 */
    private fun fakeCffSfnt(): ByteArray = ByteArrayOutputStream().apply {
        write(byteArrayOf(0, 1, 0, 0))          // sfnt version 0x00010000
        write(byteArrayOf(0, 1))                // numTables = 1
        write(byteArrayOf(0, 0))                // searchRange
        write(byteArrayOf(0, 0))                // entrySelector
        write(byteArrayOf(0, 0))                // rangeShift
        write("CFF ".toByteArray())             // tag @12
        write(byteArrayOf(0, 0, 0, 0))          // checkSum
        write(byteArrayOf(0, 0, 0, 28))         // offset
        write(byteArrayOf(0, 0, 0, 4))          // length
    }.toByteArray()

    @Test
    fun `打包中文字体为 glyf 轮廓且无 CFF 表（pdfbox 可嵌入）`() {
        val ttf = TTFParser().parse(resourceFile(cjkFontResource))
        val tables = ttf.tableMap
        assertTrue("必须有 glyf 表（TrueType 轮廓）", tables.containsKey("glyf"))
        assertFalse("不能有 CFF 表（pdfbox 无法嵌入 CFF）", tables.containsKey("CFF "))
        ttf.close()
    }

    @Test
    fun `打包中文字体在 pdfbox 选择子表下覆盖中文与标点`() {
        val ttf = TTFParser().parse(resourceFile(cjkFontResource))
        val cmap = ttf.unicodeCmap
        assertNotNull(cmap)
        // 中文基本区、全角标点（pdfbox 实际编码走 getUnicodeCmap）
        assertTrue("U+4E00 一", cmap!!.getGlyphId(0x4E00) != 0)
        assertTrue("U+9F99 龙", cmap.getGlyphId(0x9F99) != 0)
        assertTrue("U+3002 。", cmap.getGlyphId(0x3002) != 0)
        assertTrue("U+FF01 ！", cmap.getGlyphId(0xFF01) != 0)
        ttf.close()
    }

    @Test
    fun `打包符号字体在 pdfbox 选择子表下覆盖心形`() {
        val ttf = TTFParser().parse(resourceFile(symbolFontResource))
        val cmap = ttf.unicodeCmap
        assertNotNull(cmap)
        assertTrue("U+2764 ❤", cmap!!.getGlyphId(0x2764) != 0)
        assertTrue("U+2605 ★", cmap.getGlyphId(0x2605) != 0)
        ttf.close()
    }

    @Test
    fun `完整 PDF 生成：双字体渲染中文与心形并保存`() {
        val doc = PDDocument()
        val bytes = doc.use { d ->
            // 与 NovelExporter.loadPdfFonts 相同的加载路径（JVM 下 cmap 资源走 test classpath）
            val cjk = resourceFile(cjkFontResource).inputStream().use { PDType0Font.load(d, it, true) }
            val symbol = resourceFile(symbolFontResource).inputStream().use { PDType0Font.load(d, it, true) }
            val page = PDPage()
            d.addPage(page)
            PDPageContentStream(d, page).use { cs ->
                cs.beginText()
                cs.setFont(cjk, 12f)
                cs.newLineAtOffset(50f, 750f)
                cs.showText("小说导出测试：你好世界长途火车艳遇")
                // 切换符号字体绘制 ❤（CJK 字体无此字形）
                cs.setFont(symbol, 12f)
                cs.showText(" ❤ ★")
                cs.endText()
            }
            val out = ByteArrayOutputStream()
            d.save(out)
            out.toByteArray()
        }
        assertTrue("PDF 字节必须非空", bytes.isNotEmpty())
        // 重新打开校验可解析且页数正确
        PDDocument.load(ByteArrayInputStream(bytes)).use { reloaded ->
            assertEquals(1, reloaded.numberOfPages)
        }
    }

    @Test
    fun `CJK 候选发现：glyf 中文字体命中，符号与 CFF 被过滤`() {
        val dir = createTempDir("cjkscan").apply {
            // 真实中文 glyf 字体（模拟老设备 DroidSansFallback.ttf）
            resourceFile(cjkFontResource).copyTo(File(this, "DroidSansFallback.ttf"))
            // 符号字体（名字排除）
            resourceFile(symbolFontResource).copyTo(File(this, "NotoSansSymbols-Regular.ttf"))
            // 假 CFF 字体（不可嵌入）
            File(this, "NotoSansCJK-Regular.otf").writeBytes(fakeCffSfnt())
            File(this, "Roboto-Regular.ttf").writeBytes(byteArrayOf(0, 0, 0, 0))
        }
        try {
            val found = PdfFonts.systemCjkCandidates(dir).map { it.name }
            assertEquals(listOf("DroidSansFallback.ttf"), found)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `cmap 中文检测：中文字体 true，纯符号字体 false`() {
        assertTrue("DSFF 应覆盖中文", PdfFonts.hasCjkGlyph(resourceFile(cjkFontResource)))
        assertFalse("DejaVuSans 无中文", PdfFonts.hasCjkGlyph(resourceFile(symbolFontResource)))
    }

    @Test
    fun `多字体混合渲染烟雾测试：跨行字体切换与图片绘制`() {
        // 复刻 renderPdf 的字体状态管理：跨行跟踪 activeFont（修复前的行内重置
        // 会导致上一行末尾符号字体被用于下一行中文段 → No glyph）。
        // 内容含 ❤（符号字体）+ 中文（主字体）交错 + 图片绘制（drawImage 需结束文本块）。
        val doc = PDDocument()
        val bytes = doc.use { d ->
            val cjk = resourceFile(cjkFontResource).inputStream().use { PDType0Font.load(d, it, true) }
            val symbol = resourceFile(symbolFontResource).inputStream().use { PDType0Font.load(d, it, true) }
            val fonts = listOf<com.tom_roush.pdfbox.pdmodel.font.PDFont>(cjk, symbol)
            val primary = fonts.first()
            val page = PDPage()
            d.addPage(page)
            val cs = PDPageContentStream(d, page)
            val fontSize = 17f
            val leading = fontSize * 2.05f
            var y = page.mediaBox.height - 50f
            cs.beginText()
            cs.setFont(primary, fontSize)
            cs.newLineAtOffset(50f, y)
            var activeFont: com.tom_roush.pdfbox.pdmodel.font.PDFont = primary

            fun drawSeg(f: com.tom_roush.pdfbox.pdmodel.font.PDFont, seg: String) {
                if (f !== activeFont) {
                    cs.setFont(f, fontSize)
                    activeFont = f
                }
                cs.showText(seg) // 字体状态错误时此处抛 No glyph
            }

            // 行1：中文 + 符号交错（行尾字体 = 符号字体）
            drawSeg(cjk, "作系下播多")
            drawSeg(symbol, "❤")
            cs.newLineAtOffset(0f, -leading)
            y -= leading
            // 行2：首段中文（跨行 activeFont 必须正确切换回主字体）
            drawSeg(cjk, "中文混排测试")
            cs.newLineAtOffset(0f, -leading)
            y -= leading
            // 图片：drawImage 前必须结束文本块，之后恢复文本状态
            // （JVM 测试环境用 JPEG——pdfbox-android 的 PNG 解码依赖 Android Bitmap）
            cs.endText()
            val jpeg = java.util.Base64.getDecoder().decode(
                "/9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AVN//2Q=="
            )
            val img = PDImageXObject.createFromByteArray(d, jpeg, "image/jpeg")
            cs.drawImage(img, 50f, y - 30f, 30f, 30f)
            y -= 30f
            cs.beginText()
            cs.setFont(primary, fontSize)
            activeFont = primary
            cs.newLineAtOffset(50f, y)
            // 图片后中文行
            drawSeg(cjk, "图片后继续中文")
            cs.endText()
            cs.close()
            val out = java.io.ByteArrayOutputStream()
            d.save(out)
            out.toByteArray()
        }
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun `符号字体目录扫描：按名字特征命中且排除彩色 emoji`() {
        val dir = createTempDir("fontscan").apply {
            File(this, "NotoSansSymbols-Regular.ttf").writeBytes(byteArrayOf(0))
            File(this, "NotoSansSymbols2-Regular.otf").writeBytes(byteArrayOf(0))
            File(this, "NotoColorEmoji.ttf").writeBytes(byteArrayOf(0))
            File(this, "Roboto-Regular.ttf").writeBytes(byteArrayOf(0))
            File(this, "readme.txt").writeText("x")
        }
        try {
            val found = PdfFonts.systemSymbolCandidates(dir).map { it.name }
            assertEquals(listOf("NotoSansSymbols-Regular.ttf", "NotoSansSymbols2-Regular.otf"), found)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `CFF 检测：glyf 字体返回 false`() {
        assertFalse(PdfFonts.hasCffTable(resourceFile(cjkFontResource)))
    }

    @Test
    fun `CFF 检测：含 CFF 表的字体返回 true`() {
        val dir = createTempDir("cffcheck")
        try {
            val f = File(dir, "fake-cff.otf")
            f.writeBytes(fakeCffSfnt())
            assertTrue(PdfFonts.hasCffTable(f))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `CFF 检测：TTC 容器保守返回 true`() {
        val dir = createTempDir("ttccheck")
        try {
            val f = File(dir, "fake.ttc")
            f.writeBytes("ttcf".toByteArray() + ByteArray(32))
            assertTrue(PdfFonts.hasCffTable(f))
        } finally {
            dir.deleteRecursively()
        }
    }
}
