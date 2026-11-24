package com.pixiv.reader.feature.fanbox.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
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

private const val SKELETON_CARD_COUNT = 6

/** 仿 [FanboxPostCard] 结构；列表边距与真实列表一致，切到真实内容时不跳变。 */
@Composable
internal fun FanboxPostSkeleton(modifier: Modifier = Modifier) {
    val color = skeletonPulseColor(label = "fanboxPostSkeleton")
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(count = SKELETON_CARD_COUNT) {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        color = color,
                    )
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        SkeletonBlock(
                            modifier = Modifier.fillMaxWidth(0.8f).height(16.dp).clip(AppShapes.tiny),
                            color = color,
                        )
                        SkeletonBlock(
                            modifier = Modifier
                                .padding(top = Spacing.sm)
                                .fillMaxWidth(0.45f)
                                .height(16.dp)
                                .clip(AppShapes.tiny),
                            color = color,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = Spacing.xs),
                        ) {
                            SkeletonBlock(
                                modifier = Modifier.size(Sizes.s24).clip(AppShapes.circle),
                                color = color,
                            )
                            SkeletonBlock(
                                modifier = Modifier
                                    .padding(start = Spacing.xs)
                                    .width(88.dp)
                                    .height(10.dp)
                                    .clip(AppShapes.tiny),
                                color = color,
                            )
                            Spacer(Modifier.weight(1f))
                            SkeletonBlock(
                                modifier = Modifier.width(56.dp).height(10.dp).clip(AppShapes.tiny),
                                color = color,
                            )
                        }
                        SkeletonBlock(
                            modifier = Modifier
                                .padding(top = Spacing.xs)
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(AppShapes.tiny),
                            color = color,
                        )
                        SkeletonBlock(
                            modifier = Modifier
                                .padding(top = Spacing.xs)
                                .fillMaxWidth(0.65f)
                                .height(10.dp)
                                .clip(AppShapes.tiny),
                            color = color,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = Spacing.sm),
                        ) {
                            SkeletonBlock(
                                modifier = Modifier.width(56.dp).height(18.dp).clip(AppShapes.pill),
                                color = color,
                            )
                            Spacer(Modifier.weight(1f))
                            SkeletonBlock(
                                modifier = Modifier.width(32.dp).height(12.dp).clip(AppShapes.tiny),
                                color = color,
                            )
                            SkeletonBlock(
                                modifier = Modifier
                                    .padding(start = Spacing.md)
                                    .width(32.dp)
                                    .height(12.dp)
                                    .clip(AppShapes.tiny),
                                color = color,
                            )
                        }
                    }
                }
            }
        }
    }
}
