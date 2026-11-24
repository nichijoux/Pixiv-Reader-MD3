package com.pixiv.reader.core.ui.component.grid

import com.pixiv.reader.core.ui.component.card.IllustCard
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.network.ugoira.UgoiraLoader
import com.pixiv.reader.core.ui.component.layout.LocalNavBarBottomPadding
import com.pixiv.reader.core.ui.theme.Spacing
import kotlinx.coroutines.flow.distinctUntilChanged

/** 仅在作品缺少宽高数据时作为瀑布流封面回退高度。 */
private val CoverHeights = listOf(150.dp, 120.dp, 180.dp, 140.dp, 130.dp, 160.dp)

private const val PREFETCH_AHEAD_COUNT = 8

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
    val context = LocalContext.current
    // 组合期捕获动效方案：items lambda 非组合上下文
    val motionScheme = MaterialTheme.motionScheme
    var lastScrollKey by remember { mutableIntStateOf(scrollToTopKey) }
    LaunchedEffect(scrollToTopKey) {
        if (scrollToTopKey != lastScrollKey) {
            lastScrollKey = scrollToTopKey
            gridState.animateScrollToItem(0)
        }
    }
    // 仅预热磁盘缓存（关闭内存缓存避免按错误尺寸重复解码占 RAM），消除滑入视口时的网络等待
    val prefetchedUrls = remember(illusts.firstOrNull()?.id) { HashSet<String>() }
    val hasHeader = header != null
    LaunchedEffect(gridState, illusts, hasHeader) {
        if (illusts.isEmpty()) return@LaunchedEffect
        val imageLoader = context.imageLoader
        val headerOffset = if (hasHeader) 1 else 0
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (lastVisible < 0) return@collect
                val startIndex = (lastVisible - headerOffset + 1).coerceAtLeast(0)
                val endExclusive = (startIndex + PREFETCH_AHEAD_COUNT).coerceAtMost(illusts.size)
                for (i in startIndex until endExclusive) {
                    val url = illusts[i].image_urls?.medium
                        ?: illusts[i].image_urls?.square_medium
                        ?: continue
                    if (prefetchedUrls.add(url)) {
                        imageLoader.enqueue(
                            ImageRequest.Builder(context)
                                .data(url)
                                .memoryCachePolicy(CachePolicy.DISABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .build(),
                        )
                    }
                }
            }
    }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(
                        fadeInSpec = motionScheme.fastEffectsSpec<Float>(),
                        placementSpec = motionScheme.fastSpatialSpec<IntOffset>(),
                    ),
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
