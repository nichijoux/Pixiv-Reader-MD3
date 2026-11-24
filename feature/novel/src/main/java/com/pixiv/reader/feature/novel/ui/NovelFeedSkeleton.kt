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
 * 小说列表加载骨架：6 张占位卡，[showBannerHeader] 为 true 时列表顶部叠排行榜入口 banner 骨架
 * （对齐推荐页真实 header；关注页无 header 传 false）。
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

/** 单张小说卡骨架，供列表骨架与系列页骨架复用；[color] 由调用方 [skeletonPulseColor] 提供以复用同一动画源。 */
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
