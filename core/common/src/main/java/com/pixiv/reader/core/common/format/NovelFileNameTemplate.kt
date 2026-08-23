package com.pixiv.reader.core.common.format

/**
 * 小说导出/下载文件名模板的占位符与默认值。
 *
 * 渲染实现为 [renderNovelFileName]；常量供 feature:novel（导出命名）与
 * feature:user（「我的」设置 UI）共用。
 *
 * 模板按下载范围分两套：单本下载与系列导出（整系列/部分分册）各自独立配置，
 * 默认值天然区分命名来源（{title} 本作标题 / {series} 系列名）。
 */
object NovelFileNameTemplate {
    const val TITLE = "{title}"
    const val AUTHOR = "{author}"
    const val ID = "{id}"
    const val SERIES = "{series}"
    const val DATE = "{date}"
    const val FAV_COUNT = "{favcount}"

    /** 单本下载默认模板。 */
    const val DEFAULT_SINGLE = "{title}_{id}"

    /** 系列导出（整系列/部分分册合并文件）默认模板。 */
    const val DEFAULT_SERIES = "{series}_{id}"

    /** 支持的全部占位符（顺序即设置页插入按钮的展示顺序）。 */
    val ALL = listOf(TITLE, AUTHOR, ID, SERIES, DATE, FAV_COUNT)
}
