package com.pixiv.reader.core.common

import java.util.Locale

/**
 * 计数格式化（Pixiv 展示习惯）：
 * - 万：12400 → "1.2万"
 * - 亿：123456789 → "1.2亿"
 * - 不足万：999 → "999"
 * 保留一位小数，末尾 0 省略（1.0万 → "1万"）。
 */
fun formatCount(count: Long): String {
    if (count < 10_000) return count.toString()
    val yi = 100_000_000L
    if (count >= yi) {
        return formatUnit(count, yi, "亿")
    }
    return formatUnit(count, 10_000L, "万")
}

private fun formatUnit(count: Long, unit: Long, suffix: String): String {
    val value = count.toDouble() / unit
    val text = String.format(Locale.US, "%.1f", value)
    val trimmed = text.removeSuffix(".0")
    return "$trimmed$suffix"
}

/** 字数格式化：小于 1 万显示原始数字，否则显示万（用于小说列表/详情）。 */
fun formatCountForNovel(count: Int): String = when {
    count >= 10000 -> String.format(Locale.US, "%.1f万", count / 10000f)
    else -> count.toString()
}
