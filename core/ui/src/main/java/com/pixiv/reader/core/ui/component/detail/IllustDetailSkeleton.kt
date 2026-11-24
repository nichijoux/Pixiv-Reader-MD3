package com.pixiv.reader.core.ui.component.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

/** 完整加载骨架（深链/无 hero 种子兜底用）；有种子时由 pager 承载大图，仅拼接 [IllustInfoSkeleton]。 */
@Composable
fun IllustDetailSkeleton(modifier: Modifier = Modifier) {
    val color = skeletonPulseColor(label = "illustDetailSkeleton")
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(AppShapes.cardLarge),
            color = color,
        )
        IllustInfoSkeleton()
    }
}

@Composable
fun IllustInfoSkeleton(modifier: Modifier = Modifier) {
    val color = skeletonPulseColor(label = "illustInfoSkeleton")
    Column(modifier = modifier.padding(Spacing.lg)) {
        SkeletonBlock(
            modifier = Modifier.fillMaxWidth(0.85f).height(16.dp).clip(AppShapes.tiny),
            color = color,
        )
        SkeletonBlock(
            modifier = Modifier
                .padding(top = Spacing.sm)
                .fillMaxWidth(0.55f)
                .height(16.dp)
                .clip(AppShapes.tiny),
            color = color,
        )
        Row(
            modifier = Modifier.padding(top = Spacing.mdPlus),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkeletonBlock(modifier = Modifier.size(Sizes.s24).clip(AppShapes.circle), color = color)
            SkeletonBlock(
                modifier = Modifier
                    .padding(start = Spacing.sm)
                    .width(120.dp)
                    .height(12.dp)
                    .clip(AppShapes.tiny),
                color = color,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            repeat(3) {
                SkeletonBlock(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(AppShapes.tiny),
                    color = color,
                )
            }
        }
        Row(
            modifier = Modifier.padding(top = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
        ) {
            repeat(4) {
                SkeletonBlock(
                    modifier = Modifier.width(56.dp).height(20.dp).clip(AppShapes.cardSmall),
                    color = color,
                )
            }
        }
        SkeletonBlock(
            modifier = Modifier
                .padding(top = Spacing.lgPlus)
                .width(120.dp)
                .height(14.dp)
                .clip(AppShapes.tiny),
            color = color,
        )
        Row(
            modifier = Modifier.padding(top = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            repeat(3) {
                SkeletonBlock(
                    modifier = Modifier
                        .width(100.dp)
                        .height(130.dp)
                        .clip(AppShapes.card),
                    color = color,
                )
            }
        }
    }
}
