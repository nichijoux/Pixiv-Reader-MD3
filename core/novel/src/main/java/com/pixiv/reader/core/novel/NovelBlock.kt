package com.pixiv.reader.core.novel

/**
 * 小说正文结构化块。
 *
 * [startChar] / [endChar] 为该块在 [NovelDocument.fullText] 中的字符区间
 * （[startChar] 含、[endChar] 不含）。图片与分隔线不参与全文文本，
 * 因此区间恒为 (0, 0)。
 */
sealed class NovelBlock {
    abstract val startChar: Int
    abstract val endChar: Int

    /** 正文段落。text 已带全角缩进（两个全角空格） */
    data class Paragraph(
        val text: String,
        override val startChar: Int = 0,
        override val endChar: Int = 0,
    ) : NovelBlock()

    /** 标题（h1~h6） */
    data class Heading(
        val text: String,
        val level: Int = 2,
        override val startChar: Int = 0,
        override val endChar: Int = 0,
    ) : NovelBlock()

    /** 引用块 */
    data class Quote(
        val text: String,
        override val startChar: Int = 0,
        override val endChar: Int = 0,
    ) : NovelBlock()

    /** 正文插图 */
    data class Image(
        val url: String,
        val caption: String? = null,
        override val startChar: Int = 0,
        override val endChar: Int = 0,
    ) : NovelBlock()

    /** 分隔线 */
    data class Separator(
        val symbol: String = "——————",
        override val startChar: Int = 0,
        override val endChar: Int = 0,
    ) : NovelBlock()

    /** 是否为参与阅读进度的文本块 */
    val isTextBlock: Boolean
        get() = this is Paragraph || this is Heading || this is Quote
}
