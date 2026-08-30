package com.pixiv.reader.core.ui.component.text

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** pixiv 深链：illusts / novels / users 三类；[PIXIV_LINK_TAG] 作为 StringAnnotation 的 tag。 */
private val PIXIV_LINK_REGEX = Regex("pixiv://(illusts|novels|users)/(\\d+)")
private const val PIXIV_LINK_TAG = "pixivLink"

/**
 * Pixiv 简介富文本解析（Jsoup）→ Compose [AnnotatedString]。
 *
 * Pixiv 的 caption/intro 是 HTML 片段（如 `<strong><a href="pixiv://illusts/123">…</a></strong>
 * <br/><br/>正文…`），支持：
 * - `<br>` → 换行（连续两个 = 空行分段，超量压缩为最多两个）
 * - `<strong>/<b>` 加粗、`<em>/<i>` 斜体、`<u>` 下划线、`<s>/<del>` 删除线
 * - `<a href="pixiv://illusts|novels|users/{id}">` → 链接样式（[linkColor] + 下划线），
 *   链接区间以 `StringAnnotation(PIXIV_LINK_TAG, "illust:123")` 标记，点击时按 offset 查注解分发
 * - 块级标签（`p/div/li/…`）前后自动换行；`script/style/iframe/img` 等丢弃
 * - 其他未知行内标签只保留文本
 *
 * @param html 原始 HTML 片段
 * @param linkColor 链接文字颜色
 * @return 带样式与链接注解的 [AnnotatedString]
 */
fun parseCaptionHtml(html: String, linkColor: Color): AnnotatedString {
    if (html.isBlank()) return AnnotatedString("")
    val builder = AnnotatedString.Builder()
    var newlineCount = 0
    var hasContent = false

    // 换行：已有一个换行时再追加一个（`<br><br>` 空行），最多保持两个连续换行
    fun appendBreak() {
        if (!hasContent) return
        val target = if (newlineCount >= 1) 2 else 1
        while (newlineCount < target) {
            builder.append('\n')
            newlineCount++
        }
    }

    fun appendText(text: String) {
        // 压缩空白（保留单个空格）；换行完全由 <br>/块级标签控制
        builder.append(text.replace(Regex("\\s+"), " "))
        hasContent = true
        newlineCount = 0
    }

    // 递归遍历：样式标签 push/pop、链接 pushAnnotation/pop、块级前后换行
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
                            // 注解值形如 illust:123 / novel:123 / user:123，点击时按 offset 查注解分发
                            builder.pushStringAnnotation(PIXIV_LINK_TAG, "$type:$id")
                            builder.pushStyle(
                                SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            )
                            node.childNodes().forEach(::visit)
                            builder.pop()
                            builder.pop()
                        } else {
                            // 非 pixiv 链接：只保留文字，无样式
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
 * 渲染 Pixiv 简介富文本（HTML → 样式 + 可点击深链）。
 *
 * 透传 [ClickableText] 的截断/布局参数（[maxLines] / [overflow] / [onTextLayout]），
 * 调用方既有「展开/收起 + 溢出检测」逻辑无需改动；首行缩进等文字样式经 [style] 传入。
 * 链接点击：按点击 offset 查 [PIXIV_LINK_TAG] 注解，值 `illust:123` 等分发到对应回调。
 *
 * @param html 原始 HTML 片段（Pixiv caption）
 * @param modifier 外部传入的 Modifier
 * @param style 基础文字样式（如 bodyMedium + 首行缩进）
 * @param color 默认文字颜色（span 样式覆盖之）
 * @param linkColor 链接文字颜色（默认主题 primary）
 * @param maxLines 最大行数（截断检测用；默认不限制）
 * @param overflow 超出截断方式
 * @param onTextLayout 布局结果回调（溢出检测 / 展开状态联动）
 * @param onOpenIllust 点击 `pixiv://illusts/{id}` 链接回调
 * @param onOpenNovel 点击 `pixiv://novels/{id}` 链接回调
 * @param onOpenUser 点击 `pixiv://users/{id}` 链接回调
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
    val annotated = remember(html, linkColor) { parseCaptionHtml(html, linkColor) }
    // ClickableText 无 color 参数：非默认色时并入 style（span 样式仍覆盖之）
    val effectiveStyle = if (color != Color.Unspecified) style.copy(color = color) else style
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = effectiveStyle,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = onTextLayout,
        // 点击 offset → 查该位置链接注解 → 按类型分发
        onClick = { offset ->
            annotated.getStringAnnotations(PIXIV_LINK_TAG, offset, offset).firstOrNull()?.let { ann ->
                val (type, id) = ann.item.split(':')
                val targetId = id.toLongOrNull() ?: return@let
                // 类型与正则捕获一致（复数：illusts/novels/users）
                when (type) {
                    "illusts" -> onOpenIllust(targetId)
                    "novels" -> onOpenNovel(targetId)
                    "users" -> onOpenUser(targetId)
                }
            }
        },
    )
}
