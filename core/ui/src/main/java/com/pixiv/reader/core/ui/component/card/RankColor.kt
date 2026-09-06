package com.pixiv.reader.core.ui.component.card

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.pixiv.reader.core.ui.theme.AppShapes

/**
 * 排名徽标配色：1 金 / 2 橙 / 3 灰，其余返回 null（由调用方回退主题次级色）。
 * 供 [NovelCard]（小说榜排名徽标）与 [RankingIllustCard]（插画/漫画榜排名徽标）复用。
 */
internal fun rankColor(rank: Int): Color? = when (rank) {
    1 -> Color(0xFFE8A33D)
    2 -> Color(0xFFB45309)
    3 -> Color(0xFF6B7280)
    else -> null
}

/**
 * 排名徽标形状（Material 3 Expressive 形状语言）：前三名用 MaterialShapes 有机多边形——
 * 1 = Sunny / 2 = SoftBurst / 3 = Clover4Leaf；其余名次回退 [AppShapes.small] 小圆角矩形。
 *
 * @param rank 排名序号（从 1 开始）
 * @return 徽标背景形状
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rankBadgeShape(rank: Int): Shape = when (rank) {
    1 -> MaterialShapes.Sunny.toShape()
    2 -> MaterialShapes.SoftBurst.toShape()
    3 -> MaterialShapes.Clover4Leaf.toShape()
    else -> AppShapes.small
}
