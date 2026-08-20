package com.pixiv.reader.feature.reader.ui

import androidx.compose.ui.graphics.Color
import com.pixiv.reader.core.common.config.ReaderThemeMode
import com.pixiv.reader.core.ui.theme.ReaderDayBackground
import com.pixiv.reader.core.ui.theme.ReaderDayDivider
import com.pixiv.reader.core.ui.theme.ReaderDaySecondary
import com.pixiv.reader.core.ui.theme.ReaderDayText
import com.pixiv.reader.core.ui.theme.ReaderDayTopBar
import com.pixiv.reader.core.ui.theme.ReaderDeepBlackBackground
import com.pixiv.reader.core.ui.theme.ReaderDeepBlackDivider
import com.pixiv.reader.core.ui.theme.ReaderDeepBlackSecondary
import com.pixiv.reader.core.ui.theme.ReaderDeepBlackText
import com.pixiv.reader.core.ui.theme.ReaderDeepBlackTopBar
import com.pixiv.reader.core.ui.theme.ReaderNightBackground
import com.pixiv.reader.core.ui.theme.ReaderNightDivider
import com.pixiv.reader.core.ui.theme.ReaderNightSecondary
import com.pixiv.reader.core.ui.theme.ReaderNightText
import com.pixiv.reader.core.ui.theme.ReaderNightTopBar
import com.pixiv.reader.core.ui.theme.ReaderPaperBackground
import com.pixiv.reader.core.ui.theme.ReaderPaperDivider
import com.pixiv.reader.core.ui.theme.ReaderPaperSecondary
import com.pixiv.reader.core.ui.theme.ReaderPaperText
import com.pixiv.reader.core.ui.theme.ReaderPaperTopBar
import com.pixiv.reader.feature.reader.R

/**
 * 阅读器主题配色（4 套）：
 * [ReaderThemeMode.DAY] / [ReaderThemeMode.PAPER] / [ReaderThemeMode.NIGHT] / [ReaderThemeMode.DEEP_BLACK]
 * 色值统一来自 core:ui/theme/Color.kt（禁止在此写裸色值）。
 */
data class ReaderThemeColors(
    val background: Color,
    val text: Color,
    val secondary: Color,
    val divider: Color,
    val topBar: Color,
)

fun readerThemeColors(theme: ReaderThemeMode): ReaderThemeColors = when (theme) {
    ReaderThemeMode.DAY -> ReaderThemeColors(
        background = ReaderDayBackground,
        text = ReaderDayText,
        secondary = ReaderDaySecondary,
        divider = ReaderDayDivider,
        topBar = ReaderDayTopBar,
    )
    ReaderThemeMode.PAPER -> ReaderThemeColors(
        background = ReaderPaperBackground,
        text = ReaderPaperText,
        secondary = ReaderPaperSecondary,
        divider = ReaderPaperDivider,
        topBar = ReaderPaperTopBar,
    )
    ReaderThemeMode.NIGHT -> ReaderThemeColors(
        background = ReaderNightBackground,
        text = ReaderNightText,
        secondary = ReaderNightSecondary,
        divider = ReaderNightDivider,
        topBar = ReaderNightTopBar,
    )
    ReaderThemeMode.DEEP_BLACK -> ReaderThemeColors(
        background = ReaderDeepBlackBackground,
        text = ReaderDeepBlackText,
        secondary = ReaderDeepBlackSecondary,
        divider = ReaderDeepBlackDivider,
        topBar = ReaderDeepBlackTopBar,
    )
}

val READER_THEME_NAME_RES = intArrayOf(
    R.string.reader_theme_day,
    R.string.reader_theme_paper,
    R.string.reader_theme_night,
    R.string.reader_theme_deep_black,
)
val READER_PAGE_MODE_NAME_RES = intArrayOf(
    R.string.reader_page_mode_scroll,
    R.string.reader_page_mode_paginate,
    R.string.reader_page_mode_simulation,
)
val READER_FONT_FAMILY_NAME_RES = intArrayOf(
    R.string.reader_font_family_serif,
    R.string.reader_font_family_sans,
    R.string.reader_font_family_mono,
    R.string.reader_font_family_custom,
)
val READER_FONT_FAMILY_KEYS = listOf("serif", "sans", "mono", "custom")
