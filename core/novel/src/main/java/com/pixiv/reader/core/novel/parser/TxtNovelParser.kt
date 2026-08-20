package com.pixiv.reader.core.novel.parser

import com.pixiv.reader.core.novel.model.NovelBlock
import com.pixiv.reader.core.novel.model.NovelDocument
import com.pixiv.reader.core.novel.model.assignCharRanges
import com.pixiv.reader.core.novel.model.buildFullText

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
