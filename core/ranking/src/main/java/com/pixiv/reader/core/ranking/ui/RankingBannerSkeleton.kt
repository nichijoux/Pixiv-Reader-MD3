package com.pixiv.reader.core.ranking.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.component.feedback.SkeletonBlock
import com.pixiv.reader.core.ui.component.feedback.skeletonPulseColor
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.core.ui.theme.Spacing

/**
 * 排行榜入口 banner 骨架占位：仿 [RankingList] 页顶排行榜入口（NovelRankingBanner / MangaRankingBanner）布局
 * ——图标底块（圆形，与真实 banner 同形）+ 两行文本条 + 右侧箭头块，
 * 位置/尺寸对齐真实入口，保证加载/刷新骨架阶段排行榜入口区域不"消失"。
 * 纯脉冲灰色，加载中不可点，数据到位后淡入真实 banner。
 *
 * @param modifier 外部传入的 Modifier
 * @return 无返回值（渲染 Composable）
 */
@Composable
fun RankingBannerSkeleton(modifier: Modifier = Modifier) {
    val color = skeletonPulseColor(label = "rankingBannerSkeleton")
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.mdPlus, vertical = Spacing.xsPlus),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.mdPlus),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.mdPlus),
        ) {
            SkeletonBlock(
                modifier = Modifier
                    .size(Sizes.s48)
                    .clip(CircleShape),
                color = color,
            )
            Column(modifier = Modifier.weight(1f)) {
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .height(16.dp)
                        .clip(AppShapes.small),
                    color = color,
                )
                SkeletonBlock(
                    modifier = Modifier
                        .padding(top = Spacing.xsPlus)
                        .fillMaxWidth(0.3f)
                        .height(12.dp)
                        .clip(AppShapes.small),
                    color = color,
                )
            }
            SkeletonBlock(
                modifier = Modifier.size(Sizes.s20),
                color = color,
            )
        }
    }
}
