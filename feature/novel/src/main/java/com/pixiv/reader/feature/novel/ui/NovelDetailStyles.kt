package com.pixiv.reader.feature.novel.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── 常量（对齐 design/novel-detail-ui.html） ─────────────────────────────────

internal val NOVEL_BANNER_HEIGHT = 280.dp
internal val NOVEL_BANNER_TABLET_HEIGHT = 360.dp
internal val NOVEL_BANNER_GRADIENT_HEIGHT = 110.dp
internal val NOVEL_BANNER_SCRIM_HEIGHT = 96.dp
internal const val TABLET_WIDTH_DP = 600
internal const val NOVEL_TOC_MAX_HEIGHT_FRACTION = 0.4f
internal val NOVEL_TOC_PANEL_WIDTH = 264.dp

// ── 文字样式（基于 core:ui 统一 Typography 派生，禁止散落 magic number） ──

/** 字号走 type role，勿再 +2sp 破坏字阶。 */
@Composable
internal fun novelTitleStyle() = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)

@Composable
internal fun novelAuthorStyle() = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)

@Composable
internal fun novelMetaStyle() = MaterialTheme.typography.bodySmall

@Composable
internal fun novelStatValueStyle() = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)

@Composable
internal fun novelSmallLabelStyle() = MaterialTheme.typography.labelMedium

/** 行高随 type role，不要引入 14.5sp / 25sp 这类非阶梯值。 */
@Composable
internal fun novelIntroStyle() = MaterialTheme.typography.bodyMedium

@Composable
internal fun novelReadButtonStyle() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)

@Composable
internal fun novelTocTitleStyle() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)

@Composable
internal fun novelTocRowStyle() = MaterialTheme.typography.bodyMedium

@Composable
internal fun novelCurrentBadgeStyle() = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)

@Composable
internal fun novelCountBadgeStyle() = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)

@Composable
internal fun novelOptionTitleStyle() = MaterialTheme.typography.titleSmall