package com.pixiv.reader.core.novel.util

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * 任意 HTML 片段 → 可读纯文本（小说简介 / 作品描述等）：块级标签与 `<br>` 转换行，多余空行压到最多一个。
 */
fun htmlToPlainText(html: String): String {
    if (html.isBlank()) return ""
    val doc: Document = Jsoup.parse(html)
    val root = doc.body()
    val sb = StringBuilder()
    appendStructuredText(root, sb)
    return sb.toString()
        .replace(Regex("[\\t ]+"), " ")
        .replace(Regex(" *\\n *"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

private val BLOCK_TAGS = setOf(
    "p", "div", "li", "blockquote", "pre", "section", "article", "figure",
    "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "header", "footer", "main",
)

/**
 * HTML→纯文本共享实现（[htmlToPlainText] 与 `NovelParser` 全文兜底原实现逐字相同故收敛）：块级标签保证换行边界，
 * `<br>` 追加换行，script/style 类整体跳过，供调用方按空行切段 / 压缩空行等后处理。
 */
internal fun appendStructuredText(node: Node, sb: StringBuilder) {
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
            node.childNodes().forEach { appendStructuredText(it, sb) }
            if (isBlock && sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
        }
    }
}
