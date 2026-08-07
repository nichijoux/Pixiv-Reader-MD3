package com.pixiv.reader.core.common

/**
 * 小说导出/下载文件名模板的占位符与默认值。
 *
 * 渲染实现位于 feature:novel 的 [NovelExportUtil.renderNovelFileName]；
 * 此处常量供 feature:novel（渲染）与 feature:user（「我的」设置 UI）共用。
 */
object NovelFileNameTemplate {
    const val TITLE = "{title}"
    const val AUTHOR = "{author}"
    const val ID = "{id}"
    const val SERIES = "{series}"
    const val DATE = "{date}"
    const val FAV_COUNT = "{favcount}"
    const val DEFAULT = "{series}_{id}"

    /** 支持的全部占位符（顺序即设置页插入按钮的展示顺序）。 */
    val ALL = listOf(TITLE, AUTHOR, ID, SERIES, DATE, FAV_COUNT)
}
