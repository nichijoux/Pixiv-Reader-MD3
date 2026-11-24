package com.pixiv.reader.feature.reader.state

internal fun <T : CharAnchored> List<T>.indexForChar(offset: Int): Int {
    if (isEmpty()) return 0
    val index = indexOfFirst { page ->
        page.startChar >= 0 && page.endChar >= 0 &&
                offset >= page.startChar && offset < page.endChar
    }
    if (index >= 0) return index
    val lastLe = lastOrNull { page -> page.startChar in 0..offset }
        ?: first()
    return indexOf(lastLe)
}

fun List<ReaderPage>.pageIndexForChar(charOffset: Int): Int = indexForChar(charOffset)

fun List<ReaderSpread>.spreadIndexForChar(charOffset: Int): Int = indexForChar(charOffset)

fun estimateOfficialPage(charOffset: Int, textLength: Int, officialPageCount: Int): Int {
    if (officialPageCount <= 0) return 1
    val ratio = charOffset.coerceIn(0, textLength).toFloat() / textLength.coerceAtLeast(1)
    return (ratio * officialPageCount).toInt().coerceIn(1, officialPageCount)
}

fun estimateCharFromOfficialPage(page: Int, textLength: Int, officialPageCount: Int): Int {
    if (textLength <= 0) return 0
    val count = officialPageCount.coerceAtLeast(1)
    val ratio = (page - 1).coerceAtLeast(0).toFloat() / count
    return (textLength * ratio).toInt().coerceIn(0, textLength)
}
