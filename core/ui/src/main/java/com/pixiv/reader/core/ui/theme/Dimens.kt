package com.pixiv.reader.core.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 全局间距刻度（Material spacing 风格）。
 *
 * 新增 UI 一律引用本刻度；存量代码迁移见 agent.md Magic Number 轮次。
 */
object Spacing {
    /** 4dp：元素内微间距 */
    val xs = 4.dp

    /** 8dp：紧凑间距 / 图标与文字间隙 */
    val sm = 8.dp

    /** 12dp：默认元素间距 */
    val md = 12.dp

    /** 16dp：卡片内边距 / 页面级间距 */
    val lg = 16.dp

    /** 24dp：区块级大间距 */
    val xl = 24.dp

    /** 页面统一外边距 */
    val pagePadding = lg
}
