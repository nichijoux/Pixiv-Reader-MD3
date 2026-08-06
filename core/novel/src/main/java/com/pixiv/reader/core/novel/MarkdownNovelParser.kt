package com.pixiv.reader.core.novel

/**
 * Markdown 小说解析：`#`/`##` 标题、`>` 引用、`---` 分隔线、普通段落 → [NovelDocument]。
 * 用于下载管理页点击 .md 导出文件时应用内直接阅读。
 */
object MarkdownNovelParser {

    private val HEADING_RE = Regex("^(#{1,6})\\s+(.*)$")
    private val QUOTE_RE = Regex("^>\\s?(.*)$")
    private val HR_RE = Regex("^(?:-{3,}|\\*{3,}|_{3,})\\s*$")

    fun parse(text: String): NovelDocument {
        val raw = text.split('\n')
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                HEADING_RE.matchEntire(line)?.let { m ->
                    val level = m.groupValues[1].length.coerceIn(1, 6)
                    val title = m.groupValues[2].trim()
                    if (title.isBlank()) null else NovelBlock.Heading(title, level)
                } ?: QUOTE_RE.matchEntire(line)?.let { m ->
                    val quote = m.groupValues[1].trim()
                    if (quote.isBlank()) null else NovelBlock.Quote(quote)
                } ?: if (HR_RE.matches(line)) {
                    NovelBlock.Separator()
                } else {
                    NovelBlock.Paragraph(line.trim())
                }
            }
        val blocks = assignCharRanges(raw)
        val fullText = buildFullText(blocks)
        return NovelDocument(blocks = blocks, fullText = fullText, textLength = fullText.length)
    }
}
