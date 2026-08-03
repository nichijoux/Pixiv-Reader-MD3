package com.pixiv.reader.feature.reader

import androidx.compose.ui.graphics.Color

/**
 * 阅读器主题配色（4 套）：
 * 0 日间 / 1 纸张 / 2 夜间 / 3 深黑
 */
data class ReaderThemeColors(
    val background: Color,
    val text: Color,
    val secondary: Color,
    val divider: Color,
    val topBar: Color,
)

fun readerThemeColors(theme: Int): ReaderThemeColors = when (theme) {
    0 -> ReaderThemeColors(
        background = Color(0xFFFFFFFF),
        text = Color(0xFF1A1A1A),
        secondary = Color(0xFF8A8A8A),
        divider = Color(0xFFE5E5E5),
        topBar = Color(0xFFFAFAFA),
    )
    1 -> ReaderThemeColors(
        background = Color(0xFFF5EFE0),
        text = Color(0xFF3A3126),
        secondary = Color(0xFF8A7A60),
        divider = Color(0xFFE2D9C4),
        topBar = Color(0xFFEDE4CF),
    )
    2 -> ReaderThemeColors(
        background = Color(0xFF212121),
        text = Color(0xFFCFCFCF),
        secondary = Color(0xFF8A8A8A),
        divider = Color(0xFF3A3A3A),
        topBar = Color(0xFF1C1C1C),
    )
    else -> ReaderThemeColors(
        background = Color(0xFF000000),
        text = Color(0xFF9E9E9E),
        secondary = Color(0xFF555555),
        divider = Color(0xFF202020),
        topBar = Color(0xFF000000),
    )
}

val READER_THEME_NAMES = listOf("日间", "纸张", "夜间", "深黑")
val READER_PAGE_MODE_NAMES = listOf("滑动", "翻页", "仿真")
val READER_FONT_FAMILY_NAMES = listOf("衬线", "无衬线", "等宽", "自定义")
val READER_FONT_FAMILY_KEYS = listOf("serif", "sans", "mono", "custom")
