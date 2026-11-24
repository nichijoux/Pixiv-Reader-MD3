package com.pixiv.reader.core.ui.component.grid

import com.pixiv.reader.core.ui.component.card.IllustCard
import com.pixiv.reader.core.ui.component.list.LazyStaggeredGridPrefetchEffect
import com.pixiv.reader.core.ui.component.list.loadMoreFooter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.network.ugoira.UgoiraLoader
import com.pixiv.reader.core.ui.component.layout.LocalNavBarBottomPadding
import com.pixiv.reader.core.ui.theme.Spacing

/** 仅在作品缺少宽高数据时作为瀑布流封面回退高度。 */
private val CoverHeights = listOf(150.dp, 120.dp, 180.dp, 140.dp, 130.dp, 160.dp)

/**
 * 自适应列数插画瀑布流（[illusts] 需含 width/height 否则封面按回退高度裁切）；
 * [scrollToTopKey] 仅在相对上次组合变化时滚回首项（首次组合不触发）。
 */
@Composable
fun IllustWaterfallGrid(
    illusts: List<Illust>,
    onItemClick: (Long) -> Unit,
    onLoadMore: () -> Unit,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    modifier: Modifier = Modifier,
    error: String? = null,
    contentPadding: PaddingValues = PaddingValues(
        start = Spacing.md,
        end = Spacing.md,
        top = Spacing.sm,
        bottom = Spacing.xl + LocalNavBarBottomPadding.current + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    ),
    minColumnWidth: Dp = 160.dp,
    onToggleFavorite: ((Long, Boolean) -> Unit)? = null,
    onOpenUser: ((Long) -> Unit)? = null,
    onTagClick: ((String) -> Unit)? = null,
    ugoiraLoader: UgoiraLoader? = null,
    header: (@Composable () -> Unit)? = null,
    scrollToTopKey: Int = 0,
) {
    val gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState()
    var lastScrollKey by remember { mutableIntStateOf(scrollToTopKey) }
    LaunchedEffect(scrollToTopKey) {
        if (scrollToTopKey != lastScrollKey) {
            lastScrollKey = scrollToTopKey
            gridState.animateScrollToItem(0)
        }
    }
    // 距底部 8 项时提前静默拉取下一页数据，避免滑到最底撞墙等待
    LazyStaggeredGridPrefetchEffect(
        state = gridState,
        itemCount = illusts.size,
        hasMore = hasMore,
        isLoadingMore = isLoadingMore,
        error = error,
        onLoadMore = onLoadMore,
        headerCount = if (header != null) 1 else 0,
    )
    LazyVerticalStaggeredGrid(
        state = gridState,
        columns = StaggeredGridCells.Adaptive(minColumnWidth),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalItemSpacing = Spacing.sm,
    ) {
        if (header != null) {
            item(span = StaggeredGridItemSpan.FullLine, key = "grid_header", contentType = "header") {
                header()
            }
        }
        items(
            items = illusts,
            key = { it.id },
            contentType = { if (it.isGif()) "ugoira" else "illust" },
        ) { illust ->
            // 用 mod 避免负 ID 取余产生负数索引越界
            val coverHeight: Dp = CoverHeights[illust.id.mod(CoverHeights.size)]
            IllustCard(
                illust = illust,
                onClick = { onItemClick(illust.id) },
                coverHeight = coverHeight,
                onToggleFavorite = onToggleFavorite?.let { cb -> { fav -> cb(illust.id, fav) } },
                onOpenAuthor = onOpenUser?.let { cb ->
                    { illust.user?.id?.let(cb); }
                } ?: {},
                onTagClick = onTagClick,
                ugoiraLoader = ugoiraLoader,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        loadMoreFooter(
            hasMore = hasMore,
            isLoadingMore = isLoadingMore,
            onLoadMore = onLoadMore,
            span = StaggeredGridItemSpan.FullLine,
            error = error,
        )
    }
}
