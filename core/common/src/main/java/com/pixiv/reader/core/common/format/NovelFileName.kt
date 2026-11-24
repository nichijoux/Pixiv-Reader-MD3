package com.pixiv.reader.core.common.format

fun sanitizeFileName(name: String): String =
    name.replace(Regex("""[\\/:*?"<>|\r\n]"""), "_")
        .trim()
        .ifBlank { "novel" }
        .take(80)

/**
 * 按模板渲染小说导出文件名（不含扩展名），缺失字段替换为空串（`{series}` 不回退其它字段），
 * `{date}` 取 ISO 前 10 位；渲染为空时回退 [fallbackTemplate] 后再做非法字符清洗。
 */
fun renderNovelFileName(
    template: String,
    title: String,
    author: String?,
    id: Long,
    seriesTitle: String?,
    publishDate: String? = null,
    favoriteCount: Int? = null,
    fallbackTemplate: String = NovelFileNameTemplate.DEFAULT_SINGLE,
): String {
    val series = seriesTitle.orEmpty()
    fun render(tpl: String): String = tpl
        .replace(NovelFileNameTemplate.TITLE, title)
        .replace(NovelFileNameTemplate.AUTHOR, author.orEmpty())
        .replace(NovelFileNameTemplate.ID, id.toString())
        .replace(NovelFileNameTemplate.SERIES, series)
        .replace(NovelFileNameTemplate.DATE, publishDate?.take(10).orEmpty())
        .replace(NovelFileNameTemplate.FAV_COUNT, favoriteCount?.toString().orEmpty())

    val rendered = render(template)
    return if (rendered.isBlank()) {
        sanitizeFileName(render(fallbackTemplate))
    } else {
        sanitizeFileName(rendered)
    }
}
