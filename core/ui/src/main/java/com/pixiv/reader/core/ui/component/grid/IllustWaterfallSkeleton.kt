package com.pixiv.reader.core.ui.component.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.component.feedback.SkeletonBlock
import com.pixiv.reader.core.ui.component.feedback.skeletonPulseColor
import com.pixiv.reader.core.ui.theme.Spacing

/** 瀑布流骨架封面高度循环（与 [IllustWaterfallGrid] 的 CoverHeights 一致，模拟错落）。 */
private val SkeletonCoverHeights = listOf(150.dp, 120.dp, 180.dp, 140.dp, 130.dp, 160.dp)

/**
 * 插画瀑布流骨架（首页 / 漫画页 / 搜索结果首载与刷新共用的加载占位）。
 *
 * 布局对齐 [IllustWaterfallGrid]：`LazyVerticalStaggeredGrid` + `Adaptive(minColumnWidth)`，
 * 渲染 8 张占位卡（封面块交替高度 + 标题 2 行 + 作者行），呼吸脉冲动画替代全屏转圈；
 * 可选 [header] 在网格顶部整行渲染（对齐真实网格的 header，如排行榜入口 banner 骨架占位）。
 *
 * @param modifier 外部传入的 Modifier（默认 `fillMaxSize`）
 * @param contentPadding 与真实网格一致的内容边距
 * @param minColumnWidth 每列最小宽度（与真实网格一致）
 * @param header 网格头部内容（整行跨列，随列表滚动）；null 不显示
 */
@Composable
fun IllustWaterfallSkeleton(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = 96.dp),
    minColumnWidth: Dp = 140.dp,
    header: (@Composable () -> Unit)? = null,
) {
    val color = skeletonPulseColor(label = "illustFeedSkeleton")
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minColumnWidth),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalItemSpacing = Spacing.sm,
    ) {
        if (header != null) {
            item(span = StaggeredGridItemSpan.FullLine, key = "skeleton_header") {
                header()
            }
        }
        items(count = 8) { index ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SkeletonCoverHeights[index % SkeletonCoverHeights.size])
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
