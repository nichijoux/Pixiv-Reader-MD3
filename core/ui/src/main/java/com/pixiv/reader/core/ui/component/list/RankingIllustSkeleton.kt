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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.pixiv.reader.core.ui.theme.Spacing

/**
 * 插画/漫画排行榜加载骨架：仿 [RankingIllustCard] 布局（大图封面 + 左上排名徽标占位 + 标题/作者行）
 * 渲染 6 张占位卡，呼吸脉冲替代全屏转圈——数据到位后淡入真实列表。
 *
 * 对应 [RankingList] 的 itemContent 为 `RankingIllustCard` 的排行榜（插画/漫画榜）专用；
 * 小说榜（itemContent 为 `NovelCard`）用 `NovelFeedSkeleton`，默认行骨架仅用于行式 itemContent。
 */
@Composable
fun RankingIllustSkeleton() {
    val color = skeletonPulseColor(label = "rankingIllustSkeleton")
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(count = 6) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                shape = AppShapes.card,
            ) {
                Column {
                    // 封面区：大图 4:3 占位（顶部留排名徽标占位空间）
                    Box(modifier = Modifier.fillMaxWidth()) {
                        SkeletonBlock(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f),
                            color = color,
                        )
                        // 左上排名徽标占位（对齐 RankingIllustCard 的 rank 徽标）
                        SkeletonBlock(
                            modifier = Modifier
                                .padding(Spacing.sm)
                                .size(34.dp)
                                .clip(AppShapes.small),
                            color = color,
                        )
                    }
                    // 信息区：标题 + 作者行
                    Column(modifier = Modifier.padding(10.dp)) {
                        SkeletonBlock(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            color = color,
                        )
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SkeletonBlock(
                                modifier = Modifier.size(20.dp).clip(AppShapes.small),
                                color = color,
                            )
                            SkeletonBlock(
                                modifier = Modifier.width(80.dp).height(12.dp).clip(RoundedCornerShape(6.dp)),
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
