package com.pixiv.reader.feature.reader.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixiv.reader.core.novel.NovelBlock
import com.pixiv.reader.core.novel.NovelDocument
import kotlin.math.roundToInt

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
    private val density: Density,
    private val contentWidthPx: Int,
    private val pageHeightPx: Int,
    private val lineHeightPx: Int,
    private val imageHeightPx: Int,
    private val paragraphSpacingPx: Int,
) {
    /** 图片说明文字高度余量（ReaderImageBlock：上下 4dp 内边距 + labelMedium ~16sp + 4dp 间距）。 */
    private val imageCaptionHeightPx =
        with(density) { 28.dp.toPx() }.roundToInt().coerceAtLeast(1)
    private val paragraphStyle = baseStyle.copy(textAlign = TextAlign.Justify)
    private val headingStyle = baseStyle.copy(
        fontSize = baseStyle.fontSize * 1.25f,
        lineHeight = baseStyle.lineHeight * 1.15f,
        textAlign = TextAlign.Start,
        // 标题不缩进（正文首行缩进继承自 baseStyle）
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

    fun paginate(document: NovelDocument): List<ReaderPage> {
        val pages = mutableListOf<ReaderPage>()
        val elements = mutableListOf<PageElement>()
        var usedHeight = 0

        fun closePage() {
            if (elements.isEmpty()) return
            val firstText = elements.firstOrNull { it is PageElement.TextLine } as? PageElement.TextLine
            val lastText = elements.lastOrNull { it is PageElement.TextLine } as? PageElement.TextLine
            pages.add(
                ReaderPage(
                    startChar = firstText?.startChar ?: 0,
                    endChar = lastText?.endChar ?: 0,
                    elements = elements.toList(),
                ),
            )
            elements.clear()
            usedHeight = 0
        }

        fun addLine(text: String, style: TextStyle, startChar: Int, endChar: Int, heightPx: Int) {
            if (usedHeight + heightPx > pageHeightPx && elements.isNotEmpty()) closePage()
            elements.add(PageElement.TextLine(text, style, startChar, endChar, heightPx))
            usedHeight += heightPx
        }

        /** 段落/标题间的空行间距（页面顶部不空行；段距 0 时不加）。 */
        fun addSpacer() {
            if (paragraphSpacingPx <= 0) return
            if (elements.isEmpty()) return
            if (usedHeight + paragraphSpacingPx > pageHeightPx) closePage()
            if (elements.isEmpty()) return // closePage 后重新检查，避免 last() 空列表崩溃
            val anchor = elements.last().endChar
            elements.add(PageElement.TextLine("", baseStyle, anchor, anchor, paragraphSpacingPx))
            usedHeight += paragraphSpacingPx
        }

        /**
         * 图片参与文本流分页（不独占整页）：高度已自适应（≤ 页高 * IMAGE_MAX_HEIGHT_RATIO），
         * 图片后剩余空间不足一行时立即换页，避免页尾悬挂零散文字。
         * 预留说明文字高度（ReaderImageBlock 的 caption 在图片下方，分页漏算会挤出后续文字）。
         */
        fun addImage(url: String, caption: String?) {
            val totalHeightPx =
                imageHeightPx + if (caption.isNullOrBlank()) 0 else imageCaptionHeightPx
            if (usedHeight + totalHeightPx > pageHeightPx && elements.isNotEmpty()) closePage()
            elements.add(PageElement.Image(url, caption, imageHeightPx))
            usedHeight += totalHeightPx
            if (usedHeight + lineHeightPx > pageHeightPx) closePage()
        }

        fun addTextBlock(text: String, startChar: Int, style: TextStyle) {
            if (text.isBlank()) return
            val lines = measureLines(text, style)
            lines.forEachIndexed { index, line ->
                // 分页后每行是独立 Text 渲染，textIndent 对每行都视为"首行"——
                // 仅段落首行保留缩进，后续行必须清除，否则每行都缩进
                val lineStyle = if (index == 0) style else style.copy(textIndent = null)
                addLine(
                    text = line.text,
                    style = lineStyle,
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
                is NovelBlock.Image -> addImage(block.url, block.caption)
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
        // 渲染行高：每行独立 Text 的高度 = style.lineHeight（TextLayout.height = 行数 × lineHeight）。
        // 不能用 getLineTop/Bottom 差值——Compose 对段落最后一行返回字形底（lastLineFontMetrics，
        // 不含 lineHeight 下半空隙），每段最后一行会少算约 0.85 行距；段落多时累计误差把页面
        // 底部 1~2 行挤出可视区（文字"离下面很远"、翻页才显示）。
        val rowHeightPx = with(density) { style.lineHeight.toPx() }.roundToInt().coerceAtLeast(1)
        val lines = mutableListOf<MeasuredLine>()
        for (i in 0 until layout.lineCount) {
            val start = layout.getLineStart(i)
            val end = layout.getLineEnd(i)
            if (start >= end) continue // 空行（行首/行尾换行）跳过
            lines.add(
                MeasuredLine(
                    text = text.substring(start, end).trimEnd(),
                    startOffset = start,
                    endOffset = end,
                    heightPx = rowHeightPx,
                ),
            )
        }
        return lines
    }
}

/** 阅读器字体族名称 → FontFamily（"custom" 使用 [customFont]，未设置则回退衬线）。 */
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

/** 阅读正文基础样式（字号/行距/字体族/字重/首行缩进/字距）。 */
@Composable
fun rememberReaderTextStyle(
    fontSizeSp: Float,
    lineSpacing: Float,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    fontWeight: Int,
    indentCount: Int,
    letterSpacingEm: Float,
): TextStyle {
    val fontSize = fontSizeSp.sp
    return remember(
        fontSizeSp,
        lineSpacing,
        fontFamily,
        fontWeight,
        indentCount,
        letterSpacingEm,
    ) {
        TextStyle(
            fontSize = fontSize,
            // 行距增量（-1.0..1.0）：实际行高倍数 = 1.6 + 增量
            lineHeight = (fontSizeSp * (1.6f + lineSpacing)).sp,
            fontFamily = fontFamily,
            fontWeight = androidx.compose.ui.text.font.FontWeight(fontWeight.coerceIn(100, 900)),
            // 首行缩进 = 全角空格宽度（全角字宽 = 字号）
            textIndent = TextIndent(firstLine = (fontSizeSp * indentCount).sp),
            // 字距 = 字距(em) × 字号
            letterSpacing = (fontSizeSp * letterSpacingEm).sp,
        )
    }
}

/**
 * 计算分页（Compose 可组合版本）。
 * 页高 = [pageHeightDp]（阅读区可用高度）。
 * 图片高度自适应：理想为内容宽 * 0.75，但不超过页高 * [IMAGE_MAX_HEIGHT_RATIO]，
 * 保证图片与文字可同页排版（不独占整页）。
 */
const val IMAGE_MAX_HEIGHT_RATIO = 0.55f

@Composable
fun rememberReaderPages(
    document: NovelDocument?,
    fontSizeSp: Float,
    lineSpacing: Float,
    fontFamilyName: String,
    fontWeight: Int,
    indentCount: Int,
    letterSpacingEm: Float,
    paragraphSpacingEm: Float,
    customFont: androidx.compose.ui.text.font.FontFamily? = null,
    contentWidthDp: Dp,
    pageHeightDp: Dp,
): List<ReaderPage> {
    if (document == null) return emptyList()
    val textMeasurer = rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val fontFamily = rememberReaderFontFamily(fontFamilyName, customFont)
    val baseStyle = rememberReaderTextStyle(
        fontSizeSp,
        lineSpacing,
        fontFamily,
        fontWeight,
        indentCount,
        letterSpacingEm,
    )

    val contentWidthPx = with(density) { contentWidthDp.roundToPx() }.coerceAtLeast(1)
    val pageHeightPx = with(density) { pageHeightDp.roundToPx() }.coerceAtLeast(1)
    val lineHeightPx = with(density) { (fontSizeSp * (1.6f + lineSpacing)).sp.toPx() }.roundToInt()
        .coerceAtLeast(1)
    val paragraphSpacingPx = with(density) { (fontSizeSp * paragraphSpacingEm).sp.toPx() }
        .roundToInt()
    val imageHeightPx = (contentWidthPx * 0.75f).roundToInt()
        .coerceAtMost((pageHeightPx * IMAGE_MAX_HEIGHT_RATIO).roundToInt())
        .coerceAtLeast(1)

    return remember(
        document,
        fontSizeSp,
        lineSpacing,
        fontFamilyName,
        fontWeight,
        indentCount,
        letterSpacingEm,
        paragraphSpacingEm,
        customFont,
        contentWidthPx,
        pageHeightPx,
    ) {
        runCatching {
            ReaderPaginator(
                textMeasurer = textMeasurer,
                baseStyle = baseStyle,
                density = density,
                contentWidthPx = contentWidthPx,
                pageHeightPx = pageHeightPx,
                lineHeightPx = lineHeightPx,
                imageHeightPx = imageHeightPx,
                paragraphSpacingPx = paragraphSpacingPx,
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
