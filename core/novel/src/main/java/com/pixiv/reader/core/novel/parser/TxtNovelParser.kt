package com.pixiv.reader.core.novel.parser

import com.pixiv.reader.core.novel.model.NovelBlock
import com.pixiv.reader.core.novel.model.NovelDocument
import com.pixiv.reader.core.novel.model.assignCharRanges
import com.pixiv.reader.core.novel.model.buildFullText

/** 下载管理页导出文件的应用内直接阅读入口。 */
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
