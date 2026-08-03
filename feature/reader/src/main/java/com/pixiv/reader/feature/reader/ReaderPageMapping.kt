package com.pixiv.reader.feature.reader

/**
 * 阅读器字符偏移 ↔ 页 / 官方页码换算（纯函数，可单测）。
 */

/** 找到包含给定字符偏移的页下标；找不到则就近返回（开头 → 0，结尾 → 最后一页）。 */
fun List<ReaderPage>.pageIndexForChar(charOffset: Int): Int {
    if (isEmpty()) return 0
    val index = indexOfFirst { page ->
        page.startChar >= 0 && page.endChar >= 0 &&
            charOffset >= page.startChar && charOffset < page.endChar
    }
    if (index >= 0) return index
    // 没有命中：取最后一个 startChar <= offset 的页
    val lastLe = lastOrNull { page -> page.startChar >= 0 && page.startChar <= charOffset }
        ?: first()
    return indexOf(lastLe)
}

/** 页首字符偏移（插图页返回 -1）。 */
fun ReaderPage.startCharOrNull(): Int? = if (startChar >= 0) startChar else null

/**
 * 字符偏移 → 官方 marker 页码（官方分页按比例换算，clamp 到 1..pageCount）。
 */
fun estimateOfficialPage(charOffset: Int, textLength: Int, officialPageCount: Int): Int {
    if (officialPageCount <= 0) return 1
    val ratio = charOffset.coerceIn(0, textLength).toFloat() / textLength.coerceAtLeast(1)
    return (ratio * officialPageCount).toInt().coerceIn(1, officialPageCount)
}

/**
 * 官方 marker 页码 → 字符偏移（用于无本地进度时从官方书签恢复）。
 */
fun estimateCharFromOfficialPage(page: Int, textLength: Int, officialPageCount: Int): Int {
    if (textLength <= 0) return 0
    val count = officialPageCount.coerceAtLeast(1)
    val ratio = (page - 1).coerceAtLeast(0).toFloat() / count
    return (textLength * ratio).toInt().coerceIn(0, textLength)
}
