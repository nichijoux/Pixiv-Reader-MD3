package com.pixiv.reader.core.common.format

import java.util.Locale

/**
 * locale-aware 计数紧凑格式化（zh 用万/亿，其余用 K/M/B，去尾零 ".0"）。
 * 纯函数，默认读 [Locale.getDefault]（由 MainActivity.attachBaseContext 随应用内语言同步设置）。
 */
fun formatCount(count: Long, locale: Locale = Locale.getDefault()): String {
    if (count < 0) return count.toString()
    for (u in compactUnits(locale)) {
        if (count >= u.threshold) {
            val value = count.toDouble() / u.threshold
            val text = String.format(locale, "%.1f", value)
            val trimmed = text.removeSuffix(".0")
            return "$trimmed${u.suffix}"
        }
    }
    return count.toString()
}

fun formatCountForNovel(count: Int, locale: Locale = Locale.getDefault()): String =
    formatCount(count.toLong(), locale)

/** 固定 US 单位（语言中性），不随 locale 变化；非正输入返回空串（无大小不展示）。 */
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val kb = bytes / 1024.0
    return when {
        kb >= 1024 * 1024 -> String.format(Locale.US, "%.1f GB", kb / (1024 * 1024))
        kb >= 1024 -> String.format(Locale.US, "%.1f MB", kb / 1024)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}

private data class CompactUnit(val threshold: Long, val suffix: String)

private fun compactUnits(locale: Locale): List<CompactUnit> =
    if (locale.language.equals("zh", ignoreCase = true)) {
        listOf(CompactUnit(100_000_000L, "亿"), CompactUnit(10_000L, "万"))
    } else {
        listOf(
            CompactUnit(1_000_000_000L, "B"),
            CompactUnit(1_000_000L, "M"),
            CompactUnit(1_000L, "K")
        )
    }