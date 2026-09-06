package com.pixiv.reader.core.ui.component.list

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

/** 封面占位宽高比序列（width/height）：模拟真实作品比例多样性，制造瀑布流错落高度。 */
private val COVER_RATIOS = listOf(3f / 4f, 4f / 3f, 1f, 2f / 3f)

/**
 * 插画/漫画排行榜加载骨架：与真实榜单同款**瀑布流网格**（`LazyVerticalStaggeredGrid` +
 * `StaggeredGridCells.Adaptive([gridMinColumnWidth])`，列数/内容边距/间距与 [RankingList]
 * 完全一致），每张占位卡对齐 [RankingIllustCard]——封面（比例交替制造错落高度）+
 * 左上排名徽标胶囊 + 右下收藏数胶囊 + 标题条/作者行。渲染 12 张占位卡，
 * 1s 缓慢呼吸脉冲，数据到位后淡入真实列表。
 *
 * @param gridMinColumnWidth 瀑布流自适应列宽（须与 [RankingList] 的 gridMinColumnWidth 传同一值，
 *                           否则骨架列数与真实列表不一致）
 * @return 无返回值
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
                // 真实卡片为 cardLarge（14dp）圆角
                shape = AppShapes.cardLarge,
            ) {
                Column {
                    // 封面区：比例按索引交替（竖/横/方），叠加徽标与收藏数占位
                    Box(modifier = Modifier.fillMaxWidth()) {
                        SkeletonBlock(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(COVER_RATIOS[index % COVER_RATIOS.size]),
                            color = color,
                        )
                        // 左上排名徽标占位：胶囊形（对齐真实徽标的圆角文字条）
                        SkeletonBlock(
                            modifier = Modifier
                                .padding(Spacing.sm)
                                .align(Alignment.TopStart)
                                .size(width = 30.dp, height = 18.dp)
                                .clip(AppShapes.pill),
                            color = color,
                        )
                        // 右下收藏数占位：小胶囊（对齐真实卡片右下角「♥ 数量」角标）
                        SkeletonBlock(
                            modifier = Modifier
                                .padding(Spacing.sm)
                                .align(Alignment.BottomEnd)
                                .size(width = 44.dp, height = 16.dp)
                                .clip(AppShapes.pill),
                            color = color,
                        )
                    }
                    // 信息区：标题条 + 作者行（头像 + 名称条）
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
