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
 * 更新日志（GitHub Release markdown）渲染样式集。
 * 由 Composable 侧从 MaterialTheme 取色/取字号后传入解析纯函数，保持解析器可 JVM 单测。
 *
 * @param headingLarge 一/二级标题样式（`#`、`##`，加粗放大）
 * @param headingSmall 三级及以下标题样式（加粗小幅放大）
 * @param code 行内代码样式（等宽 + 底色）
 * @param rule 分隔线样式（`---` 渲染为同色细线字符）
 * @param link 链接样式（主题色文字 + 下划线；点击由 Text 经系统默认 UriHandler 打开）
 */
data class ChangelogStyles(
    val headingLarge: SpanStyle,
    val headingSmall: SpanStyle,
    val code: SpanStyle,
    val rule: SpanStyle,
    val link: TextLinkStyles,
)

/** 标题：1~6 个 `#` + 空格 + 内容 */
private val HEADING = Regex("^(#{1,6})\\s+(.+)$")

/** 无序列表：前导空格（标记嵌套层级）+ `-`/`*`/`+` + 内容 */
private val BULLET = Regex("^(\\s*)[-*+]\\s+(.+)$")

/** 有序列表：前导空格 + 数字 + `.`/`)` + 内容（编号原样保留） */
private val ORDERED = Regex("^(\\s*\\d+[.)]\\s+)(.+)$")

/** 分隔线：同一符号（`-` `*` `_`）重复 3 次以上（允许空格间隔） */
private val RULE = Regex("^\\s*([-*_])(?:\\s*\\1){2,}\\s*$")

/** 图片语法：文本框无法渲染，整段丢弃（也避免其 `[alt](url)` 部分被误识别为链接） */
private val IMAGE = Regex("!\\[[^\\]]*]\\([^)]*\\)")

/** 行内标记：`` `code` `` / `**粗体**` / `*斜体*` / `[文字](链接)` / 裸 URL（顺序即优先级，`**` 先于 `*`） */
private val INLINE = Regex(
    "(`[^`]+`)|(\\*\\*[^*]+\\*\\*)|(\\*[^*]+\\*)|(\\[[^\\]]+\\]\\([^)]+\\))|(https?://[^\\s<>)]+)"
)

/** 粗体 / 斜体 SpanStyle（与标题基样式 merge 叠加，避免覆盖字号） */
private val BOLD = SpanStyle(fontWeight = FontWeight.Bold)
private val ITALIC = SpanStyle(fontStyle = FontStyle.Italic)

/**
 * 基样式与行内样式叠加：非空字段以 [other] 为准，未设字段保留基样式（标题字号不丢）。
 *
 * @param other 叠加在其上的行内样式
 * @return 叠加后的样式；基样式为 null 时直接返回 [other]
 */
private fun SpanStyle?.merge(other: SpanStyle): SpanStyle = this?.merge(other) ?: other

/**
 * 更新日志 markdown → [AnnotatedString]（无第三方解析库，逐行 + 行内正则的极简实现）。
 *
 * 支持 GitHub Release 说明的常用子集：
 * - `#`~`######` 标题（≤2 个 `#` 用大号样式，其余用小号样式）
 * - `- `/`* `/`+ ` 无序列表（统一渲染为 `•`，每 2 个前导空格缩进一档）；`1.`/`1)` 有序列表保留编号
 * - `---`/`***`/`___` 分隔线（渲染为细线字符行）
 * - 行内：`**粗体**`、`*斜体*`、`` `行内代码` ``、`[文字](url)` 与裸 URL（可点击，系统打开）
 * - `![图片](url)` 丢弃；空行保留为段间空行
 *
 * 未覆盖的语法（表格/任务列表/代码块等）按普通文本行原样透出。
 *
 * @param body 原始 markdown 文本（GitHub Release body）
 * @param styles 渲染样式集（由调用方从主题取值构造）
 * @return 带样式与可点击链接的 [AnnotatedString]
 */
fun parseChangelogMarkdown(body: String, styles: ChangelogStyles): AnnotatedString {
    if (body.isBlank()) return AnnotatedString("")
    return buildAnnotatedString {
        body.lines().forEachIndexed { index, line ->
            // 行间统一补换行；空行行内容为空 → 两个连续换行形成段间空行
            if (index > 0) append('\n')
            appendMarkdownLine(line, styles)
        }
    }
}

/**
 * 追加单行 markdown 渲染结果：依次识别 分隔线 → 标题 → 无序/有序列表 → 普通段落。
 *
 * @param raw 原始行文本（保留前导空格用于列表嵌套判定）
 * @param styles 渲染样式集
 * @return 无返回值（向 Builder 追加内容）
 */
private fun AnnotatedString.Builder.appendMarkdownLine(raw: String, styles: ChangelogStyles) {
    // 行级预处理：丢弃图片语法、去掉行尾空白（前导空格保留供列表嵌套判定）
    val line = raw.replace(IMAGE, "").trimEnd()
    if (line.isBlank()) return
    // 分隔线：渲染为一整行细线字符
    if (RULE.matches(line)) {
        withStyle(styles.rule) { append("─".repeat(24)) }
        return
    }
    // 标题：≤2 个 # 用大号样式，其余用小号；行内标记继续解析（与标题基样式 merge）
    HEADING.find(line)?.let { m ->
        val base = if (m.groupValues[1].length <= 2) styles.headingLarge else styles.headingSmall
        appendInline(m.groupValues[2], styles, base)
        return
    }
    // 无序列表：统一为 • 前缀，每 2 个前导空格缩进一档
    BULLET.find(line)?.let { m ->
        append("    ".repeat(m.groupValues[1].length / 2))
        append("•  ")
        appendInline(m.groupValues[2], styles)
        return
    }
    // 有序列表：编号文本原样保留，仅正文部分解析行内标记
    ORDERED.find(line)?.let { m ->
        append(m.groupValues[1])
        appendInline(m.groupValues[2], styles)
        return
    }
    // 普通段落
    appendInline(line, styles)
}

/**
 * 追加一段文本的行内标记渲染：扫描 [INLINE] 逐 token 输出样式/链接，区间外文本按基样式输出。
 *
 * @param text 行内容（已去除块级标记）
 * @param styles 渲染样式集
 * @param base 行基样式（如标题放大加粗）；行内样式与其 merge 而非覆盖，null 时不附加
 * @return 无返回值（向 Builder 追加内容）
 */
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
            // `code`：去反引号，等宽 + 底色
            token.startsWith("`") -> withStyle(styles.code) {
                append(token.substring(1, token.length - 1))
            }
            // **粗体**：与基样式 merge（标题内粗体不丢字号）
            token.startsWith("**") -> appendStyled(
                token.substring(2, token.length - 2),
                base.merge(BOLD),
            )
            // *斜体*：同上
            token.startsWith("*") -> appendStyled(
                token.substring(1, token.length - 1),
                base.merge(ITALIC),
            )
            // [文字](url)：文字可点击，经系统默认 UriHandler 打开
            token.startsWith("[") -> {
                val label = token.substringAfter('[').substringBefore(']')
                val url = token.substringAfter("](").removeSuffix(")")
                pushLink(LinkAnnotation.Url(url, styles.link))
                append(label)
                pop()
            }
            // 裸 URL：整段作为链接文字
            else -> {
                pushLink(LinkAnnotation.Url(token, styles.link))
                append(token)
                pop()
            }
        }
    }
    if (cursor < text.length) appendStyled(text.substring(cursor), base)
}

/**
 * 按可选样式追加文本：[style] 为 null 时原样追加。
 *
 * @param text 待追加文本
 * @param style 附加样式；null 表示无样式
 * @return 无返回值（向 Builder 追加内容）
 */
private fun AnnotatedString.Builder.appendStyled(text: String, style: SpanStyle?) {
    if (style == null) append(text) else withStyle(style) { append(text) }
}

/**
 * 渲染更新日志富文本（markdown → 样式 + 可点击链接），无第三方解析库。
 *
 * 基础字号/颜色固定为 bodySmall + onSurfaceVariant（更新对话框场景专用），
 * 标题/代码/链接经 [ChangelogStyles] 覆盖；解析结果按 body 与配色缓存，避免重组重复解析。
 *
 * @param body 原始 markdown 文本（GitHub Release body）
 * @param modifier 外部传入的 Modifier
 * @return 无返回值（组合式 UI，无返回）
 */
@Composable
fun ChangelogMarkdownText(
    body: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val annotated = remember(body, colorScheme) {
        parseChangelogMarkdown(
            body,
            ChangelogStyles(
                headingLarge = SpanStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colorScheme.onSurface,
                ),
                headingSmall = SpanStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
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
