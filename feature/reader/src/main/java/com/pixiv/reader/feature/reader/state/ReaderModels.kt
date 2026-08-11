package com.pixiv.reader.feature.reader.state

import androidx.compose.ui.text.TextStyle

/**
 * 目录项。
 * [novelId] = -1 表示当前小说（页内按 [charOffset] 跳转）；
 * 否则为系列内目标小说（点击打开该本阅读器，[charOffset] 恒为 0）。
 */
data class ReaderTocItem(
    val title: String,
    val novelId: Long = -1,
    val charOffset: Int = 0,
)

/** 页内元素：一行文本 / 段距空隙 / 一张图片。 */
sealed class PageElement {
    /** 该元素对应的全文字符区间（空隙/图片恒为 0,0）。 */
    abstract val startChar: Int
    abstract val endChar: Int

    data class TextLine(
        val text: String,
        val style: TextStyle,
        override val startChar: Int,
        override val endChar: Int,
        /** 渲染行高（px） */
        val heightPx: Int,
        /**
         * 两端对齐富余宽度（px，0 = 不拉伸）：
         * 段落中间行按 legado textFullJustify 语义把「内容宽 - 行宽」记到行上，
         * 渲染期用词距/字距拉伸补足（末行/标题/分隔线恒为 0）。
         */
        val justifyExtraPx: Float = 0f,
    ) : PageElement()

    /** 段距空隙（legado paragraphSpacing：段落之后的显式间距，不参与两端对齐拉伸）。 */
    data class Gap(
        /** 空隙高度（px） */
        val heightPx: Int,
    ) : PageElement() {
        override val startChar: Int = 0
        override val endChar: Int = 0
    }

    data class Image(
        val url: String,
        val caption: String?,
        /** 渲染高度（px），已按页高自适应，保证同页可容纳文字 */
        val heightPx: Int,
    ) : PageElement() {
        override val startChar: Int = 0
        override val endChar: Int = 0
    }
}

/**
 * 分页结果：一页由有序的文本行与图片组成。
 * 图片不再独占整页，而是按顺序插入文本流（高度自适应），一页可同时显示图片与文字。
 */
data class ReaderPage(
    val startChar: Int,
    val endChar: Int,
    val elements: List<PageElement>,
)

/** 一页里的一行（保留全局字符区间用于进度映射）。 */
internal data class MeasuredLine(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val heightPx: Int,
    /** 两端对齐富余宽度（px），见 [PageElement.TextLine.justifyExtraPx]。 */
    val justifyExtraPx: Float = 0f,
)
