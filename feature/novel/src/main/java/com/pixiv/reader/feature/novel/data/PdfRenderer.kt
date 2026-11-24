package com.pixiv.reader.feature.novel.data

import android.content.Context
import android.util.Log
import com.tom_roush.fontbox.ttf.TTFParser
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

internal object PdfFonts {

    private fun isFontFile(name: String) =
        name.endsWith(".ttf") || name.endsWith(".otf") || name.endsWith(".ttc")

    fun systemSymbolCandidates(fontsDir: File): List<File> {
        val files = fontsDir.listFiles() ?: return emptyList()
        return files.filter { f ->
            if (!f.isFile || !isFontFile(f.name.lowercase())) return@filter false
            val name = f.name.lowercase()
            name.contains("symbol") || (name.contains("emoji") && !name.contains("color"))
        }.sortedBy { it.name }
    }

    fun systemCjkCandidates(fontsDir: File): List<File> {
        val files = fontsDir.listFiles() ?: return emptyList()
        return files.filter { f ->
            if (!f.isFile || !isFontFile(f.name.lowercase())) return@filter false
            val name = f.name.lowercase()
            if (name.contains("symbol") || name.contains("emoji") || name.contains("color")) {
                return@filter false
            }
            if (hasCffTable(f)) return@filter false
            hasCjkGlyph(f)
        }.sortedWith(compareBy({ !it.name.lowercase().contains("droid") }, { it.name }))
    }

    /** 检查 cmap 是否同时覆盖汉字（U+4E00）与平假名（U+3042）。 */
    fun hasCjkGlyph(file: File): Boolean = runCatching {
        val ttf = TTFParser().parse(file)
        ttf.use { ttf ->
            val cmap = ttf.unicodeCmapLookup ?: return@runCatching false
            cmap.getGlyphId(0x4E00) != 0 && cmap.getGlyphId(0x3042) != 0
        }
    }.getOrDefault(false)

    /** pdfbox-android 仅支持嵌入 glyf（TrueType）轮廓，CFF/OTF 字体在 save 阶段会抛异常；TTC 与解析失败保守跳过。 */
    fun hasCffTable(file: File): Boolean = try {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(0)
            val tag0 = ByteArray(4)
            raf.readFully(tag0)
            if (tag0.decodeToString() == "ttcf") return true
            raf.seek(4)
            val numTables = raf.readUnsignedShort()
            for (i in 0 until numTables) {
                raf.seek(12L + i * 16L)
                val tag = ByteArray(4)
                raf.readFully(tag)
                if (tag.decodeToString() == "CFF ") return true
            }
            false
        }
    } catch (_: Exception) {
        true
    }
}

internal data class PdfLayoutSettings(
    val fontSize: Float,
    val lineHeightMultiplier: Float,
    val indentCount: Int,
    val paragraphSpacingEm: Float,
)

internal sealed class PdfContent {
    data class Text(val text: String, val indent: Boolean) : PdfContent()
    data class Picture(val bytes: ByteArray, val mime: String) : PdfContent() {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Picture

            if (!bytes.contentEquals(other.bytes)) return false
            if (mime != other.mime) return false

            return true
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + mime.hashCode()
            return result
        }
    }
}

internal object PdfRenderer {

    private const val TAG = "PdfRenderer"

    private const val ASSET_CJK_FONT = "pdf_fonts/DroidSansFallbackFull.ttf"

    private const val ASSET_SYMBOL_FONT = "pdf_fonts/DejaVuSans.ttf"

    @Volatile
    private var cachedCjkCandidates: List<File>? = null

    fun buildPdf(
        context: Context,
        contents: List<PdfContent>,
        layout: PdfLayoutSettings
    ): ByteArray {
        // pdfbox-android 的 cmap/afm 在 assets 中，加载字体前必须初始化（重复调用幂等），否则报 cmap/afm not found
        PDFBoxResourceLoader.init(context)
        val doc = PDDocument()
        doc.use { doc ->
            val fonts = loadPdfFonts(context, doc)
            if (fonts.isEmpty()) {
                Log.w(TAG, "buildPdf 无可用系统中文字体，退化为 Helvetica（中文将不显示）")
            } else {
                Log.d(
                    TAG,
                    "buildPdf 使用字体 [${
                        fonts.joinToString(", ") {
                            runCatching { it.name }.getOrDefault("?")
                        }
                    }]，开始渲染内容块=${contents.size}"
                )
            }
            renderPdf(doc, fonts, contents, layout)
            Log.d(TAG, "buildPdf 渲染完成 页数=${doc.numberOfPages}")
            val out = ByteArrayOutputStream()
            doc.save(out)
            Log.d(TAG, "buildPdf 保存完成 size=${out.size()} bytes")
            return out.toByteArray()
        }
    }

    /** 按优先级加载字体（系统 glyf CJK → 打包 CJK → 打包符号 → 系统 glyf 符号），渲染时逐字符取首个可编码字体；全失败返回空列表退化 Helvetica。 */
    private fun loadPdfFonts(context: Context, doc: PDDocument): List<PDFont> {
        val fonts = mutableListOf<PDFont>()
        val systemCjk = cachedCjkCandidates ?: PdfFonts.systemCjkCandidates(File("/system/fonts"))
            .also { cachedCjkCandidates = it }
        for (file in systemCjk) {
            val result = runCatching { file.inputStream().use { PDType0Font.load(doc, it, true) } }
            if (result.isSuccess) {
                Log.d(
                    TAG,
                    "系统 CJK 字体加载成功 path=${file.path} font=${
                        runCatching { result.getOrThrow().name }.getOrDefault("?")
                    }"
                )
                fonts += result.getOrThrow()
                break
            }
            Log.w(TAG, "系统 CJK 字体加载失败 path=${file.path}", result.exceptionOrNull())
        }
        for ((asset, desc) in listOf(
            ASSET_CJK_FONT to "打包中文字体",
            ASSET_SYMBOL_FONT to "打包符号字体",
        )) {
            runCatching { context.assets.open(asset).use { PDType0Font.load(doc, it, true) } }
                .onSuccess {
                    Log.d(
                        TAG,
                        "${desc}加载成功 $asset font=${runCatching { it.name }.getOrDefault("?")}"
                    )
                    fonts += it
                }
                .onFailure { e -> Log.w(TAG, "${desc}加载失败 $asset", e) }
        }
        for (file in PdfFonts.systemSymbolCandidates(File("/system/fonts"))) {
            if (PdfFonts.hasCffTable(file)) {
                Log.d(TAG, "跳过 CFF 轮廓符号字体 path=${file.path}")
                continue
            }
            runCatching { file.inputStream().use { PDType0Font.load(doc, it, true) } }
                .onSuccess {
                    Log.d(
                        TAG,
                        "系统符号字体加载成功 path=${file.path} font=${
                            runCatching { it.name }.getOrDefault("?")
                        }"
                    )
                    fonts += it
                }
                .onFailure { e -> Log.w(TAG, "系统符号字体加载失败 path=${file.path}", e) }
        }
        if (fonts.isEmpty()) Log.w(TAG, "无可用字体，退化为 Helvetica")
        return fonts
    }

    private fun renderPdf(
        doc: PDDocument,
        fonts: List<PDFont>,
        contents: List<PdfContent>,
        layout: PdfLayoutSettings
    ) {
        val fontSize = layout.fontSize
        val leading = fontSize * layout.lineHeightMultiplier
        val margin = 50f
        val indentWidth = fontSize * layout.indentCount
        val paragraphGap = fontSize * layout.paragraphSpacingEm
        var page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        val usableWidth = page.mediaBox.width - margin * 2
        val usableHeight = page.mediaBox.height - margin * 2
        var y = page.mediaBox.height - margin
        var content = PDPageContentStream(doc, page)
        var inTextBlock = false
        try {
            content.beginText()
            inTextBlock = true
            // 无可用字体时退化为 Helvetica；必须 setFont 否则 showText 抛异常
            val primary = fonts.firstOrNull() ?: PDType1Font.HELVETICA
            content.setFont(primary, fontSize)
            content.newLineAtOffset(margin, y)

            var newPageCount = 0
            var showTextFailCount = 0
            var noGlyphCharCount = 0
            var pictureCount = 0
            var pictureSkipCount = 0
            // 跨行跟踪 content 的当前字体（PDPageContentStream 无 getter，须与每次
            // setFont/newPage/图片恢复同步；否则下一行首段误判「已设置」而不切换，
            // 用上一行末尾的符号字体绘制中文 → No glyph）
            var activeFont: PDFont = primary

            fun newPage() {
                if (inTextBlock) {
                    content.endText()
                    inTextBlock = false
                }
                content.close()
                page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                content = PDPageContentStream(doc, page)
                y = page.mediaBox.height - margin
                content.beginText()
                inTextBlock = true
                content.setFont(primary, fontSize)
                content.newLineAtOffset(margin, y)
                activeFont = primary
                newPageCount++
            }

            var lineCount = 0
            var isFirstContentLine = true // 全文首行（主标题）不缩进
            for (item in contents) {
                when (item) {
                    is PdfContent.Picture -> {
                        val img = runCatching {
                            PDImageXObject.createFromByteArray(doc, item.bytes, item.mime)
                        }.getOrNull()
                        if (img == null) {
                            pictureSkipCount++
                            Log.w(TAG, "图片内嵌失败（格式不支持或损坏）mime=${item.mime}")
                            continue
                        }
                        val w = img.width.toFloat()
                        val h = img.height.toFloat()
                        if (w <= 0f || h <= 0f) continue
                        // 同时受可用宽、高限制等比缩放，防止竖长图跨页溢出裁切。
                        val scale = minOf(1f, usableWidth / w, usableHeight / h)
                        val drawW = w * scale
                        val drawH = h * scale
                        if (paragraphGap > 0f) y -= paragraphGap
                        if (y - drawH < margin) newPage()
                        // drawImage 不能在文本块（BT...ET）内执行：先结束文本块，绘制后恢复
                        if (inTextBlock) {
                            content.endText()
                            inTextBlock = false
                        }
                        // 图片 y 为 PDF 底部坐标：顶边对齐当前文本游标
                        content.drawImage(img, margin, y - drawH, drawW, drawH)
                        y -= drawH
                        content.beginText()
                        inTextBlock = true
                        content.setFont(primary, fontSize)
                        content.newLineAtOffset(margin, y)
                        activeFont = primary
                        pictureCount++
                    }

                    is PdfContent.Text -> {
                        var atParagraphStart = true
                        for (line in item.text.lines()) {
                            if (line.isBlank()) {
                                // 空行 = 段落/块边界：渲染段距空隙（阅读器段距语义，非整行行距）
                                if (paragraphGap > 0f) {
                                    if (y < margin + paragraphGap) newPage()
                                    content.newLineAtOffset(0f, -paragraphGap)
                                    y -= paragraphGap
                                }
                                atParagraphStart = true
                                continue
                            }
                            // 无法被任一字体编码的字符置 null（跳过不画而非替换 '?'）
                            val glyphs = line.map { ch ->
                                fonts.firstOrNull { runCatching { it.encode(ch.toString()) }.isSuccess }
                                    ?.let { ch to it }
                            }
                            noGlyphCharCount += glyphs.count { it == null }
                            val firstRowIndent =
                                if (atParagraphStart && !isFirstContentLine && item.indent) {
                                    indentWidth
                                } else {
                                    0f
                                }
                            val rows = mutableListOf<List<Pair<Char, PDFont>>>()
                            var start = 0
                            var rowLimit = (usableWidth - firstRowIndent).coerceAtLeast(fontSize)
                            var width = 0f
                            for (i in glyphs.indices) {
                                val g = glyphs[i]
                                val charWidth =
                                    g?.let { (c, f) -> f.getStringWidth(c.toString()) * fontSize / 1000f }
                                        ?: 0f
                                if (width + charWidth > rowLimit && i > start) {
                                    rows += glyphs.subList(start, i).filterNotNull()
                                    start = i
                                    rowLimit = usableWidth
                                    width = charWidth
                                } else {
                                    width += charWidth
                                }
                            }
                            if (start < glyphs.size) {
                                rows += glyphs.subList(start, glyphs.size).filterNotNull()
                            }
                            rows.forEachIndexed { rowIndex, row ->
                                if (y < margin + leading) newPage()
                                val indent = if (rowIndex == 0) firstRowIndent else 0f
                                if (indent > 0f) content.newLineAtOffset(indent, 0f)
                                var i = 0
                                while (i < row.size) {
                                    val (_, f) = row[i]
                                    if (f !== activeFont) {
                                        content.setFont(f, fontSize)
                                        activeFont = f
                                    }
                                    var j = i + 1
                                    while (j < row.size && row[j].second === f) j++
                                    val seg = row.subList(i, j).joinToString("") { it.first.toString() }
                                    // showText 内部处理 () \ 转义，勿再手动转义；异常时跳过该段保证导出不中断
                                    try {
                                        content.showText(seg)
                                    } catch (e: Exception) {
                                        showTextFailCount++
                                        Log.w(TAG, "showText 失败，跳过该段 长度=${seg.length}", e)
                                    }
                                    i = j
                                }
                                content.newLineAtOffset(-indent, -leading)
                                y -= leading
                                atParagraphStart = false
                                isFirstContentLine = false
                                lineCount++
                            }
                        }
                    }
                }
            }
            if (inTextBlock) {
                content.endText()
                inTextBlock = false
            }
            Log.d(
                TAG,
                "renderPdf 完成 总行数=$lineCount 新增页=$newPageCount 总页数=${doc.numberOfPages} " +
                    "showText失败段=$showTextFailCount 无字形字符=$noGlyphCharCount 图片=$pictureCount 图片跳过=$pictureSkipCount",
            )
        } finally {
            runCatching { content.close() }
        }
    }
}
