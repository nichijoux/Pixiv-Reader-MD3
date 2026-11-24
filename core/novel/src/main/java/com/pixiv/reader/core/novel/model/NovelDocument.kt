package com.pixiv.reader.core.novel.model

/**
 * fullText 把文本块按顺序以 `\n` 连接：阅读进度（字符级）以其偏移表示，官方 marker 按比例换算。
 */
data class NovelDocument(
    val blocks: List<NovelBlock>,
    val fullText: String,
    val textLength: Int = fullText.length,
) {
    companion object {
        val EMPTY = NovelDocument(blocks = emptyList(), fullText = "")
    }
}

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

internal fun buildFullText(blocks: List<NovelBlock>): String =
    blocks.filter { it.isTextBlock }.joinToString("\n") { block ->
        when (block) {
            is NovelBlock.Paragraph -> block.text
            is NovelBlock.Heading -> block.text
            is NovelBlock.Quote -> block.text
            else -> ""
        }
    }

fun NovelDocument.blockContaining(charOffset: Int): NovelBlock? {
    if (charOffset < 0 || textLength == 0) return null
    return blocks.firstOrNull { block ->
        block.isTextBlock && charOffset >= block.startChar && charOffset < block.endChar
    }
}

fun NovelDocument.percentageAt(charOffset: Int): Int {
    if (textLength <= 0) return 0
    return (charOffset.coerceIn(0, textLength) * 100 / textLength).coerceIn(0, 100)
}
