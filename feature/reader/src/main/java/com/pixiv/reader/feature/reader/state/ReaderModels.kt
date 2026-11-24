package com.pixiv.reader.feature.reader.state

import androidx.compose.ui.text.TextStyle

/**
 * [novelId] = -1 表示当前小说（页内按 [charOffset] 跳转）；
 * 否则为系列内目标小说（点击打开该本阅读器，[charOffset] 恒为 0）。
 */
data class ReaderTocItem(
    val title: String,
    val novelId: Long = -1,
    val charOffset: Int = 0,
)

sealed class PageElement {
    abstract val startChar: Int
    abstract val endChar: Int

    data class TextLine(
        val text: String,
        val style: TextStyle,
        override val startChar: Int,
        override val endChar: Int,
        val heightPx: Int,
        /** 两端对齐富余宽度（px，0 = 不拉伸）：段落中间行留给渲染期按词距/字距补足，末行与标题恒为 0。 */
        val justifyExtraPx: Float = 0f,
    ) : PageElement()

    data class Gap(
        val heightPx: Int,
    ) : PageElement() {
        override val startChar: Int = 0
        override val endChar: Int = 0
    }

    data class Image(
        val url: String,
        val caption: String?,
        val heightPx: Int,
    ) : PageElement() {
        override val startChar: Int = 0
        override val endChar: Int = 0
    }
}

/** 具备半开全文字符区间 [startChar, endChar) 的分页单元（负值视为无效），供 [indexForChar] 统一查找。 */
internal interface CharAnchored {
    val startChar: Int
    val endChar: Int
}

data class ReaderPage(
    override val startChar: Int,
    override val endChar: Int,
    val elements: List<PageElement>,
) : CharAnchored

internal data class MeasuredLine(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val heightPx: Int,
    val justifyExtraPx: Float = 0f,
)

/**
 * 翻页/仿真模式的翻页单元：[columns] = 1 时 [left] 为整宽页且 [right] 恒为 null；
 * [columns] = 2 时两两配对，末页落单则 [right] 为 null（右半留白）。
 */
data class ReaderSpread(
    val left: ReaderPage?,
    val right: ReaderPage?,
    val columns: Int,
) : CharAnchored {
    override val startChar: Int
        get() = left?.startChar ?: right?.startChar ?: 0

    override val endChar: Int
        get() = right?.endChar ?: left?.endChar ?: 0
}

fun buildSpreads(pages: List<ReaderPage>, columns: Int): List<ReaderSpread> {
    if (pages.isEmpty()) return emptyList()
    return if (columns <= 1) {
        pages.map { ReaderSpread(left = it, right = null, columns = 1) }
    } else {
        pages.chunked(2).map { pair ->
            ReaderSpread(
                left = pair.getOrNull(0),
                right = pair.getOrNull(1),
                columns = 2,
            )
        }
    }
}

enum class ReaderTapZone { LEFT, CENTER, RIGHT }

/** 触控九宫格分区判定（翻页与仿真手势管线共用）。 */
fun readerTapZone(x: Float, y: Float, width: Float, height: Float): ReaderTapZone =
    if (x >= width / 3f && x <= 2f * width / 3f && y >= height / 3f && y <= 2f * height / 3f) {
        ReaderTapZone.CENTER
    } else if (x < width / 2f) {
        ReaderTapZone.LEFT
    } else {
        ReaderTapZone.RIGHT
    }
