package com.pixiv.reader.feature.novel.data

import com.pixiv.reader.core.novel.model.NovelBlock
import com.pixiv.reader.core.novel.model.NovelDocument
import com.pixiv.reader.core.novel.parser.NovelParser

private val TITLE_PATTERN = Regex(
    """^(序章|第[\d零一二三四五六七八九十百千万]+[章卷]|终章|简章|后记|完本感言|上架感言|番外)(.*)$"""
)

/** 段末不在本集合内时视为硬换行，与下一行合并。 */
private val PARAGRAPH_ENDS = """’－。.!!?？"”～~）)…】」—』=-·．※》""".toSet()

/** 按系列连续重排卷/章号（忽略原标题编号）、合并硬换行并做标点与简繁规范化；[toSimplified] 默认透传以便 JVM 单测。 */
internal fun formatChapters(
    chapters: List<Pair<com.pixiv.api.model.Novel, NovelDocument>>,
    toSimplified: (String) -> String = { it },
): List<Pair<com.pixiv.api.model.Novel, NovelDocument>> {
    var volumeNum = 1
    var chapterNum = 1
    return chapters.map { (novel, document) ->
        val (formatted, newVol, newCh) = formatNovelDocument(
            document,
            volumeNum,
            chapterNum,
            toSimplified
        )
        volumeNum = newVol
        chapterNum = newCh
        novel to formatted
    }
}

private fun formatNovelDocument(
    document: NovelDocument,
    volumeNum: Int,
    chapterNum: Int,
    toSimplified: (String) -> String,
): Triple<NovelDocument, Int, Int> {
    var vol = volumeNum
    var ch = chapterNum
    val newBlocks = mutableListOf<NovelBlock>()
    var line = ""

    fun flush() {
        if (line.isNotBlank()) {
            newBlocks.add(
                NovelBlock.Paragraph(
                    NovelParser.PARAGRAPH_INDENT + formatText(
                        line,
                        toSimplified
                    )
                )
            )
            line = ""
        }
    }

    fun renumberTitle(prefix: String, rest: String): String? = when {
        prefix.endsWith("卷") -> {
            val t = "第${vol}卷 $rest".trim()
            vol++
            ch = 1
            t
        }

        else -> {
            val t = "第${ch}章 $rest".trim()
            ch++
            t
        }
    }

    for (block in document.blocks) {
        when (block) {
            is NovelBlock.Image -> {
                flush()
                newBlocks.add(block)
            }

            is NovelBlock.Separator -> {
                flush()
                newBlocks.add(block)
            }

            is NovelBlock.Quote -> {
                flush()
                newBlocks.add(NovelBlock.Quote(formatText(block.text, toSimplified)))
            }

            is NovelBlock.Heading -> {
                flush()
                val text = block.text.trim()
                val m = TITLE_PATTERN.matchEntire(text)
                if (m != null) {
                    newBlocks.add(
                        NovelBlock.Heading(
                            formatText(
                                renumberTitle(m.groupValues[1], m.groupValues[2].trim()) ?: text,
                                toSimplified
                            ),
                            block.level
                        )
                    )
                } else {
                    newBlocks.add(NovelBlock.Heading(formatText(text, toSimplified), block.level))
                }
            }

            is NovelBlock.Paragraph -> {
                val text = stripIndent(block.text).trim()
                val m = TITLE_PATTERN.matchEntire(text)
                if (m != null) {
                    flush()
                    newBlocks.add(
                        NovelBlock.Heading(
                            formatText(
                                renumberTitle(m.groupValues[1], m.groupValues[2].trim()) ?: text,
                                toSimplified
                            ),
                            2
                        )
                    )
                } else if (text.isNotBlank()) {
                    line += text
                    if (line.lastOrNull() in PARAGRAPH_ENDS) flush()
                }
                // 空段落不输出且不中断跨行合并
            }
        }
    }
    flush()

    val fullText = newBlocks.filter { it.isTextBlock }.joinToString("\n") { block ->
        when (block) {
            is NovelBlock.Paragraph -> block.text
            is NovelBlock.Heading -> block.text
            is NovelBlock.Quote -> block.text
            else -> ""
        }
    }
    return Triple(
        NovelDocument(
            blocks = newBlocks,
            fullText = fullText,
            textLength = fullText.length
        ), vol, ch
    )
}

internal fun stripIndent(text: String): String = text.removePrefix(NovelParser.PARAGRAPH_INDENT)

private fun formatText(s: String, toSimplified: (String) -> String): String {
    var t = toSimplified(s)
        .replace(",", "，")
        .replace(":", "：")
        .replace("(", "（")
        .replace(")", "）")
        .replace("!", "！")
        .replace("：　　", "：")
        .replace("：  ", "：")
        .replace("♥", "❤️")
        .replace("♡", "❤️")
        .replace("~", "～")
        .replace("屁穴", "菊穴")
        .replace("后穴", "菊穴")
        .replace("前穴", "蜜穴")
        .replace("?", "？")
        .trim()
    t = t
        .replace(Regex("""\.{2,}"""), "……")
        .replace(Regex("""·{2,}"""), "……")
        .replace(Regex("""。{3,}"""), "……")
    return t.replace("……。", "……").replace("\uFE0F", "")
}
