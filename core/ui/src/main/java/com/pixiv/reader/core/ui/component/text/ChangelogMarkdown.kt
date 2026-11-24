package com.pixiv.reader.core.ui.component.text

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * 由 Composable 侧从 MaterialTheme 取色/取字号后传入解析纯函数，保持解析器可 JVM 单测。
 */
data class ChangelogStyles(
    val headingLarge: SpanStyle,
    val headingSmall: SpanStyle,
    val code: SpanStyle,
    val rule: SpanStyle,
    val link: TextLinkStyles,
)

private val HEADING = Regex("^(#{1,6})\\s+(.+)$")

private val BULLET = Regex("^(\\s*)[-*+]\\s+(.+)$")

private val ORDERED = Regex("^(\\s*\\d+[.)]\\s+)(.+)$")

/** 同一符号重复 3 次以上（允许空格间隔） */
private val RULE = Regex("^\\s*([-*_])(?:\\s*\\1){2,}\\s*$")

/** 图片文本框无法渲染，整段丢弃，也避免其 `[alt](url)` 部分被误识别为链接 */
private val IMAGE = Regex("!\\[[^\\]]*]\\([^)]*\\)")

/** 顺序即优先级：`**` 必须先于 `*` 匹配 */
private val INLINE = Regex(
    "(`[^`]+`)|(\\*\\*[^*]+\\*\\*)|(\\*[^*]+\\*)|(\\[[^\\]]+\\]\\([^)]+\\))|(https?://[^\\s<>)]+)"
)

private val BOLD = SpanStyle(fontWeight = FontWeight.Bold)
private val ITALIC = SpanStyle(fontStyle = FontStyle.Italic)

/** 未设字段保留基样式，使标题内粗/斜体不丢字号。 */
private fun SpanStyle?.merge(other: SpanStyle): SpanStyle = this?.merge(other) ?: other

/**
 * 无第三方解析库，逐行 + 行内正则的极简实现，只覆盖 GitHub Release 说明的常用子集（标题/列表/分隔线/
 * 行内粗斜体、代码、链接）。未覆盖的语法（表格/任务列表/代码块等）按普通文本行原样透出。
 */
fun parseChangelogMarkdown(body: String, styles: ChangelogStyles): AnnotatedString {
    if (body.isBlank()) return AnnotatedString("")
    return buildAnnotatedString {
        body.lines().forEachIndexed { index, line ->
            if (index > 0) append('\n')
            appendMarkdownLine(line, styles)
        }
    }
}

private fun AnnotatedString.Builder.appendMarkdownLine(raw: String, styles: ChangelogStyles) {
    // 前导空格保留供列表嵌套判定
    val line = raw.replace(IMAGE, "").trimEnd()
    if (line.isBlank()) return
    if (RULE.matches(line)) {
        withStyle(styles.rule) { append("─".repeat(24)) }
        return
    }
    HEADING.find(line)?.let { m ->
        val base = if (m.groupValues[1].length <= 2) styles.headingLarge else styles.headingSmall
        appendInline(m.groupValues[2], styles, base)
        return
    }
    BULLET.find(line)?.let { m ->
        append("    ".repeat(m.groupValues[1].length / 2))
        append("•  ")
        appendInline(m.groupValues[2], styles)
        return
    }
    ORDERED.find(line)?.let { m ->
        append(m.groupValues[1])
        appendInline(m.groupValues[2], styles)
        return
    }
    appendInline(line, styles)
}

/** 扫描 [INLINE] 逐 token 输出样式/链接，区间外文本按基样式输出。 */
private fun AnnotatedString.Builder.appendInline(
    text: String,
    styles: ChangelogStyles,
    base: SpanStyle? = null,
) {
    var cursor = 0
    for (m in INLINE.findAll(text)) {
        if (m.range.first > cursor) appendStyled(text.substring(cursor, m.range.first), base)
        cursor = m.range.last + 1
        val token = m.value
        when {
            token.startsWith("`") -> withStyle(styles.code) {
                append(token.substring(1, token.length - 1))
            }
            token.startsWith("**") -> appendStyled(
                token.substring(2, token.length - 2),
                base.merge(BOLD),
            )
            token.startsWith("*") -> appendStyled(
                token.substring(1, token.length - 1),
                base.merge(ITALIC),
            )
            token.startsWith("[") -> {
                val label = token.substringAfter('[').substringBefore(']')
                val url = token.substringAfter("](").removeSuffix(")")
                pushLink(LinkAnnotation.Url(url, styles.link))
                append(label)
                pop()
            }
            else -> {
                pushLink(LinkAnnotation.Url(token, styles.link))
                append(token)
                pop()
            }
        }
    }
    if (cursor < text.length) appendStyled(text.substring(cursor), base)
}

/** [style] 为 null 时原样追加。 */
private fun AnnotatedString.Builder.appendStyled(text: String, style: SpanStyle?) {
    if (style == null) append(text) else withStyle(style) { append(text) }
}

/**
 * 基础字号/颜色固定为 bodySmall + onSurfaceVariant（更新对话框场景专用）；解析结果按 body 与配色缓存，
 * 避免重组重复解析。
 */
@Composable
fun ChangelogMarkdownText(
    body: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val annotated = remember(body, colorScheme, typography) {
        parseChangelogMarkdown(
            body,
            ChangelogStyles(
                headingLarge = SpanStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = typography.bodyLarge.fontSize,
                    color = colorScheme.onSurface,
                ),
                headingSmall = SpanStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = typography.labelLarge.fontSize,
                    color = colorScheme.onSurface,
                ),
                code = SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = colorScheme.surfaceContainerHighest,
                    color = colorScheme.onSurface,
                ),
                rule = SpanStyle(color = colorScheme.outlineVariant),
                link = TextLinkStyles(
                    style = SpanStyle(
                        color = colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                    ),
                ),
            ),
        )
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = colorScheme.onSurfaceVariant,
    )
}
