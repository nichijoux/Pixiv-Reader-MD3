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

internal val PAGE_H_PADDING = 24.dp
internal val PAGE_V_PADDING = 16.dp

/** 底部信息条高度，正文页高须为其避让。 */
internal val READER_STATUS_BAR_HEIGHT = 20.dp

/**
 * 底部对齐微调：仅当页底剩余不足一行高时把富余均分到行间空隙；
 * 剩余 ≥ 一行时不拉伸，否则渲染行距会恒等于页高/行数，吞掉用户的行距设置。
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

internal fun Modifier.pageInsets(contentTopInset: Dp): Modifier = padding(
    start = PAGE_H_PADDING,
    end = PAGE_H_PADDING,
    top = PAGE_V_PADDING + contentTopInset,
    bottom = PAGE_V_PADDING,
)

@Composable
internal fun PageElementView(
    element: PageElement,
    density: Density,
    onOpenImage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (element) {
        is PageElement.TextLine -> {
            // Box 按分页行高撑起且 Text 去除 lineHeight：单行 Text 测量仅到字形底，
            // 不显式控高会使行距设置失效并导致分页失真。
            Box(
                modifier
                    .fillMaxWidth()
                    .height(with(density) { element.heightPx.toDp() }),
            ) {
                Text(
                    text = if (element.justifyExtraPx > 1f) {
                        justifyLine(element, density)
                    } else {
                        AnnotatedString(element.text)
                    },
                    style = element.style.copy(lineHeight = TextUnit.Unspecified),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        is PageElement.Gap -> Spacer(
            modifier
                .fillMaxWidth()
                .height(with(density) { element.heightPx.toDp() }),
        )

        is PageElement.Image -> ReaderImageBlock(
            url = element.url,
            caption = element.caption,
            height = with(density) { element.heightPx.toDp() },
            onOpenImage = onOpenImage,
        )
    }
}

@Composable
internal fun RenderReaderPage(
    page: ReaderPage,
    containerHeight: Dp,
    onOpenImage: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val containerHeightPx = with(density) { containerHeight.toPx() }.coerceAtLeast(1f)
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
    val lastLineHeightPx = textLines.lastOrNull()?.heightPx?.toFloat() ?: 0f
    val surplus = containerHeightPx - baseTotalPx
    val extraPerGapPx = bottomJustifyGapPx(surplus, lastLineHeightPx, textLines.size - 1)
    Column(modifier = modifier) {
        var textLineIndex = 0
        page.elements.forEach { el ->
            if (el is PageElement.TextLine) {
                if (textLineIndex > 0 && extraPerGapPx > 0f) {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(with(density) { extraPerGapPx.toDp() }),
                    )
                }
                textLineIndex++
            }
            PageElementView(el, density, onOpenImage)
        }
    }
}

internal val SPREAD_SEAM_WIDTH = 12.dp

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
    if (columns <= 1) {
        if (left != null) {
            RenderReaderPage(
                left,
                containerHeight,
                onOpenImage,
                modifier
                    .fillMaxSize()
                    .pageInsets(contentTopInset),
            )
        }
        return
    }
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
                            .pageInsets(contentTopInset),
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
                            .pageInsets(contentTopInset),
                    )
                }
            }
        }
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
 * 将行富余宽度分配到空格词距（有空格）或字符间距（无空格，末字不加）。
 * SpanStyle.letterSpacing 会覆盖行样式基础字距，故须在基础字距上叠加增量。
 */
internal fun justifyLine(line: PageElement.TextLine, density: Density): AnnotatedString {
    val text = line.text
    val fontSizePx = with(density) { line.style.fontSize.toPx() }.coerceAtLeast(1f)
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
                .clickable(onClickLabel = caption) { onOpenImage(url) },
            contentScale = ContentScale.Fit,
            // 启用加载进度与断图反馈，避免正文占位块无法区分加载中与失败
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
