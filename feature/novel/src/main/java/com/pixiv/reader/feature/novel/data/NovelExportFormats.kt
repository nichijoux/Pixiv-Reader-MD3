package com.pixiv.reader.feature.novel.data

import com.pixiv.api.model.Novel
import com.pixiv.reader.core.novel.NovelBlock
import com.pixiv.reader.core.novel.NovelDocument
import com.pixiv.reader.core.novel.htmlToPlainText
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// ── EPUB 容器常量 ────────────────────────────────────────────────────────────

private const val CONTAINER_XML = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""

private const val DOCX_CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

private const val DOCX_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

// ── TXT ─────────────────────────────────────────────────────────────────────

/**
 * 生成 TXT 全文（纯函数，可测）。
 * 多章（系列）时每章前输出分隔线与章节标题；插图块直接跳过。
 */
internal fun buildTxt(
    chapters: List<Pair<Novel, NovelDocument>>,
    seriesTitle: String?,
): String {
    return buildString {
        val first = chapters.first().first
        appendLine(first.title.orEmpty())
        first.user?.name?.let { if (it.isNotBlank()) appendLine("作者：$it") }
        if (!seriesTitle.isNullOrBlank()) appendLine("系列：$seriesTitle")
        first.caption?.takeIf { it.isNotBlank() }?.let {
            appendLine("简介：${htmlToPlainText(it)}")
        }
        appendLine()
        chapters.forEach { (novel, document) ->
            if (chapters.size > 1) {
                appendLine("================")
                appendLine("【${novel.title.orEmpty()}】")
                appendLine("================")
            }
            document.blocks.forEach { block ->
                when (block) {
                    is NovelBlock.Paragraph -> appendLine(block.text)
                    is NovelBlock.Heading -> {
                        appendLine()
                        appendLine(block.text)
                        appendLine()
                    }

                    is NovelBlock.Quote -> appendLine("> ${block.text}")
                    is NovelBlock.Image -> Unit // TXT 模式跳过插图
                    is NovelBlock.Separator -> appendLine(block.symbol)
                }
            }
            appendLine()
        }
    }
}

// ── MARKDOWN ────────────────────────────────────────────────────────────────

/**
 * 生成 Markdown 全文（纯函数，可测）。
 * 标题保留 `#` 层级、引用保留 `>`、分隔线用 `---`；插图跳过。
 */
internal fun buildMarkdown(
    chapters: List<Pair<Novel, NovelDocument>>,
    seriesTitle: String?,
): String {
    return buildString {
        val first = chapters.first().first
        appendLine("# ${first.title.orEmpty()}")
        first.user?.name?.let { if (it.isNotBlank()) appendLine("> 作者：$it") }
        if (!seriesTitle.isNullOrBlank()) appendLine("> 系列：$seriesTitle")
        first.caption?.takeIf { it.isNotBlank() }?.let {
            appendLine("> 简介：${htmlToPlainText(it)}")
        }
        appendLine()
        chapters.forEach { (novel, document) ->
            if (chapters.size > 1) {
                appendLine("---")
                appendLine("## ${novel.title.orEmpty()}")
                appendLine("---")
            }
            document.blocks.forEach { block ->
                when (block) {
                    is NovelBlock.Paragraph -> appendLine(block.text)
                    is NovelBlock.Heading -> appendLine(
                        "#".repeat(
                            block.level.coerceIn(
                                1,
                                6
                            )
                        ) + " " + block.text
                    )

                    is NovelBlock.Quote -> appendLine("> ${block.text}")
                    is NovelBlock.Image -> Unit // Markdown 无法内嵌 pixiv 图，跳过
                    is NovelBlock.Separator -> appendLine("---")
                }
            }
            appendLine()
        }
    }
}

// ── DOCX 容器（手写最小 OOXML：Content_Types + rels + document.xml） ─────────

/**
 * 生成最小 .docx 字节（纯函数，可测）。
 * 段落/标题/引用 → `w:p`；插图跳过；结尾 sectPr 保证 Word/WPS 正常分页。
 */
internal fun buildDocx(
    chapters: List<Pair<Novel, NovelDocument>>,
    seriesTitle: String?,
): ByteArray {
    val body = buildString {
        val first = chapters.first().first
        append(docxHeading(first.title.orEmpty(), 1))
        first.user?.name?.takeIf { it.isNotBlank() }?.let { append(docxParagraph("作者：$it")) }
        if (!seriesTitle.isNullOrBlank()) append(docxParagraph("系列：$seriesTitle"))
        first.caption?.takeIf { it.isNotBlank() }
            ?.let { append(docxParagraph("简介：${htmlToPlainText(it)}")) }
        chapters.forEach { (novel, document) ->
            if (chapters.size > 1) {
                append(docxParagraph(null))
                append(docxHeading(novel.title.orEmpty(), 2))
                append(docxParagraph(null))
            }
            document.blocks.forEach { block ->
                when (block) {
                    is NovelBlock.Paragraph -> append(docxParagraph(block.text))
                    is NovelBlock.Heading -> append(docxHeading(block.text, block.level))
                    is NovelBlock.Quote -> append(docxParagraph(block.text, indent = true))
                    is NovelBlock.Image -> Unit
                    is NovelBlock.Separator -> append(docxParagraph(null))
                }
            }
        }
        append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr>")
    }
    val docXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body</w:body></w:document>"""
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zip ->
        zip.writeEntry("[Content_Types].xml", DOCX_CONTENT_TYPES)
        zip.writeEntry("_rels/.rels", DOCX_RELS)
        zip.writeEntry("word/document.xml", docXml)
    }
    return bytes.toByteArray()
}

/** 标题段落：加粗 + 字号（half-points：36=18pt / 32=16pt / 28=14pt）。 */
internal fun docxHeading(text: String, level: Int): String {
    val size = when (level) {
        1 -> 36; 2 -> 32; else -> 28
    }
    return """<w:p><w:pPr><w:spacing w:before="240" w:after="120"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="$size"/></w:rPr><w:t xml:space="preserve">${
        escapeXml(
            text
        )
    }</w:t></w:r></w:p>"""
}

/** 普通段落（引用带左缩进）；null/空白 → 空段落。 */
internal fun docxParagraph(
    content: String?,
    bold: Boolean = false,
    indent: Boolean = false
): String {
    if (content.isNullOrBlank()) return "<w:p/>"
    val pPr = if (indent) "<w:pPr><w:ind w:left=\"720\"/></w:pPr>" else ""
    val rPr = if (bold) "<w:rPr><w:b/></w:rPr>" else ""
    return "<w:p>$pPr<w:r>$rPr<w:t xml:space=\"preserve\">${escapeXml(content)}</w:t></w:r></w:p>"
}

// ── EPUB 容器（EPUB3 标准 zip） ─────────────────────────────────────────────

/**
 * 生成 EPUB3 zip 字节（纯函数，可测）。
 * 图片已由调用方下载为 [EpubImage] 传入（缺失即不内嵌）。
 */
internal fun buildEpub(
    chapters: List<Pair<Novel, NovelDocument>>,
    seriesTitle: String?,
    images: List<EpubImage>,
): ByteArray {
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zip ->
        // EPUB 规范：mimetype 必须是第一个条目且不压缩（STORED）
        val mimetypeBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
        zip.putNextEntry(
            ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimetypeBytes.size.toLong()
                crc = crc32(mimetypeBytes)
            },
        )
        zip.write(mimetypeBytes)
        zip.closeEntry()

        zip.writeEntry("META-INF/container.xml", CONTAINER_XML)
        zip.writeEntry("OEBPS/content.opf", buildOpf(chapters, seriesTitle, images))
        zip.writeEntry("OEBPS/nav.xhtml", buildNav(chapters))
        chapters.forEachIndexed { index, (novel, document) ->
            zip.writeEntry(
                "OEBPS/chapter_$index.xhtml",
                buildChapterXhtml(novel, document, chapterIndex = index, images = images),
            )
        }
        images.forEach { img ->
            zip.writeEntry("OEBPS/images/${img.ref}", img.bytes)
        }
    }
    return bytes.toByteArray()
}

/** 生成 content.opf（manifest / spine / metadata）。 */
internal fun buildOpf(
    chapters: List<Pair<Novel, NovelDocument>>,
    seriesTitle: String?,
    images: List<EpubImage>,
): String {
    val first = chapters.first().first
    val manifest = buildString {
        append("""<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""")
        chapters.forEachIndexed { index, _ ->
            append("""<item id="chapter_$index" href="chapter_$index.xhtml" media-type="application/xhtml+xml"/>""")
        }
        images.forEach { img ->
            val props = if (img.ref == "cover.jpg") " properties=\"cover-image\"" else ""
            append("""<item id="${img.ref}" href="images/${img.ref}" media-type="${img.mime}"$props/>""")
        }
    }
    val spine = buildString {
        chapters.forEachIndexed { index, _ -> append("""<itemref idref="chapter_$index"/>""") }
    }
    return """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:identifier id="uid">pixiv-novel-${first.id}</dc:identifier>
<dc:title>${escapeXml(seriesTitle ?: first.title.orEmpty())}</dc:title>
<dc:creator>${escapeXml(first.user?.name.orEmpty())}</dc:creator>
<dc:language>ja</dc:language>
<meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
</metadata>
<manifest>$manifest</manifest>
<spine>$spine</spine>
</package>"""
}

/** 生成 EPUB3 导航文档（toc）。 */
internal fun buildNav(chapters: List<Pair<Novel, NovelDocument>>): String {
    val items = chapters.mapIndexed { index, (novel, _) ->
        """<li><a href="chapter_$index.xhtml">${escapeXml(novel.title.orEmpty())}</a></li>"""
    }.joinToString("\n")
    return """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>目录</title></head>
<body>
<nav epub:type="toc">
<h1>目录</h1>
<ol>
$items
</ol>
</nav>
</body>
</html>"""
}

/** 生成单章 xhtml（blocks → 语义化 HTML；插图成功下载才嵌 <img>）。 */
internal fun buildChapterXhtml(
    novel: Novel,
    document: NovelDocument,
    chapterIndex: Int,
    images: List<EpubImage>,
): String {
    val title = escapeXml(novel.title.orEmpty())
    val body = buildString {
        append("<h1>$title</h1>\n")
        var imgIndex = 0
        document.blocks.forEach { block ->
            when (block) {
                is NovelBlock.Paragraph -> append("<p>${escapeXml(block.text)}</p>\n")
                is NovelBlock.Heading -> {
                    val level = block.level.coerceIn(2, 6)
                    append("<h$level>${escapeXml(block.text)}</h$level>\n")
                }

                is NovelBlock.Quote -> append("<blockquote><p>${escapeXml(block.text)}</p></blockquote>\n")
                is NovelBlock.Separator -> append("<hr/>\n")
                is NovelBlock.Image -> {
                    val ref = "img_${chapterIndex}_${imgIndex}.jpg"
                    imgIndex++
                    if (images.any { it.ref == ref }) {
                        append("""<img src="images/$ref" alt="插图"/>""").append('\n')
                    }
                    // 未下载成功则不输出 <img>
                }
            }
        }
    }
    return """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>$title</title></head>
<body>
$body
</body>
</html>"""
}
