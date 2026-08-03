package com.pixiv.reader.core.novel

/**
 * 解析后的小说文档。
 *
 * @property blocks 结构化块（已填充 [NovelBlock.startChar]/[endChar] 字符区间）
 * @property fullText 纯文本全文：文本块按顺序以 `\n` 连接。
 *   阅读进度（字符级）以该文本中的偏移表示，官方 marker 按比例换算。
 * @property textLength 纯文本长度（进度上限）
 */
data class NovelDocument(
    val blocks: List<NovelBlock>,
    val fullText: String,
    val textLength: Int = fullText.length,
) {
    companion object {
        /** 空文档 */
        val EMPTY = NovelDocument(blocks = emptyList(), fullText = "")
    }
}

/** 为文本块填充全文字符区间（图片/分隔线区间为 0,0）。 */
internal fun assignCharRanges(raw: List<NovelBlock>): List<NovelBlock> {
    var offset = 0
    return raw.map { block ->
        when (block) {
            is NovelBlock.Paragraph -> {
                val b = block.copy(startChar = offset, endChar = offset + block.text.length)
                offset += block.text.length + 1
                b
            }
            is NovelBlock.Heading -> {
                val b = block.copy(startChar = offset, endChar = offset + block.text.length)
                offset += block.text.length + 1
                b
            }
            is NovelBlock.Quote -> {
                val b = block.copy(startChar = offset, endChar = offset + block.text.length)
                offset += block.text.length + 1
                b
            }
            is NovelBlock.Image, is NovelBlock.Separator -> block
        }
    }
}

/** 由带区间的文本块构建纯文本全文。 */
internal fun buildFullText(blocks: List<NovelBlock>): String =
    blocks.filter { it.isTextBlock }.joinToString("\n") { block ->
        when (block) {
            is NovelBlock.Paragraph -> block.text
            is NovelBlock.Heading -> block.text
            is NovelBlock.Quote -> block.text
            else -> ""
        }
    }

/** 找到包含给定字符偏移的文本块（找不到返回 null）。 */
fun NovelDocument.blockContaining(charOffset: Int): NovelBlock? {
    if (charOffset < 0 || textLength == 0) return null
    return blocks.firstOrNull { block ->
        block.isTextBlock && charOffset >= block.startChar && charOffset < block.endChar
    }
}

/** 字符偏移 → 阅读百分比（0..100）。 */
fun NovelDocument.percentageAt(charOffset: Int): Int {
    if (textLength <= 0) return 0
    return (charOffset.coerceIn(0, textLength) * 100 / textLength).coerceIn(0, 100)
}
