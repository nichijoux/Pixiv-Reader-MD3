package com.pixiv.reader.core.ui.component.card

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.pixiv.reader.core.ui.theme.AppShapes

/**
 * 1~3 名色值按 ImageScrim（70% 黑）叠加最浅纯白封面校验过 WCAG 4.5:1 对比度（6.4 / 5.4 / 6.1:1），
 * 勿改回暗色导致高光封面下不可辨；其余名次返回 null 回退白色。
 */
internal fun rankColor(rank: Int): Color? = when (rank) {
    1 -> Color(0xFFFFD98A)
    2 -> Color(0xFFFFC08A)
    3 -> Color(0xFFD5D9DE)
    else -> null
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rankBadgeShape(rank: Int): Shape = when (rank) {
    1 -> MaterialShapes.Sunny.toShape()
    2 -> MaterialShapes.SoftBurst.toShape()
    3 -> MaterialShapes.Clover4Leaf.toShape()
    else -> AppShapes.small
}
