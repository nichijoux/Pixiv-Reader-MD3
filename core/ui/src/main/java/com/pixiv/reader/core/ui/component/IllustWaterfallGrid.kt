package com.pixiv.reader.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.pixivapi.model.Illust

/** 瀑布流封面高度循环（模拟不同纵横比） */
private val CoverHeights = listOf(150.dp, 120.dp, 180.dp, 140.dp, 130.dp, 160.dp)

/**
 * 插画瀑布流（自适应列数）+ 触底加载。
 * 手机约 2 列，平板自动增加到 3~4 列（StaggeredGridCells.Adaptive）。
 * 当 hasMore 时自动在底部触发 onLoadMore。
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
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minColumnWidth),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
    ) {
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
