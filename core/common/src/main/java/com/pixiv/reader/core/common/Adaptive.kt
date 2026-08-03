package com.pixiv.reader.core.common

/**
 * Material 3 窗口尺寸类（用于平板/多窗口自适应布局）。
 */
enum class WindowSizeClass {
    Compact,
    Medium,
    Expanded,
}

/**
 * 按窗口宽度（dp）分类，对齐官方 WindowSizeClass 阈值：
 * - Compact：< 600dp（手机竖屏）
 * - Medium：600 ~ 839dp（大屏手机 / 竖屏平板）
 * - Expanded：>= 840dp（横屏平板 / 桌面）
 */
fun classifyWindowWidth(widthDp: Int): WindowSizeClass = when {
    widthDp < 600 -> WindowSizeClass.Compact
    widthDp < 840 -> WindowSizeClass.Medium
    else -> WindowSizeClass.Expanded
}

/**
 * 是否应使用侧边导航（平板）。
 * Medium 及以上使用 NavigationRail，Compact 使用底部 NavigationBar。
 */
fun WindowSizeClass.useRail(): Boolean = this != WindowSizeClass.Compact

/** 内容/阅读最大宽度（平板防止文字过长）：约 46rem */
const val MAX_CONTENT_WIDTH_DP = 760
