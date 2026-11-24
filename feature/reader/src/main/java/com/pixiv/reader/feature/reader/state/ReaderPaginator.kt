package com.pixiv.reader.feature.reader.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixiv.reader.core.novel.model.NovelBlock
import com.pixiv.reader.core.novel.model.NovelDocument
import kotlin.math.roundToInt

/**
 * 行级排版引擎：把段落拆成独立 [PageElement.TextLine] 流，供分页与滚动模式共用，保证换行与锚点一致。
 */
internal class ReaderLineEngine(
    private val textMeasurer: TextMeasurer,
    private val baseStyle: TextStyle,
    private val density: Density,
    private val contentWidthPx: Int,
    internal val lineHeightPx: Int,
    private val imageHeightPx: Int,
    private val paragraphSpacingPx: Int,
) {
    /** 与 ReaderImageBlock 说明文字区（内边距 + labelMedium）对齐的预留高度，漏算会把后续文字挤出页底。 */
    internal val imageCaptionHeightPx =
        with(density) { 28.dp.toPx() }.roundToInt().coerceAtLeast(1)
    private val paragraphStyle = baseStyle.copy(textAlign = TextAlign.Justify)
    private val headingStyle = baseStyle.copy(
        fontSize = baseStyle.fontSize * 1.25f,
        lineHeight = baseStyle.lineHeight * 1.15f,
        textAlign = TextAlign.Start,
        textIndent = null,
    )
    private val quoteStyle = baseStyle.copy(
        color = baseStyle.color.copy(alpha = 0.72f),
        textAlign = TextAlign.Justify,
    )
    private val separatorStyle = baseStyle.copy(
        textAlign = TextAlign.Center,
        textIndent = null,
    )

    fun buildElements(document: NovelDocument): List<PageElement> {
        val elements = mutableListOf<PageElement>()
        // 无字符区间元素（图片/分隔线）沿用上一文本行结束偏移作为锚点
        var cursor = 0

        fun addLine(
            text: String,
            style: TextStyle,
            startChar: Int,
            endChar: Int,
            heightPx: Int,
            justifyExtraPx: Float = 0f,
        ) {
            elements.add(
                PageElement.TextLine(
                    text = text,
                    style = style,
                    startChar = startChar,
                    endChar = endChar,
                    heightPx = heightPx,
                    justifyExtraPx = justifyExtraPx,
                ),
            )
        }

        fun addGap() {
            if (paragraphSpacingPx <= 0) return
            if (elements.isEmpty()) return
            elements.add(PageElement.Gap(paragraphSpacingPx))
        }

        fun addTextBlock(text: String, startChar: Int, style: TextStyle) {
            if (text.isBlank()) return
            val (cleaned, leading) = stripParagraphIndent(text)
            val lines = measureLines(cleaned, style)
            lines.forEachIndexed { index, line ->
                // 每行独立 Text 渲染，仅首行保留缩进，否则后续行也会被视为首行缩进
                val lineStyle = if (index == 0) style else style.copy(textIndent = null)
                val lineStart = startChar + leading + line.startOffset
                val lineEnd = startChar + leading + line.endOffset
                addLine(
                    text = line.text,
                    style = lineStyle,
                    startChar = lineStart,
                    endChar = lineEnd,
                    heightPx = line.heightPx,
                    justifyExtraPx = line.justifyExtraPx,
                )
                cursor = lineEnd
            }
        }

        document.blocks.forEach { block ->
            when (block) {
                is NovelBlock.Paragraph -> {
                    addTextBlock(block.text, block.startChar, paragraphStyle)
                    addGap()
                }

                is NovelBlock.Heading -> {
                    addGap()
                    addTextBlock(block.text, block.startChar, headingStyle)
                    addGap()
                }

                is NovelBlock.Quote -> {
                    addTextBlock(block.text, block.startChar, quoteStyle)
                    addGap()
                }

                is NovelBlock.Separator -> {
                    addGap()
                    addLine(block.symbol, separatorStyle, cursor, cursor, lineHeightPx)
                    addGap()
                }

                is NovelBlock.Image -> elements.add(
                    PageElement.Image(block.url, block.caption, imageHeightPx),
                )
            }
        }
        return elements
    }

    private fun measureLines(text: String, style: TextStyle): List<MeasuredLine> {
        // 用自然对齐测量取真实行宽，富余宽度留给渲染期拉伸
        val justify = style.textAlign == TextAlign.Justify
        val layout = textMeasurer.measure(
            text = AnnotatedString(text),
            style = style.copy(textAlign = TextAlign.Start),
            constraints = Constraints(maxWidth = contentWidthPx),
            overflow = TextOverflow.Clip,
        )
        // 不可改用 getLineTop/Bottom 差值：Compose 段落末行不含 lineHeight 下半空隙，
        // 多段累计会把页底 1~2 行挤出可视区。
        val rowHeightPx = with(density) { style.lineHeight.toPx() }.roundToInt().coerceAtLeast(1)
        val keptIndices = (0 until layout.lineCount)
            .filter { layout.getLineStart(it) < layout.getLineEnd(it) }
        val lastKeptIndex = keptIndices.lastOrNull() ?: return emptyList()
        val lines = mutableListOf<MeasuredLine>()
        for (i in keptIndices) {
            val start = layout.getLineStart(i)
            val end = layout.getLineEnd(i)
            // 用 getLineRight 而非 (right-left)：首行缩进会右移起点，富余宽度是距容器右缘的距离
            val justifyExtraPx = if (justify && i < lastKeptIndex) {
                (contentWidthPx - layout.getLineRight(i)).coerceAtLeast(0f)
            } else {
                0f
            }
            lines.add(
                MeasuredLine(
                    text = text.substring(start, end).trimEnd(),
                    startOffset = start,
                    endOffset = end,
                    heightPx = rowHeightPx,
                    justifyExtraPx = justifyExtraPx,
                ),
            )
        }
        return lines
    }
}

class ReaderPaginator internal constructor(
    private val engine: ReaderLineEngine,
    private val pageHeightPx: Int,
) {
    fun paginate(document: NovelDocument): List<ReaderPage> {
        val elements = engine.buildElements(document)
        val pages = mutableListOf<ReaderPage>()
        val page = mutableListOf<PageElement>()
        var usedHeight = 0

        fun closePage() {
            if (page.isEmpty()) return
            val firstText = page.firstOrNull { it is PageElement.TextLine } as? PageElement.TextLine
            val lastText = page.lastOrNull { it is PageElement.TextLine } as? PageElement.TextLine
            pages.add(
                ReaderPage(
                    startChar = firstText?.startChar ?: 0,
                    endChar = lastText?.endChar ?: 0,
                    elements = page.toList(),
                ),
            )
            page.clear()
            usedHeight = 0
        }

        for (el in elements) {
            when (el) {
                is PageElement.TextLine -> {
                    if (usedHeight + el.heightPx > pageHeightPx && page.isNotEmpty()) closePage()
                    page.add(el)
                    usedHeight += el.heightPx
                }

                is PageElement.Gap -> {
                    // 段距不落在页首，换页后若变空页则丢弃该空隙
                    if (page.isEmpty()) continue
                    if (usedHeight + el.heightPx > pageHeightPx) closePage()
                    if (page.isEmpty()) continue
                    page.add(el)
                    usedHeight += el.heightPx
                }

                is PageElement.Image -> {
                    val totalHeightPx =
                        el.heightPx + if (el.caption.isNullOrBlank()) 0 else engine.imageCaptionHeightPx
                    if (usedHeight + totalHeightPx > pageHeightPx && page.isNotEmpty()) closePage()
                    page.add(el)
                    usedHeight += totalHeightPx
                    // 图片后剩余空间不足一行时立即换页，避免页尾悬挂零散文字
                    if (usedHeight + engine.lineHeightPx > pageHeightPx) closePage()
                }
            }
        }
        closePage()
        return pages
    }
}

/** 剔除段首尾空白（含 U+3000）使缩进仅由设置控制；返回剔除的段首长度以保持全文字符偏移对齐。 */
internal fun stripParagraphIndent(text: String): Pair<String, Int> {
    val leading = text.indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 } ?: text.length
    return text.trim() to leading
}

@Composable
fun rememberReaderFontFamily(
    name: String,
    customFont: androidx.compose.ui.text.font.FontFamily? = null,
): androidx.compose.ui.text.font.FontFamily =
    remember(name, customFont) {
        when (name) {
            "custom" -> customFont ?: androidx.compose.ui.text.font.FontFamily.Serif
            "sans" -> androidx.compose.ui.text.font.FontFamily.SansSerif
            "mono" -> androidx.compose.ui.text.font.FontFamily.Monospace
            "cursive" -> androidx.compose.ui.text.font.FontFamily.Cursive
            else -> androidx.compose.ui.text.font.FontFamily.Serif
        }
    }

@Composable
fun rememberReaderTextStyle(
    fontSizeSp: Float,
    lineSpacing: Float,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    fontWeight: Int,
    indentCount: Int,
    letterSpacingEm: Float,
    textColor: Color = Color.Unspecified,
): TextStyle {
    val fontSize = fontSizeSp.sp
    return remember(
        fontSizeSp,
        lineSpacing,
        fontFamily,
        fontWeight,
        indentCount,
        letterSpacingEm,
        textColor,
    ) {
        TextStyle(
            fontSize = fontSize,
            // 行距为相对 1.6 倍基准行高的增量（-1.0..1.0）
            lineHeight = (fontSizeSp * (1.6f + lineSpacing)).sp,
            fontFamily = fontFamily,
            fontWeight = androidx.compose.ui.text.font.FontWeight(fontWeight.coerceIn(100, 900)),
            // 首行缩进按全角字宽计，并将字距计入缩进宽度以与正文对齐
            textIndent = TextIndent(
                firstLine = (fontSizeSp * indentCount * (1f + letterSpacingEm)).sp,
            ),
            letterSpacing = (fontSizeSp * letterSpacingEm).sp,
            color = textColor,
        )
    }
}

/** 图片高度占页高上限，保证图片与文字可同页混排而非独占整页。 */
const val IMAGE_MAX_HEIGHT_RATIO = 0.55f

@Composable
fun rememberReaderPages(
    document: NovelDocument?,
    baseStyle: TextStyle,
    paragraphSpacingEm: Float,
    contentWidthDp: Dp,
    pageHeightDp: Dp,
): List<ReaderPage> {
    if (document == null) return emptyList()
    val density = androidx.compose.ui.platform.LocalDensity.current

    val contentWidthPx = with(density) { contentWidthDp.roundToPx() }.coerceAtLeast(1)
    val pageHeightPx = with(density) { pageHeightDp.roundToPx() }.coerceAtLeast(1)
    val imageHeightPx = (contentWidthPx * 0.75f).roundToInt()
        .coerceAtMost((pageHeightPx * IMAGE_MAX_HEIGHT_RATIO).roundToInt())
        .coerceAtLeast(1)

    val engine = rememberReaderLineEngine(
        baseStyle = baseStyle,
        paragraphSpacingEm = paragraphSpacingEm,
        contentWidthDp = contentWidthDp,
        imageHeightDp = with(density) { imageHeightPx.toDp() },
    )
    val paginator = remember(engine, pageHeightPx) { ReaderPaginator(engine, pageHeightPx) }
    return remember(
        document,
        paginator,
    ) {
        runCatching {
            paginator.paginate(document)
        }.getOrElse { e ->
            android.util.Log.w("ReaderPaginator", "paginate failed", e)
            emptyList()
        }
    }
}

@Composable
internal fun rememberReaderLineEngine(
    baseStyle: TextStyle,
    paragraphSpacingEm: Float,
    contentWidthDp: Dp,
    imageHeightDp: Dp,
): ReaderLineEngine {
    val textMeasurer = rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val contentWidthPx = with(density) { contentWidthDp.roundToPx() }.coerceAtLeast(1)
    val imageHeightPx = with(density) { imageHeightDp.roundToPx() }.coerceAtLeast(1)
    val lineHeightPx = with(density) { baseStyle.lineHeight.toPx() }.roundToInt()
        .coerceAtLeast(1)
    val fontSizePx = with(density) { baseStyle.fontSize.toPx() }.coerceAtLeast(1f)
    val paragraphSpacingPx = (fontSizePx * paragraphSpacingEm).roundToInt()
    return remember(
        baseStyle,
        paragraphSpacingPx,
        contentWidthPx,
        imageHeightPx,
        density,
    ) {
        ReaderLineEngine(
            textMeasurer = textMeasurer,
            baseStyle = baseStyle,
            density = density,
            contentWidthPx = contentWidthPx,
            lineHeightPx = lineHeightPx,
            imageHeightPx = imageHeightPx,
            paragraphSpacingPx = paragraphSpacingPx,
        )
    }
}

@Composable
fun rememberReaderElements(
    document: NovelDocument?,
    baseStyle: TextStyle,
    paragraphSpacingEm: Float,
    contentWidthDp: Dp,
    imageHeightDp: Dp,
): List<PageElement> {
    if (document == null) return emptyList()
    val engine = rememberReaderLineEngine(
        baseStyle = baseStyle,
        paragraphSpacingEm = paragraphSpacingEm,
        contentWidthDp = contentWidthDp,
        imageHeightDp = imageHeightDp,
    )
    return remember(
        document,
        engine,
    ) {
        runCatching {
            engine.buildElements(document)
        }.getOrElse { e ->
            android.util.Log.w("ReaderLineEngine", "buildElements failed", e)
            emptyList()
        }
    }
}

@Composable
fun readerImageHeight(contentWidthDp: Dp): Dp {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val px = with(density) { contentWidthDp.roundToPx() }.coerceAtLeast(1) * 0.75f
    return with(density) { px.toInt().toDp() }
}
