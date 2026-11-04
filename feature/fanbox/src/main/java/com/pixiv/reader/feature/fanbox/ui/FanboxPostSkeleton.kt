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

/** 骨架占位卡数量（首屏可见约 5~6 张）。 */
private const val SKELETON_CARD_COUNT = 6

/**
 * FANBOX 投稿流加载骨架：仿 `FanboxPostCard` 结构（16:9 封面 + 标题 + 创作者行 + 摘要 + 徽标/计数行），
 * 呼吸脉冲占位替代全屏转圈；列表边距与真实列表一致，切到真实内容时不跳变。
 *
 * @param modifier 外部传入的 Modifier
 * @return 无返回值（渲染 Composable）
 */
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
                    // 封面：16:9（与真实卡一致，仅在有封面时渲染）
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        color = color,
                    )
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        // 标题（两行）
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
                        // 创作者行：圆头像 + 昵称 + 行尾时间
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
                        // 摘要两行
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
                        // 元信息行：费用徽标 + 点赞/评论计数
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
