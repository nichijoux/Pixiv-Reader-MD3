package com.pixiv.reader.core.ui.component.card

import androidx.compose.ui.graphics.Color

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
