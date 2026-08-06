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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.component.RankingBannerSkeleton
import com.pixiv.reader.core.ui.component.SkeletonBlock
import com.pixiv.reader.core.ui.component.skeletonPulseColor

/**
 * 小说列表加载骨架：仿 [NovelCard] 布局（封面 104dp 3:4 + 标题/系列/作者行 + 底部标签胶囊）
 * 渲染 6 张占位卡，呼吸脉冲替代全屏转圈——首载与下拉刷新共用。
 * [showBannerHeader] 为 true 时列表顶部渲染排行榜入口 banner 骨架占位（对齐真实列表 header，
 * 如推荐页的排行榜入口；关注页无 header 传 false）。
 */
@Composable
internal fun NovelFeedSkeleton(showBannerHeader: Boolean) {
    val color = skeletonPulseColor(label = "novelFeedSkeleton")
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showBannerHeader) {
            item(key = "skeleton_header") { RankingBannerSkeleton() }
        }
        items(count = 6) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // 上部分：左封面 | 右信息（作者行抵底）
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        SkeletonBlock(
                            modifier = Modifier
                                .width(104.dp)
                                .aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(12.dp)),
                            color = color,
                        )
                        Column(
                            modifier = Modifier
                                .padding(start = 14.dp)
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            SkeletonBlock(
                                modifier = Modifier.fillMaxWidth(0.75f).height(16.dp).clip(RoundedCornerShape(6.dp)),
                                color = color,
                            )
                            SkeletonBlock(
                                modifier = Modifier.padding(top = 8.dp).fillMaxWidth(0.35f).height(12.dp).clip(RoundedCornerShape(6.dp)),
                                color = color,
                            )
                            Spacer(Modifier.weight(1f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                SkeletonBlock(Modifier.size(24.dp).clip(CircleShape), color)
                                SkeletonBlock(
                                    modifier = Modifier.padding(start = 8.dp).width(80.dp).height(10.dp).clip(RoundedCornerShape(6.dp)),
                                    color = color,
                                )
                                Spacer(Modifier.weight(1f))
                                SkeletonBlock(
                                    modifier = Modifier.width(40.dp).height(10.dp).clip(RoundedCornerShape(6.dp)),
                                    color = color,
                                )
                            }
                        }
                    }
                    // 下部分：标签胶囊占位
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        repeat(3) {
                            SkeletonBlock(
                                modifier = Modifier.width(56.dp).height(20.dp).clip(RoundedCornerShape(10.dp)),
                                color = color,
                            )
                        }
                    }
                }
            }
        }
    }
}
