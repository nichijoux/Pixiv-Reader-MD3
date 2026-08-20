package com.pixiv.reader.core.novel.parser

import com.pixiv.reader.core.novel.model.NovelBlock
import com.pixiv.reader.core.novel.model.NovelDocument
import com.pixiv.reader.core.novel.model.assignCharRanges
import com.pixiv.reader.core.novel.model.buildFullText
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * 小说正文 HTML 解析器（Jsoup）。
 *
 * Pixiv `/webview/v2/novel?id=` 返回整页 HTML，正文结构随端版本变化，
 * 因此采用「多选择器回退 + 文本兜底」策略：
 * 1. 优先在 `div.novel-body` / `.novel-view` / `.novel-content` 内收集 `<p>`、`<h*>`、
 *    `<figure>`/`<img>`、`<hr>`、`<blockquote>`；
 * 2. 结构不足时退化为「直接子元素文本」收集；
 * 3. 仍无内容则用 [extractAllText] 保留换行的全文提取兜底（兼容 div 结构段落）。
 */
object NovelParser {

    /** 段落首行缩进（两个全角空格，与中文排版习惯一致） */
    const val PARAGRAPH_INDENT = "\u3000\u3000"

    /** 单段最大字符数（防止异常解析出超大文本块拖垮渲染） */
    const val MAX_PARAGRAPH_CHARS = 3000

    private val ROOT_SELECTORS = listOf(
        "div.novel-content",
        "div.novel-view",
        "div.novel-body",
        "#novel-body",
        "section.novel-body",
        "main",
        "article",
    )

    /**
     * 解析小说正文 HTML 为 [NovelDocument]。
     *
     * 解析策略（按优先级）：候选容器选择器逐个尝试 → 整页正文兜底 → `<script>` 内嵌 JSON 兜底。
     *
     * @param html `/webview/v2/novel` 返回的整页 HTML
     * @param imageUrls 正文嵌入图片映射：key 为 `uploadedimage:file` 标记，value 为图片 URL
     *   （来自 `webApi.getNovelWeb().textEmbeddedImages`）；用于把正文标记解析为 [NovelBlock.Image]
     * @return 结构化文档；解析失败/无内容返回 [NovelDocument.EMPTY]
     */
    fun parse(html: String, imageUrls: Map<String, String> = emptyMap()): NovelDocument {
        if (html.isBlank()) return NovelDocument.EMPTY
        val doc = Jsoup.parse(html)

        // 1) 逐个候选容器尝试，第一个能提取到内容者胜出
        for (selector in ROOT_SELECTORS) {
            val root = doc.selectFirst(selector) ?: continue
            val blocks = tryExtract(root, imageUrls)
            if (blocks.isNotEmpty()) return buildDocument(blocks)
        }

        // 2) 整页正文兜底（任意 DOM 结构，跳过 script/style）
        val bodyBlocks = tryExtract(doc.body(), imageUrls)
        if (bodyBlocks.isNotEmpty()) return buildDocument(bodyBlocks)

        // 3) React 页面内嵌 JSON 兜底（正文在 <script> 里）
        val scriptBlocks = extractFromScripts(doc, imageUrls)
        if (scriptBlocks.isNotEmpty()) return buildDocument(scriptBlocks)

        return NovelDocument.EMPTY
    }

    private fun buildDocument(blocks: List<NovelBlock>): NovelDocument {
        // 超长段落切分，避免单个 Text 渲染过大文本
        val safe = blocks.flatMap { block ->
            if (block is NovelBlock.Paragraph && block.text.length > MAX_PARAGRAPH_CHARS) {
                splitLongParagraph(block.text, MAX_PARAGRAPH_CHARS).map { NovelBlock.Paragraph(it) }
            } else {
                listOf(block)
            }
        }
        val ranged = assignCharRanges(safe)
        return NovelDocument(blocks = ranged, fullText = buildFullText(ranged))
    }

    private fun splitLongParagraph(text: String, max: Int): List<String> {
        val parts = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = (start + max).coerceAtMost(text.length)
            if (end < text.length) {
                val boundary = text.lastIndexOfAny(charArrayOf(' ', '\n', '。', '！', '？', '，', '、'), end - 1)
                if (boundary > start + max / 2) end = boundary + 1
            }
            parts.add(text.substring(start, end).trim())
            start = end
        }
        return parts
    }

    /** 在单个容器内尝试结构化提取，失败时用全文提取兜底。 */
    private fun tryExtract(root: Element, imageUrls: Map<String, String>): List<NovelBlock> {
        val raw = extractBlocks(root)
        if (raw.isNotEmpty()) return raw
        return textFallback(root, imageUrls)
    }

    // ── 结构化提取 ────────────────────────────────────────────────────────────

    private fun extractBlocks(root: Element): List<NovelBlock> {
        val blocks = mutableListOf<NovelBlock>()

        // 直接子元素逐个识别；若直接子元素无 `<p>`，再尝试整个容器内的结构标签（多嵌套兜底）
        val children = root.children()
        val directParagraphs = children.count { it.tagName() == "p" }
        val targets = if (directParagraphs > 0) {
            children
        } else {
            root.select("p, h1, h2, h3, h4, h5, h6, figure, img, hr, blockquote, .novel-paragraph")
        }

        for (el in targets) {
            val block = parseElement(el) ?: continue
            if (blocks.lastOrNull() != block) blocks.add(block)
        }
        return blocks
    }

    private fun parseElement(el: Element): NovelBlock? {
        val tag = el.tagName()
        return when {
            tag == "p" || el.hasClass("novel-paragraph") -> parseParagraph(el)
            tag.matches(Regex("h[1-6]")) -> parseHeading(el)
            tag == "blockquote" -> {
                val text = cleanText(el.text())
                if (text.isBlank()) null else NovelBlock.Quote(text)
            }
            tag == "hr" -> NovelBlock.Separator()
            tag == "img" -> parseImage(el)
            tag == "figure" || el.hasClass("novel-image") || el.hasClass("novel-figure") ->
                parseFigure(el)
            else -> {
                // 无结构标签的 div：仅当含有有意义的自有文本时兜底为段落
                val text = cleanText(el.text())
                if (text.isBlank() || !hasVisibleText(el)) null else NovelBlock.Paragraph(text)
            }
        }
    }

    private fun parseParagraph(el: Element): NovelBlock? {
        // `<br>` 在段落内表示换行（同一段内的软换行）
        val text = el.html()
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .let { Jsoup.parse(it).text() }
        val cleaned = cleanText(text)
        if (cleaned.isBlank()) return null
        // 不再硬编码全角缩进前缀：cleanText 已剔除段首/段尾空白（含全角空格），
        // 段首缩进完全由阅读器「缩进」设置控制（textIndent）
        return NovelBlock.Paragraph(cleaned)
    }

    private fun parseHeading(el: Element): NovelBlock? {
        val level = el.tagName().removePrefix("h").toIntOrNull() ?: 2
        val text = cleanText(el.text())
        if (text.isBlank()) return null
        return NovelBlock.Heading(text, level.coerceIn(1, 6))
    }

    private fun parseImage(el: Element): NovelBlock? {
        val url = el.absUrl("src").ifBlank { el.attr("src") }
        if (url.isBlank()) return null
        val caption = el.attr("alt").takeIf { it.isNotBlank() }
        return NovelBlock.Image(url, caption)
    }

    private fun parseFigure(el: Element): NovelBlock? {
        val img = el.selectFirst("img") ?: return null
        val url = img.absUrl("src").ifBlank { img.attr("src") }
        if (url.isBlank()) return null
        val caption = cleanText(el.selectFirst("figcaption")?.text().orEmpty())
            .ifBlank { img.attr("alt").trim() }
        return NovelBlock.Image(url, caption.takeIf { it.isNotBlank() })
    }

    /** 全文文本兜底：保留换行的结构提取，按空行切段，再切分嵌入图片标记。 */
    private fun textFallback(root: Element, imageUrls: Map<String, String>): List<NovelBlock> {
        val paragraphs = extractAllText(root)
            .map { cleanText(it) }
            .filter { it.isNotBlank() }
        if (paragraphs.isEmpty()) return emptyList()
        return paragraphs.flatMap { splitEmbeddedImages(it, imageUrls) }
    }

    /**
     * React 渲染页面兜底：正文以 JSON 字符串内嵌在 `<script>` 中。
     * 如 `window.pixiv.novel.text`（pixiv isV2 页面，正文 `\uXXXX` 转义、含 `\n` 换行）。
     * 用「indexOf 定位键 + 逐字符解析 JSON 字符串」而非正则匹配：
     * 正文字符串可长达几十万字符，正则回溯会触发 StackOverflowError。
     *
     * 正文中的插图以标记内嵌（`[pixivimage:ID]` 引用画作 / `[uploadedimage:file]` 上传图），
     * 由 [splitEmbeddedImages] 切分为图片块；[imageUrls] 提供标记内容 → 图片 URL 的映射
     * （来自 `ajax/novel/{id}` 的 textEmbeddedImages），缺失时保留标记协议串供上层异步解析。
     */
    private fun extractFromScripts(doc: Document, imageUrls: Map<String, String>): List<NovelBlock> {
        val scriptTexts = doc.select("script").map { it.html() }.filter { it.isNotBlank() }
        val candidates = mutableListOf<String>()
        val fields = listOf("content", "text", "description", "body")
        for (raw in scriptTexts) {
            for (field in fields) {
                var searchFrom = 0
                while (true) {
                    val key = raw.indexOf("\"$field\"", searchFrom)
                    if (key < 0) break
                    searchFrom = key + 1
                    val value = readJsonStringValue(raw, key + field.length + 2) ?: continue
                    // 只收包含较多中/日/韩字符的长文本，过滤掉脚本噪音
                    // （正文含大量插图标记时 CJK 占比会下降，故放宽为「至少 10 个 CJK 或占比 1/3」）
                    val cjkCount = value.count { isCjk(it) }
                    if (value.length >= 20 && (cjkCount >= 10 || cjkCount >= value.length / 3)) {
                        candidates.add(value)
                    }
                }
            }
        }
        val best = candidates.maxByOrNull { it.length } ?: return emptyList()
        val paragraphs = best
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</?[a-zA-Z][^>]*>"), "")
            .split(Regex("\\n+"))
            .map { cleanText(it) }
            .filter { it.isNotBlank() }
        val blocks = paragraphs.flatMap { splitEmbeddedImages(it, imageUrls) }
        if (blocks.isEmpty()) return emptyList()
        return blocks
    }

    /** 正文插图标记：`[pixivimage:ID]`（画作引用）/ `[uploadedimage:file]`（上传图）。 */
    private val EMBEDDED_IMAGE_RE = Regex("\\[pixivimage:\\d+\\]|\\[uploadedimage:[^\\]]+\\]")

    /**
     * 把一段正文按嵌入图片标记切分为「段落 / 图片」块序列。
     * [imageUrls] 键为标记内容（`uploadedimage:file`），值为图片 URL；
     * 映射缺失时图片 URL 保留标记协议串（如 `pixivimage:123456`），由上层异步解析真实 URL。
     */
    private fun splitEmbeddedImages(paragraph: String, imageUrls: Map<String, String>): List<NovelBlock> {
        val result = mutableListOf<NovelBlock>()
        var cursor = 0
        for (m in EMBEDDED_IMAGE_RE.findAll(paragraph)) {
            val before = paragraph.substring(cursor, m.range.first)
            if (before.isNotBlank()) result.add(NovelBlock.Paragraph(before.trim()))
            val content = m.value.removePrefix("[").removeSuffix("]")
            result.add(NovelBlock.Image(resolveEmbeddedUrl(content, imageUrls), null))
            cursor = m.range.last + 1
        }
        if (cursor < paragraph.length) {
            val rest = paragraph.substring(cursor)
            if (rest.isNotBlank()) result.add(NovelBlock.Paragraph(rest.trim()))
        }
        return result
    }

    /** 标记内容 → 图片 URL：优先完整标记内容，其次去掉前缀的纯文件名。 */
    private fun resolveEmbeddedUrl(content: String, imageUrls: Map<String, String>): String {
        imageUrls[content]?.let { return it }
        imageUrls[content.substringAfter(':', content)]?.let { return it }
        return content
    }

    /**
     * 从脚本文本指定位置开始，读取一个 JSON 字符串字面量的值（自动解码 `\uXXXX`/`\n` 等转义）。
     * 不依赖正则回溯，长文本（几十万字符）下安全。
     *
     * @param keyEnd 键名结束位置（键名最后一个字符的下标 + 1），函数会跳过空白与冒号定位值。
     */
    private fun readJsonStringValue(raw: String, keyEnd: Int): String? {
        var i = keyEnd
        // 跳过空白与冒号，定位值起始引号
        while (i < raw.length) {
            val c = raw[i]
            if (c == ':' || c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                i++
                continue
            }
            break
        }
        if (i >= raw.length || raw[i] != '"') return null
        val sb = StringBuilder()
        i++
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\') {
                if (i + 1 >= raw.length) return null
                val next = raw[i + 1]
                when (next) {
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'b' -> { sb.append('\b'); i += 2 }
                    'f' -> { sb.append('\u000C'); i += 2 }
                    '"' -> { sb.append('"'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    '/' -> { sb.append('/'); i += 2 }
                    'u' -> {
                        if (i + 5 < raw.length) {
                            val hex = raw.substring(i + 2, i + 6)
                            sb.append(hex.toIntOrNull(16)?.toChar() ?: next)
                            i += 6
                        } else {
                            return null
                        }
                    }
                    else -> { sb.append(next); i += 2 }
                }
            } else if (c == '"') {
                return sb.toString()
            } else {
                sb.append(c)
                i++
            }
        }
        return null
    }

    private fun isCjk(c: Char): Boolean {
        val code = c.code
        return code in 0x4E00..0x9FFF || code in 0x3040..0x30FF ||
            code in 0xAC00..0xD7AF || code in 0x3400..0x4DBF
    }

    // ── 工具 ──────────────────────────────────────────────────────────────────

    private fun hasVisibleText(el: Element): Boolean {
        el.childNodes().forEach { node ->
            when (node) {
                is TextNode -> if (node.text().isNotBlank()) return true
                is Element -> if (node.tagName() != "script" && node.tagName() != "style" &&
                    hasVisibleText(node)
                ) return true
            }
        }
        return false
    }

    private fun cleanText(raw: String): String =
        // trim() 依据 Character.isWhitespace，含全角空格（U+3000）——
        // 段首/段尾的空格与全角缩进一律剔除，缩进交给阅读器设置
        raw.replace(Regex("\\s+"), " ").trim()

    // ── 保留换行的全文提取（任意结构兜底） ───────────────────────────────────

    private val BLOCK_TAGS = setOf(
        "p", "div", "li", "blockquote", "pre", "section", "article", "figure",
        "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "header", "footer", "main",
    )

    private fun extractAllText(root: Element): List<String> {
        val sb = StringBuilder()
        appendNodeText(root, sb)
        return sb.toString()
            .split(Regex("\\n{2,}"))
            .map { it.replace(Regex("[\\t ]+"), " ").trim() }
            .filter { it.isNotBlank() }
    }

    private fun appendNodeText(node: Node, sb: StringBuilder) {
        when (node) {
            is TextNode -> sb.append(node.text())
            is Element -> {
                val tag = node.tagName()
                if (tag == "script" || tag == "style" || tag == "noscript" || tag == "head" || tag == "iframe") {
                    return
                }
                if (tag == "br") {
                    if (sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
                    return
                }
                val isBlock = tag in BLOCK_TAGS
                if (isBlock && sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
                node.childNodes().forEach { appendNodeText(it, sb) }
                if (isBlock && sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
            }
        }
    }
}
