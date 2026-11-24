package com.pixiv.reader.core.common.ui

enum class WindowSizeClass {
    Compact,
    Medium,
    Expanded,
}

fun classifyWindowWidth(widthDp: Int): WindowSizeClass = when {
    widthDp < 600 -> WindowSizeClass.Compact
    widthDp < 840 -> WindowSizeClass.Medium
    else -> WindowSizeClass.Expanded
}

fun WindowSizeClass.useRail(): Boolean = this != WindowSizeClass.Compact

const val MAX_CONTENT_WIDTH_DP = 760
