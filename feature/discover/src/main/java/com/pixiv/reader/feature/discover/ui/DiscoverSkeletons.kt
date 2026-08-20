package com.pixiv.reader.feature.discover.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.component.feedback.SkeletonBlock
import com.pixiv.reader.core.ui.component.feedback.skeletonPulseColor

/**
 * 插画搜索结果骨架：仿 [com.pixiv.reader.core.ui.component.IllustWaterfallGrid]
 * （自适应 2 列瀑布流）渲染 8 张占位卡
 * ——圆角 14dp 卡片 + 封面块（交替高度模拟瀑布流）+ 标题 2 行 + 作者行（20dp 圆头像 + 名称条）。
 */
@Composable
internal fun IllustSearchSkeleton() {
    val color = skeletonPulseColor()
    val coverHeights = listOf(150.dp, 120.dp, 180.dp, 140.dp, 130.dp, 160.dp)
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(140.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
    ) {
        items(count = 8) { index ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(coverHeights[index % coverHeights.size])
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                    color = color,
                )
                Column(modifier = Modifier.padding(10.dp)) {
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = color,
                    )
                    SkeletonBlock(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth(0.5f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = color,
                    )
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SkeletonBlock(
                            modifier = Modifier.size(20.dp).clip(CircleShape),
                            color = color,
                        )
                        SkeletonBlock(
                            modifier = Modifier.padding(start = 6.dp).width(80.dp).height(10.dp).clip(RoundedCornerShape(6.dp)),
                            color = color,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 小说搜索结果骨架：仿 [com.pixiv.reader.core.ui.component.NovelCard]（横排卡片列表）渲染 6 张占位卡
 * ——圆角 16dp Card + 左侧 104dp 3/4 封面块 + 右侧标题/作者条。
 */
@Composable
internal fun NovelSearchSkeleton() {
    val color = skeletonPulseColor()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(count = 6) {
            androidx.compose.material3.Card(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(modifier = Modifier.padding(14.dp)) {
                    SkeletonBlock(
                        modifier = Modifier
                            .width(104.dp)
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(12.dp)),
                        color = color,
                    )
                    Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                        SkeletonBlock(
                            modifier = Modifier.fillMaxWidth(0.75f).height(16.dp).clip(RoundedCornerShape(6.dp)),
                            color = color,
                        )
                        SkeletonBlock(
                            modifier = Modifier.padding(top = 10.dp).fillMaxWidth(0.4f).height(12.dp).clip(RoundedCornerShape(6.dp)),
                            color = color,
                        )
                        Row(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SkeletonBlock(
                                modifier = Modifier.size(28.dp).clip(CircleShape),
                                color = color,
                            )
                            SkeletonBlock(
                                modifier = Modifier.padding(start = 8.dp).width(90.dp).height(10.dp).clip(RoundedCornerShape(6.dp)),
                                color = color,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 用户搜索结果骨架：仿 [com.pixiv.reader.core.ui.component.CreatorProfileCard] 渲染 5 张占位卡
 * ——圆角 16dp 卡片 + 顶部 120dp 三封面横排 + 底部 64dp 圆头像（重叠封面）+ 名称条 + 关注按钮块。
 */
@Composable
internal fun UserSearchSkeleton() {
    val color = skeletonPulseColor()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(count = 5) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                    repeat(3) {
                        SkeletonBlock(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            color = color,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.offset(y = (-24).dp)) {
                        SkeletonBlock(
                            modifier = Modifier.size(64.dp).clip(CircleShape),
                            color = color,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    SkeletonBlock(
                        modifier = Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(6.dp)),
                        color = color,
                    )
                    Spacer(Modifier.width(10.dp))
                    SkeletonBlock(
                        modifier = Modifier.width(72.dp).height(40.dp).clip(RoundedCornerShape(20.dp)),
                        color = color,
                    )
                }
            }
        }
    }
}
