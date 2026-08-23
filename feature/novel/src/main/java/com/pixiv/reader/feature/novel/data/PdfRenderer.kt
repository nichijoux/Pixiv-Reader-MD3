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

/**
 * PDF 字体发现工具（纯 JVM 可测，无 Android 依赖）：
 * 目录扫描 + CFF 轮廓检测（pdfbox-android 只能嵌入 glyf 轮廓字体）。
 */
internal object PdfFonts {

    private fun isFontFile(name: String) =
        name.endsWith(".ttf") || name.endsWith(".otf") || name.endsWith(".ttc")

    /**
     * 扫描系统字体目录，返回符号字体候选（排除彩色 emoji 位图字体）。
     * 匹配规则：文件名含 "symbol"，或含 "emoji" 且非彩色（单色 emoji 字体）；
     * 不依赖具体文件名，OEM 定制命名也能命中。
     */
    fun systemSymbolCandidates(fontsDir: File): List<File> {
        val files = fontsDir.listFiles() ?: return emptyList()
        return files.filter { f ->
            if (!f.isFile || !isFontFile(f.name.lowercase())) return@filter false
            val name = f.name.lowercase()
            name.contains("symbol") || (name.contains("emoji") && !name.contains("color"))
        }.sortedBy { it.name }
    }

    /**
     * 扫描系统字体目录，返回可作为 PDF 主字体的 CJK 候选（glyf 轮廓 + cmap 覆盖中文）。
     * 与 PdfFontTest 使用同一判定逻辑（[hasCjkGlyph]），适配才返回：
     * - 排除符号/emoji/彩色字体（不负责中文）
     * - CFF 轮廓（pdfbox 无法嵌入）经 [hasCffTable] 过滤；TTC 容器保守跳过
     * - 剩余文件逐个 TTFParser 解析验证中文（U+4E00）与假名（U+3042）字形
     * 现代设备通常返回空（系统 CJK 全为 CFF），老设备可命中 DroidSansFallback.ttf，
     * OEM 定制 TrueType 中文字体（如 MiSans）也能自动发现。结果按偏好排序（Droid 优先）。
     */
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

    /** 解析字体并检查 cmap 是否覆盖中文（U+4E00）与常用假名（U+3042）。 */
    fun hasCjkGlyph(file: File): Boolean = runCatching {
        val ttf = TTFParser().parse(file)
        ttf.use { ttf ->
            val cmap = ttf.unicodeCmapLookup ?: return@runCatching false
            cmap.getGlyphId(0x4E00) != 0 && cmap.getGlyphId(0x3042) != 0
        }
    }.getOrDefault(false)

    /**
     * 检测字体文件是否为 CFF/OTF 轮廓（读 sfnt 表目录查 "CFF " 表）。
     * pdfbox-android 的子集化/完整嵌入都只支持 glyf（TrueType）轮廓，
     * CFF 字体（如系统 NotoSansCJK）嵌入会在 save 阶段抛异常，必须跳过。
     * TTC 容器 / 解析失败保守返回 true（跳过）。
     */
    fun hasCffTable(file: File): Boolean = try {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(0)
            val tag0 = ByteArray(4)
            raf.readFully(tag0)
            if (tag0.decodeToString() == "ttcf") return true // TTC 容器不做子字体级判定，跳过
            raf.seek(4) // 跳过 sfnt version
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
        true // 无法解析时保守视为不可嵌入
    }
}

/** PDF 排版参数（对齐阅读器设置：字号/行距/段首缩进/段距）。 */
internal data class PdfLayoutSettings(
    val fontSize: Float,
    val lineHeightMultiplier: Float,
    val indentCount: Int,
    val paragraphSpacingEm: Float,
)

/** PDF 内容元素流：文本块（块首行是否缩进由 [indent] 控制，标题/元数据不缩进）或内嵌图片。 */
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

/**
 * PDF 渲染器（pdfbox-android）：A4 分页，加载系统中日文字体，按元素流绘制并自动换行/分页；
 * 排版参数对齐阅读器设置（缩进/段距/字号/行距）。
 *
 * 从 NovelExporter 拆分（原单类五职责之一）：字体发现/加载/排版渲染与导出编排解耦。
 */
internal object PdfRenderer {

    private const val TAG = "PdfRenderer"

    /** 打包的 PDF 中文字体（AOSP DroidSansFallbackFull，TrueType/glyf 轮廓）。 */
    private const val ASSET_CJK_FONT = "pdf_fonts/DroidSansFallbackFull.ttf"

    /** 打包的 PDF 符号字体（DejaVu Sans，含 ❤ 等 CJK 缺失字形）。 */
    private const val ASSET_SYMBOL_FONT = "pdf_fonts/DejaVuSans.ttf"

    /** 系统 CJK 候选扫描结果缓存（进程内有效；字体文件变更需重启进程）。 */
    @Volatile
    private var cachedCjkCandidates: List<File>? = null

    /**
     * 生成 PDF 字节（pdfbox-android）。
     */
    fun buildPdf(
        context: Context,
        contents: List<PdfContent>,
        layout: PdfLayoutSettings
    ): ByteArray {
        // pdfbox-android 的 cmap/afm 等运行时资源打包在 APK assets 里，使用前必须先初始化
        // 资源加载器；否则 PDType0Font.load 抛 "Could not find referenced cmap stream
        // Identity-H"、PDType1Font（Helvetica 退化路径）类初始化抛 "afm not found"。
        // 重复调用幂等（内部 isReady 守卫）。
        PDFBoxResourceLoader.init(context)
        val doc = PDDocument()
        doc.use { doc ->
            val fonts = loadPdfFonts(context, doc)
            if (fonts.isEmpty()) {
                // 无可用系统中文字体：退化为 Helvetica（仅 Latin-1 字形，中文不显示；无字形字符跳过不画）
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

    /**
     * 加载 PDF 渲染字体集（pdfbox-android；仅真机验证）。
     *
     * 返回按优先级排序的字体列表，渲染时逐字符选择第一个能编码该字符的字体：
     * 1. 系统 CJK 字体（glyf 轮廓且 cmap 覆盖中文，[PdfFonts.systemCjkCandidates] 自动
     *    发现）：老设备 DroidSansFallback.ttf、OEM 定制 TrueType 中文字体直接复用，
     *    免打包体积；现代设备系统 CJK 全为 CFF 不可嵌入，此步通常为空。
     * 2. 打包中文字体 `assets/pdf_fonts/DroidSansFallbackFull.ttf`（AOSP，Apache 2.0）兜底。
     * 3. 打包符号字体 `assets/pdf_fonts/DejaVuSans.ttf`（双许可，含 ❤ U+2764 等
     *    Dingbats/杂项符号——CJK 字体不覆盖这些字形）。
     * 4. 系统符号字体（glyf）：目录扫描自动发现，CFF 经 [PdfFonts.hasCffTable] 过滤。
     * 全部失败返回空列表（调用方退化为 Helvetica，仅 Latin-1 字形）。
     */
    private fun loadPdfFonts(context: Context, doc: PDDocument): List<PDFont> {
        val fonts = mutableListOf<PDFont>()
        // 1. 系统 CJK（进程内缓存扫描结果，避免每次导出全目录解析）
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
        // 2-3. 打包字体兜底
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
        // 4. 系统符号字体（glyf）：目录扫描自动发现，CFF 轮廓（pdfbox 无法嵌入）跳过
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

    /**
     * 按元素流绘制 PDF（pdfbox-android）：
     * - [PdfContent.Text]：段落文本行——空行（段落边界）渲染段距空隙；段落首行缩进
     *   （[PdfContent.Text.indent]=false 的标题/元数据块不缩进）；超宽按字符断行、到底换页
     * - [PdfContent.Picture]：内嵌插图（jpg/png），按可用宽度等比缩放居中绘制，
     *   高度不够自动换页；格式不支持（webp 等）或损坏的图片跳过
     *
     * 排版对齐阅读器设置（[PdfLayoutSettings]）：字号、行距（1.6+增量）、
     * 段首缩进（字号 × 缩进数）、段距（em × 字号）。
     *
     * 多字体渲染：每个字符选用字体列表中第一个能编码它的字体（主中文字体优先，
     * 符号字体兜底 ❤ 等 CJK 缺字形符号）；所有字体都无法编码的字符跳过不画
     * （不替换为 '?'，保持原文语义）。字体切换通过同一文本对象内 setFont(Tf) 实现，
     * 不影响文本基线位置。
     */
    private fun renderPdf(
        doc: PDDocument,
        fonts: List<PDFont>,
        contents: List<PdfContent>,
        layout: PdfLayoutSettings
    ) {
        val fontSize = layout.fontSize
        val leading = fontSize * layout.lineHeightMultiplier
        val margin = 50f
        // 段首缩进 = 全角字宽（= 字号）× 缩进数；段距空隙 = 段距(em) × 字号
        val indentWidth = fontSize * layout.indentCount
        val paragraphGap = fontSize * layout.paragraphSpacingEm
        var page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        val usableWidth = page.mediaBox.width - margin * 2
        var y = page.mediaBox.height - margin
        var content = PDPageContentStream(doc, page)
        content.beginText()
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
            content.endText()
            content.close()
            page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            content = PDPageContentStream(doc, page)
            y = page.mediaBox.height - margin
            content.beginText()
            content.setFont(primary, fontSize)
            content.newLineAtOffset(margin, y)
            activeFont = primary // 新页字体重置为 primary，同步跟踪变量
            newPageCount++
        }

        var lineCount = 0
        var isFirstContentLine = true // 全文首行（主标题）不缩进
        for (item in contents) {
            when (item) {
                is PdfContent.Picture -> {
                    // 插图：jpg/png 内嵌（webp 等 pdfbox 不支持 → 跳过）
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
                    // 可用宽度内等比缩放；图片块视为段落边界（前后留段距）
                    val scale = minOf(1f, usableWidth / w)
                    val drawW = w * scale
                    val drawH = h * scale
                    if (paragraphGap > 0f) y -= paragraphGap
                    if (y - drawH < margin) newPage()
                    // drawImage 不能在文本块（BT...ET）内执行：先结束文本块，绘制后恢复
                    content.endText()
                    // 图片 y 为 PDF 底部坐标：顶边对齐当前文本游标
                    content.drawImage(img, margin, y - drawH, drawW, drawH)
                    y -= drawH
                    content.beginText()
                    content.setFont(primary, fontSize)
                    content.newLineAtOffset(margin, y)
                    activeFont = primary // 图片恢复后字体重置为 primary
                    pictureCount++
                }

                is PdfContent.Text -> {
                    var atParagraphStart = true // 块首行视为段首（缩进由 indent 与首行标记控制）
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
                        // 逐字符绑定字体：null = 无任何字体能编码（跳过不画，宽 0）
                        val glyphs = line.map { ch ->
                            fonts.firstOrNull { runCatching { it.encode(ch.toString()) }.isSuccess }
                                ?.let { ch to it }
                        }
                        noGlyphCharCount += glyphs.count { it == null }
                        // 按显示宽度断行（无字形字符宽 0；单字超宽也强制断，避免死循环）
                        val rows = mutableListOf<List<Pair<Char, PDFont>>>()
                        var start = 0
                        var lastGood = 0
                        var width = 0f
                        for (i in glyphs.indices) {
                            val g = glyphs[i]
                            width += g?.let { (c, f) -> f.getStringWidth(c.toString()) * fontSize / 1000f }
                                ?: 0f
                            if (width > usableWidth) {
                                if (lastGood == start) lastGood = i + 1
                                rows += glyphs.subList(start, lastGood).filterNotNull()
                                start = lastGood
                                lastGood = start
                                width = 0f
                            } else {
                                lastGood = i + 1
                            }
                        }
                        if (start < glyphs.size) rows += glyphs.subList(start, glyphs.size)
                            .filterNotNull()
                        for (row in rows) {
                            if (y < margin + leading) newPage()
                            // 段落首行缩进（标题/元数据块与全文首行不缩进）
                            val indent =
                                if (atParagraphStart && !isFirstContentLine && item.indent) {
                                    indentWidth
                                } else {
                                    0f
                                }
                            if (indent > 0f) content.newLineAtOffset(indent, 0f)
                            // 行内按连续同字体分段绘制（setFont 切换不改变文本位置）；
                            // activeFont 为跨行跟踪变量（见上），勿在此重复声明
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
                            // 换行：退回缩进 + 下移一行
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
        content.endText()
        content.close()
        Log.d(
            TAG,
            "renderPdf 完成 总行数=$lineCount 新增页=$newPageCount 总页数=${doc.numberOfPages} " +
                    "showText失败段=$showTextFailCount 无字形字符=$noGlyphCharCount 图片=$pictureCount 图片跳过=$pictureSkipCount"
        )
    }
}
