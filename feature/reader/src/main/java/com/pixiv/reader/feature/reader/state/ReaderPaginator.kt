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
 * 行级排版引擎（参考 legado-with-MD3 TextChapterLayout 的段落模型）。
 *
 * 段落只是排版输入，**行是元素**：每段经换行测量拆成独立 [PageElement.TextLine] 行元素流，
 * 分页（[ReaderPaginator] 按行高切页）与滚动模式（逐行渲染）共用同一输出，换行结果完全一致。
 *
 * 段落显示方式：
 * - **缩进**：正文/引用段首行保留设置的 [TextStyle.textIndent]，后续行清除；
 *   段首/段尾空白（含全角空格 U+3000）在排版前剔除——缩进只由设置驱动（"把空格去掉"）。
 * - **段距**：每个段落结束后插入 [PageElement.Gap]（高度 = 字号 × 段距(em)，legado paragraphSpacing 语义）。
 * - **行距/字号/字距**：全部来自 [baseStyle]（rememberReaderTextStyle 由设置构造），
 *   每行按行样式渲染；行高 = style.lineHeight。
 * - **两端对齐**（legado textFullJustify）：测量时按自然行宽计算段落中间行的富余宽度，
 *   记到 [PageElement.TextLine.justifyExtraPx]，渲染期用词距/字距拉伸补足。
 *
 * 行文本从行首截取，贪心换行结果与整体测量一致，保证分页稳定。
 */
internal class ReaderLineEngine(
    private val textMeasurer: TextMeasurer,
    private val baseStyle: TextStyle,
    private val density: Density,
    private val contentWidthPx: Int,
    /** 正文基础行高（分页器图片后换页判断用）。 */
    internal val lineHeightPx: Int,
    private val imageHeightPx: Int,
    private val paragraphSpacingPx: Int,
) {
    /** 图片说明文字高度余量（ReaderImageBlock：上下 4dp 内边距 + labelMedium ~16sp + 4dp 间距）。 */
    internal val imageCaptionHeightPx =
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

    /**
     * 生成行元素流（不换页；滚动模式直接渲染的元素列表）：
     * - 每段拆成 N 个 [PageElement.TextLine]（首行保留缩进、后续行清除）+ 段尾 [PageElement.Gap]
     * - 图片按顺序插入文本流；分隔线文本不在全文里，锚点取当前排版游标（前一个文本行结束处）
     */
    fun buildElements(document: NovelDocument): List<PageElement> {
        val elements = mutableListOf<PageElement>()
        // 排版游标：上一个文本行结束位置；图片/分隔线等无字符区间元素的锚点
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

        /** 段落结束后的段距空隙（元素流开头不空行；段距 0 时不加）。 */
        fun addGap() {
            if (paragraphSpacingPx <= 0) return
            if (elements.isEmpty()) return
            elements.add(PageElement.Gap(paragraphSpacingPx))
        }

        /**
         * 段落文本块 → 行：剔除段首/段尾空白（缩进只由设置驱动），
         * 首行保留 [TextStyle.textIndent]，后续行清除——每行是独立 Text 渲染，textIndent 对每行都视为"首行"。
         */
        fun addTextBlock(text: String, startChar: Int, style: TextStyle) {
            if (text.isBlank()) return
            val (cleaned, leading) = stripParagraphIndent(text)
            val lines = measureLines(cleaned, style)
            lines.forEachIndexed { index, line ->
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
                    // 分隔线不在全文里：锚点取排版游标（前一个文本行结束处），不占用字符区间
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
        // 用自然对齐测量取真实行宽（Justify 只影响渲染间距、不影响换行位置），
        // 富余宽度留给渲染期按 legado textFullJustify 语义拉伸
        val justify = style.textAlign == TextAlign.Justify
        val layout = textMeasurer.measure(
            text = AnnotatedString(text),
            style = style.copy(textAlign = TextAlign.Start),
            constraints = Constraints(maxWidth = contentWidthPx),
            overflow = TextOverflow.Clip,
        )
        // 渲染行高：每行独立 Text 的高度 = style.lineHeight（TextLayout.height = 行数 × lineHeight）。
        // 不能用 getLineTop/Bottom 差值——Compose 对段落最后一行返回字形底（lastLineFontMetrics，
        // 不含 lineHeight 下半空隙），每段最后一行会少算约 0.85 行距；段落多时累计误差把页面
        // 底部 1~2 行挤出可视区（文字"离下面很远"、翻页才显示）。
        val rowHeightPx = with(density) { style.lineHeight.toPx() }.roundToInt().coerceAtLeast(1)
        // 跳过空行（行首/行尾换行），末行判断基于实际保留的行
        val keptIndices = (0 until layout.lineCount)
            .filter { layout.getLineStart(it) < layout.getLineEnd(it) }
        val lastKeptIndex = keptIndices.lastOrNull() ?: return emptyList()
        val lines = mutableListOf<MeasuredLine>()
        for (i in keptIndices) {
            val start = layout.getLineStart(i)
            val end = layout.getLineEnd(i)
            // 段落中间行：把「内容宽 - 行右缘」作为两端对齐富余（末行自然排布）。
            // 用 getLineRight 而非 (right-left) 差：首行缩进会让行右缘右移，
            // 富余 = 距容器右缘的距离，拉伸后整行正好贴齐右缘。
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

/**
 * 分页器：把行元素流按页高切分（legado 语义——行是元素，页面 = 行序列 + 换页规则）。
 *
 * 换页规则（与 legado prepareNextPageIfNeed 等效）：
 * - 行放不下即换页，段落可跨页；段距空隙不落在页首
 * - 图片参与文本流分页（不独占整页）：高度已自适应（≤ 页高 * IMAGE_MAX_HEIGHT_RATIO），
 *   图片后剩余空间不足一行时立即换页，避免页尾悬挂零散文字
 */
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
                    // 页面顶部不空行：换页后重新检查，避免空页插入
                    if (page.isEmpty()) continue
                    if (usedHeight + el.heightPx > pageHeightPx) closePage()
                    if (page.isEmpty()) continue
                    page.add(el)
                    usedHeight += el.heightPx
                }

                is PageElement.Image -> {
                    // 预留说明文字高度（ReaderImageBlock 的 caption 在图片下方，分页漏算会挤出后续文字）
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

/**
 * 剔除段落首尾空白（含全角空格 U+3000）——缩进只由阅读器「缩进」设置驱动
 * （legado 段落缩进语义：正文里的空白不参与缩进显示）。
 *
 * @return 去空白后的文本 + 剔除的段首字符数（段首字符数用于保持全文字符偏移映射）。
 */
internal fun stripParagraphIndent(text: String): Pair<String, Int> {
    val leading = text.indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 } ?: text.length
    return text.trim() to leading
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

/**
 * 阅读正文基础样式（字号/行距/字体族/字重/首行缩进/字距/颜色）。
 *
 * - 行距增量（[lineSpacing]，-1.0..1.0）：实际行高倍数 = 1.6 + 增量
 * - 缩进 = 全角字宽（= 字号）× 缩进数；字距非 0 时按 legado 语义把字距计入缩进宽度
 *   （legado upStyle：indentWidth += letterSpacing * textSize）
 * - 字距 = 字距(em) × 字号
 */
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
            // 行距增量（-1.0..1.0）：实际行高倍数 = 1.6 + 增量
            lineHeight = (fontSizeSp * (1.6f + lineSpacing)).sp,
            fontFamily = fontFamily,
            fontWeight = androidx.compose.ui.text.font.FontWeight(fontWeight.coerceIn(100, 900)),
            // 首行缩进 = 全角字宽（全角字宽 = 字号）× 缩进数 ×（1 + 字距）
            textIndent = TextIndent(
                firstLine = (fontSizeSp * indentCount * (1f + letterSpacingEm)).sp,
            ),
            // 字距 = 字距(em) × 字号
            letterSpacing = (fontSizeSp * letterSpacingEm).sp,
            color = textColor,
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
    baseStyle: TextStyle,
    paragraphSpacingEm: Float,
    contentWidthDp: Dp,
    pageHeightDp: Dp,
): List<ReaderPage> {
    if (document == null) return emptyList()
    val density = androidx.compose.ui.platform.LocalDensity.current

    val contentWidthPx = with(density) { contentWidthDp.roundToPx() }.coerceAtLeast(1)
    val pageHeightPx = with(density) { pageHeightDp.roundToPx() }.coerceAtLeast(1)
    // 图片高度自适应：理想为内容宽 * 0.75，但不超过页高 * IMAGE_MAX_HEIGHT_RATIO，
    // 保证图片与文字可同页排版（不独占整页）
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

/**
 * 构造行级排版引擎（Compose 可组合版本）。
 * 分页（[rememberReaderPages]）与滚动模式（[rememberReaderElements]）共用同一引擎，
 * 保证两种模式下换行/样式/锚点完全一致。
 */
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

/**
 * 计算行元素流（滚动模式直接渲染的元素列表，不换页）：
 * 与分页模式共用 [ReaderLineEngine]，段落经换行测量拆成独立 [PageElement.TextLine] 行元素。
 */
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

/** 插图页高度（与分页估算一致）。 */
@Composable
fun readerImageHeight(contentWidthDp: Dp): Dp {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val px = with(density) { contentWidthDp.roundToPx() }.coerceAtLeast(1) * 0.75f
    return with(density) { px.toInt().toDp() }
}
