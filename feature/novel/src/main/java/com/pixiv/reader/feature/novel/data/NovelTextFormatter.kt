package com.pixiv.reader.feature.novel.data

import com.pixiv.reader.core.novel.model.NovelBlock
import com.pixiv.reader.core.novel.model.NovelDocument
import com.pixiv.reader.core.novel.parser.NovelParser

/**
 * 标题/分卷正则（对齐 `format_novel`）：组 1 为前缀（序章/第N章/第N卷/终章/后记/番外 等），组 2 为剩余标题。
 */
private val TITLE_PATTERN = Regex(
    """^(序章|第[\d零一二三四五六七八九十百千万]+[章卷]|终章|简章|后记|完本感言|上架感言|番外)(.*)$"""
)

/**
 * 句末标点：段落末字符属于此集合即认为段落已结束，否则与下一行合并（整理硬换行）。
 */
private val PARAGRAPH_ENDS = """’－。.!!?？"”～~）)…】」—』=-·．※》""".toSet()

/**
 * 导出前统一格式化系列章节（对齐 Python `format_novel`）：
 * - 卷/章标题按顺序重排：前缀以「卷」结尾 → `第N卷 标题`（章号重置 1）；其余 → `第N章 标题`（章号递增）；
 *   忽略原标题编号（照搬重排，序章/后记也会并入章节序号）。
 * - 非标题段落按句末标点合并硬换行（末字符不在 [PARAGRAPH_ENDS] 则与后续行拼接）。
 * - 简繁转换（[toSimplified]，默认不转换）+ 全角标点规范化与专属词替换（[formatText]）。
 * - Quote 不参与合并仅套用规范化；Image / Separator 原样保留。
 *
 * 卷/章号跨整个系列连续（单本从 1/1 起）。
 *
 * @param toSimplified 繁→简转换器（OpenCC；默认原样返回，便于 JVM 单测）
 */
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

/** 单章文档格式化：返回（格式化后文档, 新卷号, 新章号）。 */
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

    // 把已合并的未结束段落输出为一段（前缀应用惯例的全角缩进）
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

    // 标题重排（卷/章），返回重排后的标题；非标题返回 null
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
                    // 正文中出现的标题/分卷行 → 重排为标题
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
                    // 普通段落：按句末标点合并硬换行
                    line += text
                    if (line.lastOrNull() in PARAGRAPH_ENDS) flush()
                }
                // 空段落不输出（与 format_novel `elif paragraph != ""` 一致，也不中断合并）
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

/** 去除段落首部应用自带的全角缩进（HTML 解析器添加的 `\u3000\u3000`）。 */
internal fun stripIndent(text: String): String = text.removePrefix(NovelParser.PARAGRAPH_INDENT)

/**
 * 文本规范化（对齐 `format_novel` 后半段）：
 * 先 [toSimplified] 繁→简（OpenCC），再半角标点转全角、冒号后多余空格、爱心符号、
 * 专属词替换、连续点/圆点/句号转省略号、去变体选择符。
 */
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
