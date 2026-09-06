package com.pixiv.reader.feature.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.reader.state.PageElement
import com.pixiv.reader.feature.reader.state.ReaderPage

/** 正文内容内边距（翻页 / 仿真 / 渲染共用）。 */
internal val PAGE_H_PADDING = 24.dp
internal val PAGE_V_PADDING = 16.dp

/** 底部信息条高度（左侧章节标题 + 右侧分页），正文页高需为其避让。 */
internal val READER_STATUS_BAR_HEIGHT = 20.dp

/**
 * 底部对齐行间微调量（legado upLinesPosition / textBottomJustify 语义）：
 * 仅当页底剩余空间不足一行时，把富余均分到 [gapCount] 个文本行间空隙（末行贴底）；
 * 剩余 ≥ 一行高时不拉伸——行距必须由设置直接决定（无条件拉伸会让渲染行距恒等于
 * 页高/行数，行距设置被吞掉、视觉上"怎么改都一样"）。
 *
 * @return 每个行间空隙应增加的 px；不需要微调时返回 0。
 */
internal fun bottomJustifyGapPx(
    surplus: Float,
    lastLineHeightPx: Float,
    gapCount: Int,
): Float =
    if (gapCount > 0 && surplus > 0f && surplus < lastLineHeightPx) {
        surplus / gapCount
    } else {
        0f
    }

/**
 * 渲染单页内容（文本行 + 段距空隙 + 图片混合排版）。
 * 供翻页模式与仿真模式共用（图片高度来自分页器自适应值，图片块含说明文字）。
 *
 * 段落显示方式（参考 legado-with-MD3）：
 * - **字号 / 行距 / 字距 / 缩进** 全部来自分页器产出的行样式 [PageElement.TextLine.style]
 *   （rememberReaderTextStyle 由设置构造；缩进只来自设置，段首空白已在分页时剔除）
 * - **段距**：[PageElement.Gap] 按原高度渲染为空隙，不参与拉伸
 * - **两端对齐**（legado textFullJustify）：段落中间行按 [PageElement.TextLine.justifyExtraPx]
 *   把富余宽度分布到词距（有空格）或字距（纯中文行）上
 * - **底部对齐**（legado textBottomJustify/upLinesPosition，必备功能无开关）：页底
 *   剩余不足一行时，把富余均分到文本行间空隙（末行贴页底）。
 */
@Composable
internal fun RenderReaderPage(
    page: ReaderPage,
    containerHeight: Dp,
    onOpenImage: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val containerHeightPx = with(density) { containerHeight.toPx() }.coerceAtLeast(1f)
    // 参与拉伸的文本行（段距空隙/图片不参与）
    val textLines = page.elements
        .filterIsInstance<PageElement.TextLine>()
        .filter { it.text.isNotEmpty() }
    var baseTotalPx = 0f
    page.elements.forEach { el ->
        baseTotalPx += when (el) {
            is PageElement.TextLine -> el.heightPx.toFloat()
            is PageElement.Gap -> el.heightPx.toFloat()
            is PageElement.Image -> el.heightPx.toFloat()
        }
    }
    // legado upLinesPosition（textBottomJustify）语义：末行贴底是必备功能（无开关）。
    // 页底剩余不足一行时把富余均分到文本行间空隙让末行贴底（首个文本行贴页顶、不加空隙）。
    // 分页是"放不下才换页"，页底剩余恒小于一行高；行距基础值仍 = el.style.lineHeight（由设置决定），
    // 拉伸只是把不足一行的剩余微调均分，不替换行距设置。
    val lastLineHeightPx = textLines.lastOrNull()?.heightPx?.toFloat() ?: 0f
    val surplus = containerHeightPx - baseTotalPx
    val extraPerGapPx = bottomJustifyGapPx(surplus, lastLineHeightPx, textLines.size - 1)
    Column(modifier = modifier) {
        var textLineIndex = 0
        page.elements.forEach { el ->
            when (el) {
                is PageElement.TextLine -> {
                    // 行间空隙微调（legado：行 top 递增 tj×i 的等效实现）
                    if (textLineIndex > 0 && extraPerGapPx > 0f) {
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .height(with(density) { extraPerGapPx.toDp() }),
                        )
                    }
                    textLineIndex++
                    // 行元素高度由 Box 显式撑起（= 分页行高）：单行 Text 的测量高度只到字形底
                    // （Compose 最后一行语义，不含 lineHeight），若不显式控高，行距设置对
                    // 逐行渲染完全无效、分页也会失真（行距高时内容堆在顶部、负行距时溢出）。
                    // Text 自身去掉 lineHeight：字形顶贴 Box 顶，行与行之间距离 = Box 高度。
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(with(density) { el.heightPx.toDp() }),
                    ) {
                        Text(
                            text = if (el.justifyExtraPx > 1f) {
                                justifyLine(el, density)
                            } else {
                                AnnotatedString(el.text)
                            },
                            style = el.style.copy(lineHeight = TextUnit.Unspecified),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                is PageElement.Gap -> Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(with(density) { el.heightPx.toDp() }),
                )

                is PageElement.Image -> ReaderImageBlock(
                    url = el.url,
                    caption = el.caption,
                    height = with(density) { el.heightPx.toDp() },
                    onOpenImage = onOpenImage,
                )
            }
        }
    }
}

/** 中缝阴影宽度（dp）：跨页中缝立体感渐变条。 */
internal val SPREAD_SEAM_WIDTH = 12.dp

/**
 * 渲染跨页（翻页/仿真模式的翻页单元，见 [com.pixiv.reader.feature.reader.state.ReaderSpread]）。
 *
 * - columns = 1：单页模式，[left] 占满整宽（与单页渲染完全一致）
 * - columns = 2：双页并排，左右各半宽；[right] 为 null（章节末页落单）时右半留纸底色；
 *   叠加中缝竖向渐变阴影形成书脊立体感
 *
 * @param left 左页（columns = 1 时为整宽页；为 null 时不渲染内容，仅留背景）
 * @param right 右页（columns = 1 恒为 null；columns = 2 末页落单时为 null）
 * @param columns 列数：1 单页整宽 / 2 双页并排
 * @param containerHeight 容器高度（RenderReaderPage 底部贴底微调用）
 * @param contentTopInset 内容顶部额外避让（沉浸式纸面覆盖状态栏时 = 状态栏高度，
 *   文字从状态栏下方开始而纸面延伸到屏幕顶）
 * @param onOpenImage 点击图片回调（全屏查看，透传给 [RenderReaderPage]）
 * @param showSeam 是否绘制中缝阴影（静态跨页显示 true；仿真叶翻的叶面层不需要）
 */
@Composable
internal fun RenderSpreadColumns(
    left: ReaderPage?,
    right: ReaderPage?,
    columns: Int,
    containerHeight: Dp,
    contentTopInset: Dp = 0.dp,
    onOpenImage: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 单页模式：与原单页渲染路径完全一致（同一 padding、同一整宽约束）
    if (columns <= 1) {
        if (left != null) {
            RenderReaderPage(
                left,
                containerHeight,
                onOpenImage,
                modifier
                    .fillMaxSize()
                    .padding(
                        start = PAGE_H_PADDING,
                        end = PAGE_H_PADDING,
                        top = PAGE_V_PADDING + contentTopInset,
                        bottom = PAGE_V_PADDING,
                    ),
            )
        }
        return
    }
    // 双页模式：两列各占一半，各自内边距；末页落单时右半留纸底色
    Box(modifier.fillMaxSize()) {
        Row(modifier = Modifier.matchParentSize()) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (left != null) {
                    RenderReaderPage(
                        left,
                        containerHeight,
                        onOpenImage,
                        Modifier
                            .fillMaxSize()
                            .padding(
                                start = PAGE_H_PADDING,
                                end = PAGE_H_PADDING,
                                top = PAGE_V_PADDING + contentTopInset,
                                bottom = PAGE_V_PADDING,
                            ),
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (right != null) {
                    RenderReaderPage(
                        right,
                        containerHeight,
                        onOpenImage,
                        Modifier
                            .fillMaxSize()
                            .padding(
                                start = PAGE_H_PADDING,
                                end = PAGE_H_PADDING,
                                top = PAGE_V_PADDING + contentTopInset,
                                bottom = PAGE_V_PADDING,
                            ),
                    )
                }
            }
        }
        // 中缝阴影：居中竖向渐变条（中心深、两侧渐隐），营造书脊凹陷感
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(SPREAD_SEAM_WIDTH)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0f),
                        0.5f to Color.Black.copy(alpha = 0.10f),
                        1f to Color.Black.copy(alpha = 0f),
                    ),
                ),
        )
    }
}

/**
 * 两端对齐（legado textFullJustify 的 addCharsToLineMiddle 语义）：
 * 把行富余宽度 [PageElement.TextLine.justifyExtraPx] 分布到行内空隙上——
 * - 行内有空格：每个空格词距 + 富余/空格数（inter-word）
 * - 纯中文行：每个字符间距 + 富余/(字符数-1)（inter-character，legado 无空格分支）
 *
 * 注意：SpanStyle.letterSpacing 会覆盖行样式的基础字距，因此跨度值 = 基础字距 + 富余增量。
 */
internal fun justifyLine(line: PageElement.TextLine, density: Density): AnnotatedString {
    val text = line.text
    val fontSizePx = with(density) { line.style.fontSize.toPx() }.coerceAtLeast(1f)
    // 基础字距（em）：行样式 letterSpacing / 字号（letterSpacing 本身已是 em 单位则直接用）
    val baseEm = when {
        line.style.letterSpacing == TextUnit.Unspecified -> 0f
        line.style.letterSpacing.type == TextUnitType.Em -> line.style.letterSpacing.value
        else -> line.style.letterSpacing.value / line.style.fontSize.value
    }
    val extraPx = line.justifyExtraPx
    return buildAnnotatedString {
        append(text)
        val spaces = text.count { it == ' ' || it == '\u3000' }
        if (spaces > 0) {
            // 词距拉伸：每个空格词距 + 富余/空格数
            val extraEm = extraPx / fontSizePx / spaces
            var index = 0
            while (index < text.length) {
                if (text[index] == ' ' || text[index] == '\u3000') {
                    addStyle(
                        SpanStyle(letterSpacing = (baseEm + extraEm).em),
                        index,
                        index + 1,
                    )
                }
                index++
            }
        } else {
            // 无空格：字符间距均匀拉伸（最后一个字符不加，避免行尾多出半个间距）
            val gaps = text.length - 1
            if (gaps > 0) {
                val extraEm = extraPx / fontSizePx / gaps
                addStyle(
                    SpanStyle(letterSpacing = (baseEm + extraEm).em),
                    0,
                    gaps,
                )
            }
        }
    }
}

/**
 * 插图块：图片 + 可选说明文字，点击图片全屏查看。
 *
 * @param url 图片地址
 * @param caption 说明文字（可空）
 * @param height 图片高度
 * @param onOpenImage 点击图片回调（全屏查看）
 */
@Composable
internal fun ReaderImageBlock(
    url: String,
    caption: String?,
    height: Dp,
    onOpenImage: (String) -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixivImage(
            url = url,
            contentDescription = caption,
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                // 点击全屏查看；语义标签让无障碍服务可识别为可点击图片
                .clickable(onClickLabel = caption) { onOpenImage(url) },
            contentScale = ContentScale.Fit,
            // 阅读器图片需明确反馈：加载中底部进度条、失败断图图标（否则占位块无法区分加载/失败）
            showProgress = true,
        )
        if (!caption.isNullOrBlank()) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}
