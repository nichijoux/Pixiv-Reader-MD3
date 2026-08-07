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
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""

private const val DOCX_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="word/styles.xml"/>
</Relationships>"""

/** Word 样式表：Title（书名）+ Heading1/Heading2（导航窗格目录层级）。 */
private const val DOCX_STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Title">
    <w:name w:val="Title"/>
    <w:basedOn w:val="Normal"/>
    <w:pPr><w:spacing w:before="240" w:after="240"/><w:jc w:val="center"/></w:pPr>
    <w:rPr><w:b/><w:sz w:val="36"/></w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading1">
    <w:name w:val="heading 1"/>
    <w:basedOn w:val="Normal"/>
    <w:pPr><w:outlineLvl w:val="0"/><w:spacing w:before="240" w:after="120"/></w:pPr>
    <w:rPr><w:b/><w:sz w:val="32"/></w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading2">
    <w:name w:val="heading 2"/>
    <w:basedOn w:val="Normal"/>
    <w:pPr><w:outlineLvl w:val="1"/><w:spacing w:before="240" w:after="120"/></w:pPr>
    <w:rPr><w:b/><w:sz w:val="28"/></w:rPr>
  </w:style>
</w:styles>"""

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
                appendLine(novel.title.orEmpty())
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
 * 生成 .docx 字节（纯函数，可测）。
 * 书名用 Title 样式；系列按卷/章层级：卷 Heading1、卷下章节 Heading2、无卷归属章节 Heading1，
 * Word 导航窗格呈现 卷 ▸ 章 层级；正文内部标题保持加粗+字号（不进目录）；插图跳过。
 */
internal fun buildDocx(
    chapters: List<Pair<Novel, NovelDocument>>,
    seriesTitle: String?,
): ByteArray {
    val body = buildString {
        val first = chapters.first().first
        append(docxTitle(seriesTitle ?: first.title.orEmpty()))
        first.user?.name?.takeIf { it.isNotBlank() }?.let { append(docxParagraph("作者：$it")) }
        if (!seriesTitle.isNullOrBlank()) append(docxParagraph("系列：$seriesTitle"))
        first.caption?.takeIf { it.isNotBlank() }
            ?.let { append(docxParagraph("简介：${htmlToPlainText(it)}")) }
        if (chapters.size > 1) {
            // 系列：卷 Heading1、卷下章节 Heading2；无卷归属章节 Heading1（导航窗格顶层）
            buildTocHierarchy(chapters).forEach { entry ->
                append(docxParagraph(null))
                append(docxChapterTitle(entry.title, 1))
                append(docxParagraph(null))
                chapters[entry.sectionIndex - 1].second.blocks.forEach { append(docxBlock(it)) }
                entry.children.forEach { (childIdx, childTitle) ->
                    append(docxParagraph(null))
                    append(docxChapterTitle(childTitle, 2))
                    append(docxParagraph(null))
                    chapters[childIdx - 1].second.blocks.forEach { append(docxBlock(it)) }
                }
            }
        } else {
            // 单本：书名 Title 样式，正文直接输出（不再重复章节标题）
            chapters.first().second.blocks.forEach { append(docxBlock(it)) }
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
        zip.writeEntry("word/styles.xml", DOCX_STYLES)
    }
    return bytes.toByteArray()
}

/** 书名段落：Word Title 样式（居中大标题，不进导航目录）。 */
internal fun docxTitle(text: String): String =
    """<w:p><w:pPr><w:pStyle w:val="Title"/></w:pPr><w:r><w:t xml:space="preserve">${escapeXml(text)}</w:t></w:r></w:p>"""

/** 卷/章节标题段落：Word Heading 样式（导航窗格目录层级；1=Heading1 卷/顶层章，2=Heading2 卷下章）。 */
internal fun docxChapterTitle(text: String, level: Int): String {
    val style = if (level <= 1) "Heading1" else "Heading2"
    return """<w:p><w:pPr><w:pStyle w:val="$style"/></w:pPr><w:r><w:t xml:space="preserve">${
        escapeXml(
            text
        )
    }</w:t></w:r></w:p>"""
}

/** 正文块 → w:p（内部标题用加粗+字号，不进导航目录）。 */
private fun docxBlock(block: NovelBlock): String = when (block) {
    is NovelBlock.Paragraph -> docxParagraph(block.text)
    is NovelBlock.Heading -> docxHeading(block.text, block.level)
    is NovelBlock.Quote -> docxParagraph(block.text, indent = true)
    is NovelBlock.Image -> ""
    is NovelBlock.Separator -> docxParagraph(null)
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
 * @param css 合并后的样式表（样书 Main.css 全文，含 @font-face 段），写入 OEBPS/Styles/Main.css。
 */
internal fun buildEpub(
    chapters: List<Pair<Novel, NovelDocument>>,
    seriesTitle: String?,
    images: List<EpubImage>,
    css: String,
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
        zip.writeEntry("OEBPS/toc.ncx", buildNcx(chapters, seriesTitle))
        zip.writeEntry("OEBPS/Styles/Main.css", css)
        // 书名页（Section0，spine 首位、不进目录）
        zip.writeEntry("OEBPS/Text/Section0.xhtml", buildTitlePage(chapters, seriesTitle))
        // 章节（Section1..N）
        chapters.forEachIndexed { index, (novel, document) ->
            zip.writeEntry(
                "OEBPS/Text/Section${index + 1}.xhtml",
                buildChapterXhtml(novel, document, chapterIndex = index, images = images),
            )
        }
        images.forEach { img ->
            zip.writeEntry("OEBPS/Images/${img.ref}", img.bytes)
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
        append("""<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""")
        append("""<item id="Main.css" href="Styles/Main.css" media-type="text/css"/>""")
        append("""<item id="Section0.xhtml" href="Text/Section0.xhtml" media-type="application/xhtml+xml"/>""")
        chapters.forEachIndexed { index, _ ->
            append("""<item id="Section${index + 1}.xhtml" href="Text/Section${index + 1}.xhtml" media-type="application/xhtml+xml"/>""")
        }
        images.forEach { img ->
            val props = if (img.ref == "cover.jpg") " properties=\"cover-image\"" else ""
            append("""<item id="${img.ref}" href="Images/${img.ref}" media-type="${img.mime}"$props/>""")
        }
    }
    val spine = buildString {
        append("""<itemref idref="Section0.xhtml"/>""")
        chapters.forEachIndexed { index, _ -> append("""<itemref idref="Section${index + 1}.xhtml"/>""") }
    }
    return """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:identifier id="uid">pixiv-novel-${first.id}</dc:identifier>
<dc:title>${escapeXml(seriesTitle ?: first.title.orEmpty())}</dc:title>
<dc:creator>${escapeXml(first.user?.name.orEmpty())}</dc:creator>
<dc:language>zh-CN</dc:language>
<meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
</metadata>
<manifest>$manifest</manifest>
<spine toc="ncx">$spine</spine>
</package>"""
}

/**
 * 章节/分卷标题前缀正则（对齐阅读器规则）：
 * `序章` / `第N章`、`第N卷` / `终章` / `简章` / `后记` / `完本感言` / `上架感言` / `番外`。
 * 组 1 为徽标前缀，组 2 为剩余标题。
 */
private val CHAPTER_NUM_REGEX = Regex(
    """^(序章|第[\d零一二三四五六七八九十百千万]+[章卷]|终章|简章|后记|完本感言|上架感言|番外)(.*)$"""
)

/** 提取标题前缀徽标（序章/第N章/后记/番外 等）；无前缀返回 null。 */
internal fun extractChapterPrefix(title: String): String? =
    CHAPTER_NUM_REGEX.find(title)?.groupValues?.getOrNull(1)

/**
 * 目录条目：卷（含子章节）或顶层章节。
 * [sectionIndex] 为 1 基章节序号（对应 EPUB SectionN / DOCX 章节顺序）。
 */
internal data class TocEntry(
    val sectionIndex: Int,
    val title: String,
    val isVolume: Boolean,
    val children: List<Pair<Int, String>> = emptyList(),
)

/**
 * 卷/章层级：标题前缀以「卷」结尾（第X卷）即卷节点；其后非卷章节归入该卷，直到下一卷；
 * 卷前无归属章节为顶层。
 */
internal fun buildTocHierarchy(chapters: List<Pair<Novel, NovelDocument>>): List<TocEntry> {
    val result = mutableListOf<TocEntry>()
    val pendingChildren = mutableMapOf<TocEntry, MutableList<Pair<Int, String>>>()
    var currentVolume: TocEntry? = null
    chapters.forEachIndexed { index, (novel, _) ->
        val title = novel.title.orEmpty()
        val isVolume = extractChapterPrefix(title)?.endsWith("卷") == true
        if (isVolume) {
            val entry = TocEntry(index + 1, title, isVolume = true)
            result.add(entry)
            pendingChildren[entry] = mutableListOf()
            currentVolume = entry
        } else {
            val vol = currentVolume
            if (vol != null) {
                pendingChildren.getValue(vol).add((index + 1) to title)
            } else {
                result.add(TocEntry(index + 1, title, isVolume = false))
            }
        }
    }
    result.replaceAll { entry ->
        if (entry.isVolume) entry.copy(children = pendingChildren[entry].orEmpty()) else entry
    }
    return result
}

/** 生成 EPUB3 导航文档（toc：卷为外层、卷下章节嵌套 ol；顶层章节平铺）。 */
internal fun buildNav(chapters: List<Pair<Novel, NovelDocument>>): String {
    val items = buildTocHierarchy(chapters).joinToString("\n") { entry ->
        if (entry.isVolume) {
            """<li><a href="Text/Section${entry.sectionIndex}.xhtml">${escapeXml(entry.title)}</a>
<ol>
${
                entry.children.joinToString("\n") { (idx, title) ->
                    """<li><a href="Text/Section$idx.xhtml">${escapeXml(title)}</a></li>"""
                }
            }
</ol>
</li>"""
        } else {
            """<li><a href="Text/Section${entry.sectionIndex}.xhtml">${escapeXml(entry.title)}</a></li>"""
        }
    }
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

/** 生成 EPUB2 兼容目录（toc.ncx：卷 navPoint 内嵌子 navPoint；playOrder 全局顺序递增）。 */
internal fun buildNcx(chapters: List<Pair<Novel, NovelDocument>>, seriesTitle: String?): String {
    val bookTitle = escapeXml(seriesTitle ?: chapters.firstOrNull()?.first?.title.orEmpty())
    val navMap = buildString {
        var order = 0
        buildTocHierarchy(chapters).forEach { entry ->
            val volOrder = ++order
            if (entry.isVolume) {
                append(
                    """    <navPoint id="navPoint-$volOrder" playOrder="$volOrder">
      <navLabel>
        <text>${escapeXml(entry.title)}</text>
      </navLabel>
      <content src="Text/Section${entry.sectionIndex}.xhtml"/>
"""
                )
                entry.children.forEach { (idx, title) ->
                    val childOrder = ++order
                    append(
                        """      <navPoint id="navPoint-$childOrder" playOrder="$childOrder">
        <navLabel>
          <text>${escapeXml(title)}</text>
        </navLabel>
        <content src="Text/Section$idx.xhtml"/>
      </navPoint>
"""
                    )
                }
                append("    </navPoint>\n")
            } else {
                append(
                    """    <navPoint id="navPoint-$volOrder" playOrder="$volOrder">
      <navLabel>
        <text>${escapeXml(entry.title)}</text>
      </navLabel>
      <content src="Text/Section${entry.sectionIndex}.xhtml"/>
    </navPoint>
"""
                )
            }
        }
    }
    return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE ncx PUBLIC "-//NISO//DTD ncx 2005-1//EN" "http://www.daisy.org/z3986/2005/ncx-2005-1.dtd">
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head>
    <meta name="dtb:uid" content="pixiv-novel"/>
    <meta name="dtb:depth" content="1"/>
    <meta name="dtb:totalPageCount" content="0"/>
    <meta name="dtb:maxPageNumber" content="0"/>
  </head>
  <docTitle>
    <text>$bookTitle</text>
  </docTitle>
  <navMap>
$navMap  </navMap>
</ncx>"""
}

/** 生成书名页（Section0）：书名 + 作者（样书 Title-center/Title-color 结构）。 */
internal fun buildTitlePage(
    chapters: List<Pair<Novel, NovelDocument>>,
    seriesTitle: String?,
): String {
    val first = chapters.first().first
    val title = escapeXml(seriesTitle ?: first.title.orEmpty())
    val author = escapeXml(first.user?.name.orEmpty())
    val authorLine =
        if (author.isBlank()) "" else """<p class="Title-center Title-color">$author</p>"""
    return """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>$title</title>
<link href="../Styles/Main.css" type="text/css" rel="stylesheet"/>
</head>
<body>
<h1>$title</h1>
$authorLine
</body>
</html>"""
}

/** 去除段落首部全角缩进（CSS text-indent:2em 已承担缩进，避免双重缩进）。 */
private fun stripIndent(text: String): String = text.removePrefix("\u3000\u3000")

/** 生成单章 xhtml（blocks → 样书语义化 HTML；插图成功下载才嵌 div>img）。 */
internal fun buildChapterXhtml(
    novel: Novel,
    document: NovelDocument,
    chapterIndex: Int,
    images: List<EpubImage>,
): String {
    val rawTitle = novel.title.orEmpty()
    // 章标题拆「前缀徽标」+ 章题（样书 Title-num/Title-text 结构；前缀如 序章/第N章/后记/番外）
    val chapterNum = extractChapterPrefix(rawTitle)
    val chapterText = rawTitle.removePrefix(chapterNum.orEmpty()).trim()
    val showBadge = chapterNum != null && chapterText.isNotBlank()
    val title = escapeXml(rawTitle)
    val body = buildString {
        append("<h1 class=\"Title-center\">")
        if (showBadge) {
            append("<span class=\"Title-num\">${escapeXml(chapterNum!!)}</span><br/>")
        }
        append("<span class=\"Title-text\">${escapeXml(if (showBadge) chapterText else rawTitle)}</span>")
        append("</h1>\n")
        var imgIndex = 0
        document.blocks.forEach { block ->
            when (block) {
                is NovelBlock.Paragraph -> append("<p>${escapeXml(stripIndent(block.text))}</p>\n")
                is NovelBlock.Heading -> {
                    val level = block.level.coerceIn(2, 6)
                    append("<h$level>${escapeXml(block.text)}</h$level>\n")
                }

                is NovelBlock.Quote -> append("<blockquote><p>${escapeXml(stripIndent(block.text))}</p></blockquote>\n")
                // 样书场景分隔 = 一段破折号 <p>（非 <hr/>）
                is NovelBlock.Separator -> append("<p>${escapeXml(block.symbol)}</p>\n")
                is NovelBlock.Image -> {
                    val ref = "img_${chapterIndex}_${imgIndex}.jpg"
                    imgIndex++
                    if (images.any { it.ref == ref }) {
                        append(
                            """<div class="Header-image-dk"><img class="width100" src="../Images/$ref" alt="插图"/></div>"""
                        ).append('\n')
                        block.caption?.takeIf { it.isNotBlank() }?.let {
                            append("<p>${escapeXml(it)}</p>\n")
                        }
                    }
                    // 未下载成功则不输出
                }
            }
        }
    }
    return """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>$title</title>
<link href="../Styles/Main.css" type="text/css" rel="stylesheet"/>
</head>
<body>
$body
</body>
</html>"""
}
