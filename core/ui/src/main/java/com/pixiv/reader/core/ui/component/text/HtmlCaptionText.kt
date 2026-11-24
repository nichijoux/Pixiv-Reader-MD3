package com.pixiv.reader.core.ui.component.text

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** pixiv 深链三类：捕获组拼成 `illusts:123` 形式的链接标识。 */
private val PIXIV_LINK_REGEX = Regex("pixiv://(illusts|novels|users)/(\\d+)")

/**
 * 把 Pixiv caption 的 HTML 片段（Jsoup 解析）转成 [AnnotatedString]：`pixiv://illusts|novels|users/{id}`
 * 渲染为可点击链接，块级标签前后自动换行且连续换行最多压到两个（否则长简介空白失控），
 * 未知标签只保留文本。
 */
fun parseCaptionHtml(html: String, linkColor: Color, onLinkClick: (String) -> Unit = {}): AnnotatedString {
    if (html.isBlank()) return AnnotatedString("")
    val builder = AnnotatedString.Builder()
    var newlineCount = 0
    var hasContent = false

    // 已有一个换行时再追加 = 空行，最多两个连续换行
    fun appendBreak() {
        if (!hasContent) return
        val target = if (newlineCount >= 1) 2 else 1
        while (newlineCount < target) {
            builder.append('\n')
            newlineCount++
        }
    }

    fun appendText(text: String) {
        // 换行完全由 <br>/块级标签控制，文本内的空白只压成单个空格
        builder.append(text.replace(Regex("\\s+"), " "))
        hasContent = true
        newlineCount = 0
    }

    fun visit(node: Node) {
        when (node) {
            is TextNode -> {
                val t = node.text()
                if (t.isNotEmpty()) appendText(t)
            }
            is Element -> {
                when (node.tagName()) {
                    "br" -> appendBreak()
                    "script", "style", "noscript", "iframe", "head", "img", "figure", "svg" -> Unit // 丢弃
                    "strong", "b" -> {
                        builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        node.childNodes().forEach(::visit)
                        builder.pop()
                    }
                    "em", "i" -> {
                        builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        node.childNodes().forEach(::visit)
                        builder.pop()
                    }
                    "u" -> {
                        builder.pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                        node.childNodes().forEach(::visit)
                        builder.pop()
                    }
                    "s", "del" -> {
                        builder.pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                        node.childNodes().forEach(::visit)
                        builder.pop()
                    }
                    "a" -> {
                        val m = PIXIV_LINK_REGEX.find(node.attr("href"))
                        if (m != null) {
                            val (type, id) = m.destructured
                            builder.pushLink(
                                LinkAnnotation.Clickable(
                                    tag = "$type:$id",
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            color = linkColor,
                                            textDecoration = TextDecoration.Underline,
                                        ),
                                    ),
                                    linkInteractionListener = { onLinkClick("$type:$id") },
                                ),
                            )
                            node.childNodes().forEach(::visit)
                            builder.pop()
                        } else {
                            node.childNodes().forEach(::visit)
                        }
                    }
                    "p", "div", "li", "blockquote", "pre", "section", "article",
                    "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "dl", "table",
                    -> {
                        appendBreak()
                        node.childNodes().forEach(::visit)
                        appendBreak()
                    }
                    else -> node.childNodes().forEach(::visit) // 未知行内标签：透传文本
                }
            }
        }
    }

    visit(Jsoup.parseBodyFragment(html).body())
    return builder.toAnnotatedString()
}

/**
 * 透传 [Text] 的截断/布局参数（[maxLines] / [overflow] / [onTextLayout]），调用方既有「展开/收起 + 溢出检测」
 * 逻辑无需改动；首行缩进等文字样式经 [style] 传入。
 */
@Composable
fun HtmlCaptionText(
    html: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    color: Color = Color.Unspecified,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    onOpenIllust: (Long) -> Unit = {},
    onOpenNovel: (Long) -> Unit = {},
    onOpenUser: (Long) -> Unit = {},
) {
    // AnnotatedString 经 remember 缓存并内嵌首帧的链接回调，经 rememberUpdatedState 间接读取才能分发到新闭包
    val currentOnOpenIllust = rememberUpdatedState(onOpenIllust)
    val currentOnOpenNovel = rememberUpdatedState(onOpenNovel)
    val currentOnOpenUser = rememberUpdatedState(onOpenUser)
    // 链接标识类型与正则捕获一致（复数 illusts/novels/users）；id 非法时静默忽略
    val onLinkClick = remember {
        { link: String ->
            val (type, id) = link.split(':', limit = 2)
            val targetId = id.toLongOrNull()
            if (targetId != null) {
                when (type) {
                    "illusts" -> currentOnOpenIllust.value(targetId)
                    "novels" -> currentOnOpenNovel.value(targetId)
                    "users" -> currentOnOpenUser.value(targetId)
                }
            }
        }
    }
    val annotated = remember(html, linkColor) { parseCaptionHtml(html, linkColor, onLinkClick) }
    // Text 无 color 参数：非默认色时并入 style（链接 span 样式仍覆盖之）
    val effectiveStyle = if (color != Color.Unspecified) style.copy(color = color) else style
    Text(
        text = annotated,
        modifier = modifier,
        style = effectiveStyle,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = onTextLayout,
    )
}
