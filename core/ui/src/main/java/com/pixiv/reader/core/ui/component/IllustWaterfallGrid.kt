package com.pixiv.reader.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.Illust

/** 瀑布流封面高度循环（模拟不同纵横比，仅用于无宽高数据时的回退）。 */
private val CoverHeights = listOf(150.dp, 120.dp, 180.dp, 140.dp, 130.dp, 160.dp)

/**
 * 插画瀑布流（自适应列数）+ 触底加载，首页 / 搜索结果 / 收藏 / 浏览历史共用。
 *
 * ## UI 设计方式
 * `LazyVerticalStaggeredGrid` + `StaggeredGridCells.Adaptive(minColumnWidth)`：
 * 手机约 2 列，平板自动 3~4 列。内部每项渲染 [IllustCard]（封面按宽高比完整显示）。
 * `hasMore` 时网格尾部自动放一个加载 item 并触发 [onLoadMore]（无需调用方手动监听滚动）。
 *
 * @param illusts 作品列表（需含 `width/height` 以保证完整显示）
 * @param onItemClick 点击作品（参数为作品 id，通常打开详情）
 * @param onLoadMore 触底加载回调（`hasMore` 时自动触发）
 * @param hasMore 是否还有下一页
 * @param isLoadingMore 加载更多中（尾部显示加载指示器）
 * @param modifier 外部传入的 Modifier（默认 `fillMaxSize`）
 * @param contentPadding 网格内容边距
 * @param minColumnWidth 每列最小宽度（决定自适应列数）
 * @param onToggleFavorite 收藏切换回调（参数为 id + 目标状态）；null 则卡片不显示收藏按钮
 * @param header 网格头部内容（整行跨列，随列表滚动，如排行榜入口 banner）；null 不显示
 */
@Composable
fun IllustWaterfallGrid(
    illusts: List<Illust>,
    onItemClick: (Long) -> Unit,
    onLoadMore: () -> Unit,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
    minColumnWidth: Dp = 140.dp,
    onToggleFavorite: ((Long, Boolean) -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minColumnWidth),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
    ) {
        if (header != null) {
            item(span = StaggeredGridItemSpan.FullLine, key = "grid_header") {
                header()
            }
        }
        items(
            items = illusts,
            key = { it.id },
        ) { illust ->
            val coverHeight: Dp = CoverHeights[(illust.id % CoverHeights.size).toInt()]
            IllustCard(
                illust = illust,
                onClick = { onItemClick(illust.id) },
                coverHeight = coverHeight,
                onToggleFavorite = onToggleFavorite?.let { cb -> { fav -> cb(illust.id, fav) } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (hasMore) {
            item(key = "load_more") {
                LaunchedEffect(Unit) { onLoadMore() }
                Box(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isLoadingMore) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
