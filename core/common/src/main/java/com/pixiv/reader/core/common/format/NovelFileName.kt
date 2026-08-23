package com.pixiv.reader.core.common.format

/**
 * 小说导出/下载文件名的清洗与模板渲染。
 *
 * 供 feature:novel（导出命名）与 feature:user（「我的」页模板设置与预览）共用。
 */

/** 文件名清洗：替换文件系统非法字符（纯函数，可测）。 */
fun sanitizeFileName(name: String): String =
    name.replace(Regex("""[\\/:*?"<>|\r\n]"""), "_")
        .trim()
        .ifBlank { "novel" }
        .take(80)

/**
 * 按模板渲染小说导出文件名（不含扩展名）。
 *
 * - `{title}` 本作标题
 * - `{author}` 作者名（缺失替换为空串）
 * - `{id}` 作品 ID
 * - `{series}` 系列标题；无系列为空串（不回退其它字段）
 * - `{date}` 作品发布日期（ISO 日期前 10 位 `yyyy-MM-dd`；缺失为空串）
 * - `{favcount}` 收藏数（缺失为空串）
 *
 * 渲染后整体清洗非法字符；结果为空时回退 [fallbackTemplate]（调用方按下载范围传
 * 单本/系列默认模板），避免导出无名文件。
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
