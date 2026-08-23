package com.pixiv.reader.feature.novel.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── 常量（对齐 design/novel-detail-ui.html） ─────────────────────────────────

/** 手机端 banner 高度。 */
internal val NOVEL_BANNER_HEIGHT = 280.dp
/** 平板端 banner 高度（HTML 平板 360px）。 */
internal val NOVEL_BANNER_TABLET_HEIGHT = 360.dp
/** banner 底部渐变高度。 */
internal val NOVEL_BANNER_GRADIENT_HEIGHT = 110.dp
/** banner 顶部 scrim 高度（保证悬浮白色返回按钮在浅色封面上可见）。 */
internal val NOVEL_BANNER_SCRIM_HEIGHT = 96.dp
/** 平板判断阈值（screenWidthDp ≥ 该值走双栏布局）。 */
internal const val TABLET_WIDTH_DP = 600
/** 手机端系列目录滚动区最大高度（占屏高比例，避免随分册数量增高）。 */
internal const val NOVEL_TOC_MAX_HEIGHT_FRACTION = 0.4f
/** 平板左栏系列目录宽度。 */
internal val NOVEL_TOC_PANEL_WIDTH = 264.dp

// ── 文字样式（基于 core:ui 统一 Typography 派生，对齐 HTML 并整体 +1sp，禁止散落 magic number） ──

/** 标题：titleLarge + 22sp Bold。 */
@Composable
internal fun novelTitleStyle() = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold)

/** 作者名：bodyMedium + 15sp SemiBold。 */
@Composable
internal fun novelAuthorStyle() = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

/** 次级信息（发布时间 / 查看完整系列 / 展开 / 下载进度）：bodyMedium 13sp。 */
@Composable
internal fun novelMetaStyle() = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)

/** 统计数值：bodyLarge Bold。 */
@Composable
internal fun novelStatValueStyle() = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)

/** 小标签（统计标签 / 标签 / 竖排按钮 / 目录 meta / 序号徽标）：labelMedium。 */
@Composable
internal fun novelSmallLabelStyle() = MaterialTheme.typography.labelMedium

/** 简介：bodyMedium 14.5sp + 行高 25sp。 */
@Composable
internal fun novelIntroStyle() = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp, lineHeight = 25.sp)

/** 阅读主按钮：titleMedium SemiBold。 */
@Composable
internal fun novelReadButtonStyle() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)

/** 系列目录标题：titleMedium Bold。 */
@Composable
internal fun novelTocTitleStyle() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)

/** 系列目录行标题：bodyMedium（当前章加粗在调用处 copy）。 */
@Composable
internal fun novelTocRowStyle() = MaterialTheme.typography.bodyMedium

/** 「当前」胶囊：labelMedium 11sp SemiBold。 */
@Composable
internal fun novelCurrentBadgeStyle() = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

/** 数量胶囊：labelMedium SemiBold。 */
@Composable
internal fun novelCountBadgeStyle() = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)

/** 下载对话框选项标题：bodyMedium 15sp Medium。 */
@Composable
internal fun novelOptionTitleStyle() = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium)