package com.pixiv.reader.core.common.format

import java.util.Locale

/**
 * 计数紧凑格式化（按 [locale] 选单位）：
 * - zh：万（1e4）/ 亿（1e8），如 12400 → "1.2万"、123456789 → "1.2亿"
 * - 其它（en 等）：B（1e9）/ M（1e6）/ K（1e3），如 12400 → "12.4K"
 *
 * 不足最小单位阈值返回原始数字字符串；保留一位小数，末尾 ".0" 省略（1.0万 → "1万"）。
 * 纯函数，默认读 [Locale.getDefault]（由 MainActivity.attachBaseContext 设置）。
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

/**
 * 字数格式化：与 [formatCount] 同策略（阈值以下显示原始数字，以上按 locale 紧凑单位 + 去尾零）。
 * 适用于小说列表/详情的字数/收藏展示。
 */
fun formatCountForNovel(count: Int, locale: Locale = Locale.getDefault()): String =
    formatCount(count.toLong(), locale)

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