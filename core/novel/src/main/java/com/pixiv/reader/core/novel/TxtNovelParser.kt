package com.pixiv.reader.core.novel

/**
 * TXT 小说解析：按换行分段 → [NovelDocument]（本地导出文件直接阅读用）。
 */
object TxtNovelParser {

    fun parse(text: String): NovelDocument {
        val raw = text.split('\n')
            .filter { it.isNotBlank() }
            .map { NovelBlock.Paragraph(it.trimEnd()) }
        val blocks = assignCharRanges(raw)
        val fullText = buildFullText(blocks)
        return NovelDocument(blocks = blocks, fullText = fullText, textLength = fullText.length)
    }
}
