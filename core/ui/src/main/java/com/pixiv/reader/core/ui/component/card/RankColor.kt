package com.pixiv.reader.core.ui.component.card

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.pixiv.reader.core.ui.theme.AppShapes

/**
 * 排名徽标配色：1 金 / 2 橙 / 3 银灰，其余返回 null（由调用方回退白色）。
 * 供 [NovelCard]（小说榜排名徽标）与 [IllustCard]（插画/漫画榜排名徽标，rank 参数）复用。
 *
 * 色值必须按 [ImageScrim]（黑 70%）底校验过 WCAG 对比度（最坏情况纯白封面 → 底 #4C4C4C）：
 * 金 #FFD98A = 6.4:1、橙 #FFC08A = 5.4:1、银灰 #D5D9DE = 6.1:1，均达小字 4.5:1；
 * 旧值 #B45309 / #6B7280 在该底上只有 1.4:1，截图亮度稍高即看不清。
 *
 * @param rank 排名序号（从 1 开始）
 * @return 徽标文字颜色；非前三名返回 null
 */
internal fun rankColor(rank: Int): Color? = when (rank) {
    1 -> Color(0xFFFFD98A)
    2 -> Color(0xFFFFC08A)
    3 -> Color(0xFFD5D9DE)
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
