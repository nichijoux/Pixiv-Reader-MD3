package com.pixiv.reader.feature.novel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ranking.ui.RankingBannerSkeleton
import com.pixiv.reader.core.ui.component.feedback.SkeletonBlock
import com.pixiv.reader.core.ui.component.feedback.skeletonPulseColor
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Sizes

/**
 * 小说列表加载骨架：仿 [NovelCard] 布局（封面 104dp 3:4 + 标题/系列/作者行 + 底部标签胶囊）
 * 渲染 6 张占位卡（[NovelCardSkeleton]），呼吸脉冲替代全屏转圈——首载与下拉刷新共用。
 * [showBannerHeader] 为 true 时列表顶部渲染排行榜入口 banner 骨架占位（对齐真实列表 header，
 * 如推荐页的排行榜入口；关注页无 header 传 false）。
 *
 * @param showBannerHeader 是否在列表顶部渲染排行榜入口 banner 骨架
 * @return 无返回值（渲染 Composable）
 */
@Composable
internal fun NovelFeedSkeleton(showBannerHeader: Boolean) {
    val color = skeletonPulseColor(label = "novelFeedSkeleton")
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.smPlus),
    ) {
        if (showBannerHeader) {
            item(key = "skeleton_header") { RankingBannerSkeleton() }
        }
        items(count = 6) {
            NovelCardSkeleton(color = color)
        }
    }
}

/**
 * 单张小说卡骨架（仿 [NovelCard]：封面 104dp 3:4 + 标题/系列/作者行 + 底部标签胶囊）。
 * 抽出具名组件供列表骨架与系列页骨架复用。
 *
 * @param color 呼吸脉冲占位色（由调用方 [skeletonPulseColor] 提供，同屏复用同一动画源）
 * @param modifier 外部传入的 Modifier（如列表页的水平内边距）
 * @return 无返回值（渲染 Composable）
 */
@Composable
internal fun NovelCardSkeleton(color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = AppShapes.large,
    ) {
        Column(modifier = Modifier.padding(Spacing.mdPlus)) {
            // 上部分：左封面 | 右信息（作者行抵底）
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                SkeletonBlock(
                    modifier = Modifier
                        .width(104.dp)
                        .aspectRatio(3f / 4f)
                        .clip(AppShapes.card),
                    color = color,
                )
                Column(
                    modifier = Modifier
                        .padding(start = Spacing.mdPlus)
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    SkeletonBlock(
                        modifier = Modifier.fillMaxWidth(0.75f).height(16.dp).clip(AppShapes.tiny),
                        color = color,
                    )
                    SkeletonBlock(
                        modifier = Modifier.padding(top = Spacing.sm).fillMaxWidth(0.35f).height(12.dp).clip(AppShapes.tiny),
                        color = color,
                    )
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkeletonBlock(Modifier.size(Sizes.s24).clip(CircleShape), color)
                        SkeletonBlock(
                            modifier = Modifier.padding(start = Spacing.sm).width(80.dp).height(10.dp).clip(AppShapes.tiny),
                            color = color,
                        )
                        Spacer(Modifier.weight(1f))
                        SkeletonBlock(
                            modifier = Modifier.width(40.dp).height(10.dp).clip(AppShapes.tiny),
                            color = color,
                        )
                    }
                }
            }
            // 下部分：标签胶囊占位
            Row(
                modifier = Modifier.padding(top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
            ) {
                repeat(3) {
                    SkeletonBlock(
                        modifier = Modifier.width(56.dp).height(20.dp).clip(AppShapes.cardSmall),
                        color = color,
                    )
                }
            }
        }
    }
}
