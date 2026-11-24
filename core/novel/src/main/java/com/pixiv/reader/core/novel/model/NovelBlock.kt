package com.pixiv.reader.core.novel.model

/**
 * 小说正文结构化块。[startChar] 含、[endChar] 不含；图片与分隔线不参与全文文本，区间恒为 (0, 0)。
 */
sealed class NovelBlock {
    abstract val startChar: Int
    abstract val endChar: Int

    /** text 已带全角缩进（两个全角空格） */
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

    data class Quote(
        val text: String,
        override val startChar: Int = 0,
        override val endChar: Int = 0,
    ) : NovelBlock()

    data class Image(
        val url: String,
        val caption: String? = null,
        override val startChar: Int = 0,
        override val endChar: Int = 0,
    ) : NovelBlock()

    data class Separator(
        val symbol: String = "——————",
        override val startChar: Int = 0,
        override val endChar: Int = 0,
    ) : NovelBlock()

    val isTextBlock: Boolean
        get() = this is Paragraph || this is Heading || this is Quote
}
