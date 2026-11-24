package com.pixiv.reader.core.common.format

object NovelFileNameTemplate {
    const val TITLE = "{title}"
    const val AUTHOR = "{author}"
    const val ID = "{id}"
    const val SERIES = "{series}"
    const val DATE = "{date}"
    const val FAV_COUNT = "{favcount}"

    const val DEFAULT_SINGLE = "{title}_{id}"

    const val DEFAULT_SERIES = "{series}_{id}"

    /** 顺序即设置页插入按钮的展示顺序。 */
    val ALL = listOf(TITLE, AUTHOR, ID, SERIES, DATE, FAV_COUNT)
}
