package com.pixiv.reader.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixiv.reader.core.novel.NovelBlock
import com.pixiv.reader.core.novel.NovelDocument
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 分页结果：一页要么是排版好的文本页，要么是插图页。
 */
sealed class ReaderPage {
    abstract val startChar: Int
    abstract val endChar: Int

    /** 文本页：[annotated] 已按行应用样式（字号/行距/字体/对齐/缩进）。 */
    data class Text(
        override val startChar: Int,
        override val endChar: Int,
        val annotated: AnnotatedString,
    ) : ReaderPage()

    /** 插图页（独立成页）。 */
    data class Image(
        val url: String,
        val caption: String?,
    ) : ReaderPage() {
        override val startChar: Int = 0
        override val endChar: Int = 0
    }
}

/** 一页里的一行（保留全局字符区间用于进度映射）。 */
internal data class PageLine(
    val text: String,
    val style: TextStyle,
    val startChar: Int,
    val endChar: Int,
)

internal data class MeasuredLine(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val heightPx: Int,
)

/**
 * 自研排版引擎：把结构化段落按「内容宽度 × 页高」切分为文本页。
 *
 * 原理：逐块用 [TextMeasurer] 测量出所有行，再按页高把行分配到各页；
 * 每页把行文本 + 行样式拼成 AnnotatedString 渲染（行文本从行首截取，
 * 贪心换行结果与整体测量一致，保证分页稳定）。
 */
class ReaderPaginator(
    private val textMeasurer: TextMeasurer,
    private val baseStyle: TextStyle,
    private val contentWidthPx: Int,
    private val pageHeightPx: Int,
    private val lineHeightPx: Int,
    private val imageHeightPx: Int,
) {
    private val paragraphStyle = baseStyle.copy(textAlign = TextAlign.Justify)
    private val headingStyle = baseStyle.copy(
        fontSize = baseStyle.fontSize * 1.25f,
        lineHeight = baseStyle.lineHeight * 1.15f,
        textAlign = TextAlign.Start,
    )
    private val quoteStyle = baseStyle.copy(
        color = baseStyle.color.copy(alpha = 0.72f),
        textAlign = TextAlign.Justify,
    )
    private val separatorStyle = baseStyle.copy(textAlign = TextAlign.Center)

    fun paginate(document: NovelDocument): List<ReaderPage> {
        val pages = mutableListOf<ReaderPage>()
        val pageLines = mutableListOf<PageLine>()
        var usedHeight = 0

        fun closePage() {
            if (pageLines.isEmpty()) return
            val startChar = pageLines.first().startChar
            val endChar = pageLines.last().endChar
            val annotated = buildAnnotatedString {
                pageLines.forEach { line ->
                    withStyle(line.style.toSpanStyle()) { append(line.text) }
                    append("\n")
                }
            }
            pages.add(ReaderPage.Text(startChar = startChar, endChar = endChar, annotated = annotated))
            pageLines.clear()
            usedHeight = 0
        }

        fun addLine(text: String, style: TextStyle, startChar: Int, endChar: Int, heightPx: Int) {
            if (usedHeight + heightPx > pageHeightPx && pageLines.isNotEmpty()) closePage()
            pageLines.add(PageLine(text = text, style = style, startChar = startChar, endChar = endChar))
            usedHeight += heightPx
        }

        /** 段落/标题间的空行间距（页面顶部不空行）。 */
        fun addSpacer() {
            if (pageLines.isEmpty()) return
            if (usedHeight + lineHeightPx > pageHeightPx) closePage()
            if (pageLines.isEmpty()) return // closePage 后重新检查，避免 last() 空列表崩溃
            val anchor = pageLines.last().endChar
            pageLines.add(PageLine(text = "", style = baseStyle, startChar = anchor, endChar = anchor))
            usedHeight += lineHeightPx
        }

        fun addTextBlock(text: String, startChar: Int, style: TextStyle) {
            if (text.isBlank()) return
            val lines = measureLines(text, style)
            lines.forEach { line ->
                addLine(
                    text = line.text,
                    style = style,
                    startChar = startChar + line.startOffset,
                    endChar = startChar + line.endOffset,
                    heightPx = line.heightPx,
                )
            }
        }

        document.blocks.forEach { block ->
            when (block) {
                is NovelBlock.Paragraph -> {
                    addTextBlock(block.text, block.startChar, paragraphStyle)
                    addSpacer()
                }
                is NovelBlock.Heading -> {
                    addSpacer()
                    addTextBlock(block.text, block.startChar, headingStyle)
                    addSpacer()
                }
                is NovelBlock.Quote -> {
                    addTextBlock(block.text, block.startChar, quoteStyle)
                    addSpacer()
                }
                is NovelBlock.Separator -> {
                    addSpacer()
                    addLine(
                        text = block.symbol,
                        style = separatorStyle,
                        startChar = block.startChar,
                        endChar = block.startChar + block.symbol.length,
                        heightPx = lineHeightPx,
                    )
                    addSpacer()
                }
                is NovelBlock.Image -> {
                    if (pageLines.isNotEmpty()) closePage()
                    pages.add(ReaderPage.Image(block.url, block.caption))
                }
            }
        }
        closePage()
        return pages
    }

    private fun measureLines(text: String, style: TextStyle): List<MeasuredLine> {
        val layout = textMeasurer.measure(
            text = AnnotatedString(text),
            style = style,
            constraints = Constraints(maxWidth = contentWidthPx),
            overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
        )
        val lines = mutableListOf<MeasuredLine>()
        for (i in 0 until layout.lineCount) {
            val start = layout.getLineStart(i)
            val end = layout.getLineEnd(i)
            if (start >= end) continue // 空行（行首/行尾换行）跳过
            val height = ceil(layout.getLineBottom(i) - layout.getLineTop(i)).toInt().coerceAtLeast(1)
            lines.add(
                MeasuredLine(
                    text = text.substring(start, end).trimEnd(),
                    startOffset = start,
                    endOffset = end,
                    heightPx = height,
                ),
            )
        }
        return lines
    }
}

/** 阅读器字体族名称 → FontFamily。 */
@Composable
fun rememberReaderFontFamily(name: String): androidx.compose.ui.text.font.FontFamily =
    remember(name) {
        when (name) {
            "sans" -> androidx.compose.ui.text.font.FontFamily.SansSerif
            "mono" -> androidx.compose.ui.text.font.FontFamily.Monospace
            "cursive" -> androidx.compose.ui.text.font.FontFamily.Cursive
            else -> androidx.compose.ui.text.font.FontFamily.Serif
        }
    }

/** 阅读正文基础样式（字号/行距/字体族）。 */
@Composable
fun rememberReaderTextStyle(
    fontSizeSp: Float,
    lineHeightMultiplier: Float,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
): TextStyle {
    val fontSize = fontSizeSp.sp
    return remember(fontSizeSp, lineHeightMultiplier, fontFamily) {
        TextStyle(
            fontSize = fontSize,
            lineHeight = (fontSizeSp * lineHeightMultiplier).sp,
            fontFamily = fontFamily,
        )
    }
}

/**
 * 计算分页（Compose 可组合版本）。
 * 页高 = [pageHeightDp]（阅读区可用高度），图片页高 = 内容宽 * 0.75。
 */
@Composable
fun rememberReaderPages(
    document: NovelDocument?,
    fontSizeSp: Float,
    lineHeightMultiplier: Float,
    fontFamilyName: String,
    contentWidthDp: Dp,
    pageHeightDp: Dp,
): List<ReaderPage> {
    if (document == null) return emptyList()
    val textMeasurer = rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val fontFamily = rememberReaderFontFamily(fontFamilyName)
    val baseStyle = rememberReaderTextStyle(fontSizeSp, lineHeightMultiplier, fontFamily)

    val contentWidthPx = with(density) { contentWidthDp.roundToPx() }.coerceAtLeast(1)
    val pageHeightPx = with(density) { pageHeightDp.roundToPx() }.coerceAtLeast(1)
    val lineHeightPx = with(density) { (fontSizeSp * lineHeightMultiplier).sp.toPx() }.roundToInt()
        .coerceAtLeast(1)
    val imageHeightPx = (contentWidthPx * 0.75f).roundToInt().coerceAtLeast(1)

    return remember(
        document,
        fontSizeSp,
        lineHeightMultiplier,
        fontFamilyName,
        contentWidthPx,
        pageHeightPx,
    ) {
        runCatching {
            ReaderPaginator(
                textMeasurer = textMeasurer,
                baseStyle = baseStyle,
                contentWidthPx = contentWidthPx,
                pageHeightPx = pageHeightPx,
                lineHeightPx = lineHeightPx,
                imageHeightPx = imageHeightPx,
            ).paginate(document)
        }.getOrElse { e ->
            android.util.Log.w("ReaderPaginator", "paginate failed", e)
            emptyList()
        }
    }
}

/** 插图页高度（与分页估算一致）。 */
@Composable
fun readerImageHeight(contentWidthDp: Dp): Dp {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val px = with(density) { contentWidthDp.roundToPx() }.coerceAtLeast(1) * 0.75f
    return with(density) { px.toInt().toDp() }
}
