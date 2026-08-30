package com.pixiv.reader.core.ui.theme

import androidx.compose.ui.unit.dp

/**
 * 全局间距刻度（Material spacing 风格，2dp 步进）。
 *
 * 基准档：xs=4 / sm=8 / md=12 / lg=16 / xl=24；
 * 半档（`*Plus`，介于相邻基准档之间）：xxs=2 / xsPlus=6 / smPlus=10 / mdPlus=14 / lgPlus=20。
 *
 * 新增 UI 一律引用本刻度；存量代码迁移见 agent.md Magic Number 轮次。
 */
object Spacing {
    /** 2dp：极微间距 / 边框内微缩 */
    val xxs = 2.dp

    /** 4dp：元素内微间距 */
    val xs = 4.dp

    /** 6dp：紧凑 chip / 行内间距（xs 与 sm 之间半档） */
    val xsPlus = 6.dp

    /** 8dp：紧凑间距 / 图标与文字间隙 */
    val sm = 8.dp

    /** 10dp：行距 / 控件内边距（sm 与 md 之间半档） */
    val smPlus = 10.dp

    /** 12dp：默认元素间距 */
    val md = 12.dp

    /** 14dp：卡片内边距变体（md 与 lg 之间半档） */
    val mdPlus = 14.dp

    /** 16dp：卡片内边距 / 页面级间距 */
    val lg = 16.dp

    /** 20dp：区块内大间距（lg 与 xl 之间半档） */
    val lgPlus = 20.dp

    /** 24dp：区块级大间距 */
    val xl = 24.dp
}

/**
 * 通用元素尺寸（图标 / 头像 / 装饰元素），按数值命名避免语义误判。
 *
 * 仅收纳跨组件复用的尺寸档；组件独有布局尺寸（封面、Banner、列宽等）保持组件内常量。
 */
object Sizes {
    /** 16dp：小图标 */
    val s16 = 16.dp

    /** 18dp：次级图标 */
    val s18 = 18.dp

    /** 20dp：中图标 */
    val s20 = 20.dp

    /** 22dp：中图标变体 */
    val s22 = 22.dp

    /** 24dp：标准图标 / 按钮内图标 */
    val s24 = 24.dp

    /** 28dp：小头像 / 缩略图 */
    val s28 = 28.dp

    /** 32dp：大图标 */
    val s32 = 32.dp

    /** 36dp：头像 / 大图标 */
    val s36 = 36.dp

    /** 40dp：标准头像 / 触控元素 */
    val s40 = 40.dp

    /** 44dp：大触控元素 */
    val s44 = 44.dp

    /** 48dp：触控元素 / 装饰容器（Material 最小触控目标；骨架占位、banner 图标底块） */
    val s48 = 48.dp

    /** 64dp：大头像 / 页面级大图标（用户头像、创建者头像、引导页图标） */
    val s64 = 64.dp

    /** 72dp：超大头像 */
    val s72 = 72.dp
}
