package com.pixiv.reader.core.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 全局间距刻度（2dp 步进）：基准档 xs=4 / sm=8 / md=12 / lg=16 / xl=24，
 * `*Plus` 为相邻基准档之间的半档。新增 UI 一律引用本刻度，不写裸数字。
 */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val xsPlus = 6.dp
    val sm = 8.dp
    val smPlus = 10.dp
    val md = 12.dp
    val mdPlus = 14.dp
    val lg = 16.dp
    val lgPlus = 20.dp
    val xl = 24.dp
}

/**
 * 通用元素尺寸（图标 / 头像 / 装饰），按数值命名避免语义误判；
 * 组件独有布局尺寸（封面、Banner、列宽等）留在组件内。
 */
object Sizes {
    val s16 = 16.dp
    val s18 = 18.dp
    val s20 = 20.dp
    val s22 = 22.dp
    val s24 = 24.dp
    val s28 = 28.dp
    val s32 = 32.dp
    val s36 = 36.dp
    val s40 = 40.dp
    val s44 = 44.dp
    val s48 = 48.dp
    val s64 = 64.dp
    val s72 = 72.dp
}
