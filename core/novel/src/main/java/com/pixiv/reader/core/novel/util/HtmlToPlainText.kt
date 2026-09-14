package com.pixiv.reader.core.novel.util

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
    appendStructuredText(root, sb)
    return sb.toString()
        .replace(Regex("[\\t ]+"), " ")
        .replace(Regex(" *\\n *"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

/** 块级标签集合（递归换行边界；[htmlToPlainText] 与 NovelParser 全文兜底提取共用）。 */
private val BLOCK_TAGS = setOf(
    "p", "div", "li", "blockquote", "pre", "section", "article", "figure",
    "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "header", "footer", "main",
)

/**
 * 递归追加节点可见文本（HTML→纯文本共享实现，[htmlToPlainText] 与
 * `NovelParser` 的保留换行全文兜底提取共用，两处原实现逐字相同故收敛）：
 * TextNode 原文追加；script/style/noscript/head/iframe 整体跳过；`<br>` 追加换行；
 * 块级标签前后保证换行边界，供调用方按空行切段 / 压缩空行等后处理。
 *
 * @param node 当前遍历的 Jsoup 节点
 * @param sb 文本输出缓冲（跨递归累积）
 * @return 无返回值（结果写入 [sb]）
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
