package com.pixiv.reader.core.novel

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * 把任意 HTML 片段转为可读纯文本（用于小说简介 / 作品描述等富文本字段）。
 *
 * - 去掉 `<br>` 与块级标签产生的原始标签
 * - `<a href>` 等行内标签只保留文字
 * - 块级元素/换行转换为换行，压缩多余空行
 */
fun htmlToPlainText(html: String): String {
    if (html.isBlank()) return ""
    val doc: Document = Jsoup.parse(html)
    val root = doc.body()
    val sb = StringBuilder()
    appendRichText(root, sb)
    return sb.toString()
        .replace(Regex("[\\t ]+"), " ")
        .replace(Regex(" *\\n *"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

private val RICH_BLOCK_TAGS = setOf(
    "p", "div", "li", "blockquote", "pre", "section", "article", "figure",
    "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "header", "footer", "main",
)

private fun appendRichText(node: Node, sb: StringBuilder) {
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
            val isBlock = tag in RICH_BLOCK_TAGS
            if (isBlock && sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
            node.childNodes().forEach { appendRichText(it, sb) }
            if (isBlock && sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
        }
    }
}
