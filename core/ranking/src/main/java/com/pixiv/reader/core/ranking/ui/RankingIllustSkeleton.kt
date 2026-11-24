package com.pixiv.reader.core.ranking.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.component.feedback.SkeletonBlock
import com.pixiv.reader.core.ui.component.feedback.skeletonPulseColor
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes

/** 模拟真实作品比例多样性，制造瀑布流错落高度。 */
private val COVER_RATIOS = listOf(3f / 4f, 4f / 3f, 1f, 2f / 3f)

/**
 * 列数/内容边距/间距须与 [RankingList] 一致，否则首载切真实列表时会跳列。
 */
@Composable
fun RankingIllustSkeleton(gridMinColumnWidth: Dp = RANKING_GRID_MIN_COLUMN_WIDTH) {
    val color = skeletonPulseColor(label = "rankingIllustSkeleton")
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(gridMinColumnWidth),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.xs, bottom = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalItemSpacing = Spacing.sm,
    ) {
        items(count = 12) { index ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                shape = AppShapes.cardLarge,
            ) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        SkeletonBlock(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(COVER_RATIOS[index % COVER_RATIOS.size]),
                            color = color,
                        )
                        SkeletonBlock(
                            modifier = Modifier
                                .padding(Spacing.sm)
                                .align(Alignment.TopStart)
                                .size(width = 30.dp, height = 18.dp)
                                .clip(AppShapes.pill),
                            color = color,
                        )
                        SkeletonBlock(
                            modifier = Modifier
                                .padding(Spacing.sm)
                                .align(Alignment.BottomEnd)
                                .size(width = 44.dp, height = 16.dp)
                                .clip(AppShapes.pill),
                            color = color,
                        )
                    }
                    Column(modifier = Modifier.padding(Spacing.smPlus)) {
                        SkeletonBlock(
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .height(14.dp)
                                .clip(AppShapes.tiny),
                            color = color,
                        )
                        Row(
                            modifier = Modifier.padding(top = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
                        ) {
                            SkeletonBlock(
                                modifier = Modifier.size(Sizes.s20).clip(AppShapes.small),
                                color = color,
                            )
                            SkeletonBlock(
                                modifier = Modifier.width(90.dp).height(12.dp).clip(AppShapes.tiny),
                                color = color,
                            )
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
