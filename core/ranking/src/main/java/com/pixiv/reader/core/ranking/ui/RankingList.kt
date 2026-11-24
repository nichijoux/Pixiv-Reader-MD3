package com.pixiv.reader.core.ranking.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ranking.state.RankingModeInfo
import com.pixiv.reader.core.ui.component.list.LazyListPrefetchEffect
import com.pixiv.reader.core.ui.component.list.LazyStaggeredGridPrefetchEffect
import com.pixiv.reader.core.ui.component.list.LoadMoreItem
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.FeedPhase
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.feedback.feedPhase
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.theme.Spacing
import kotlinx.coroutines.launch

/** 漫画/插画排行榜瀑布流最小列宽：必须与 [RankingIllustSkeleton] 同值，否则骨架列数与真实列表不一致。 */
val RANKING_GRID_MIN_COLUMN_WIDTH = 200.dp

private const val CENTERED_TAB_MAX = 4

/**
 * 通用排行榜容器（漫画/插画/小说复用）。
 *
 * 每页只 collect **自己 mode** 的 [PagedState]（实例由调用方 ViewModel 缓存、数据驻留 VM）：已加载段滑动切回
 * 直接显示、不重复请求；[AnimatedContent] 的 targetState 用**该页自身内容状态**，首次数据到位纯淡入，切回已就绪页
 * 不重播过渡。
 *
 * [filter] 只隐藏不匹配项，名次仍取原列表位置；[filter] 的过滤行为变化时调用方**必须**传不同的 [filterKey]，
 * 否则会沿用旧过滤结果。[stateKey]（如日期筛选）变更时各页按新维度取新 [PagedState] 并重新触发加载。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> RankingList(
    modes: List<RankingModeInfo>,
    onModeSelect: (String) -> Unit,
    stateFor: (String) -> PagedState<T>,
    onRetry: (String) -> Unit,
    onLoadMore: (String) -> Unit,
    modifier: Modifier = Modifier,
    emptyText: String,
    filter: ((T) -> Boolean)? = null,
    filterKey: Any? = null,
    filteredEmptyText: String? = null,
    skeleton: @Composable () -> Unit = {},
    gridMinColumnWidth: Dp? = null,
    stateKey: String = "",
    listHeader: (@Composable () -> Unit)? = null,
    itemContent: @Composable (T, Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { modes.size.coerceAtLeast(1) },
    )

    val activatedModes = remember(stateKey) { mutableStateMapOf<String, Boolean>() }
    val activeMode = modes.getOrNull(pagerState.currentPage)?.value
    LaunchedEffect(activeMode, stateKey) {
        val mode = activeMode ?: return@LaunchedEffect
        // 尚未访问的空邻页保持骨架，已请求的空页可继续分页或进入空态。
        onModeSelect(mode)
        activatedModes[mode] = true
    }

    AdaptiveContentBox(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            listHeader?.invoke()
            if (modes.size <= CENTERED_TAB_MAX) {
                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage.coerceIn(0, (modes.size - 1).coerceAtLeast(0)),
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    for (index in modes.indices) {
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(stringResource(modes[index].labelRes)) },
                        )
                    }
                }
            } else {
                PrimaryScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage.coerceIn(0, (modes.size - 1).coerceAtLeast(0)),
                    edgePadding = Spacing.sm,
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    for (index in modes.indices) {
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(stringResource(modes[index].labelRes)) },
                        )
                    }
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                val mode = modes.getOrNull(page)
                if (mode == null) {
                    LoadingBox()
                } else {
                    // 该页独立的 PagedState；stateKey 变更时按新复合键取到新实例并重新加载
                    val paged = remember(mode.value, stateKey) { stateFor(mode.value) }
                    val items by paged.items.collectAsStateWithLifecycle()
                    val isLoading by paged.isLoading.collectAsStateWithLifecycle()
                    val isLoadingMore by paged.isLoadingMore.collectAsStateWithLifecycle()
                    val hasMore by paged.hasMore.collectAsStateWithLifecycle()
                    val error by paged.error.collectAsStateWithLifecycle()
                    val isActivated = activatedModes[mode.value] == true
                    val canContinue = hasMore && !isLoading && isActivated
                    val contentState = feedPhase(
                        loading = isLoading || (hasMore && !isActivated),
                        hasItems = items.isNotEmpty() || canContinue,
                        hasError = error != null,
                    )
                    // transitionSpec 非组合上下文：规格须在组合期取好再闭包捕获
                    val rankFade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                    AnimatedContent(
                        targetState = contentState,
                        transitionSpec = {
                            fadeIn(animationSpec = rankFade)
                                .togetherWith(fadeOut(animationSpec = rankFade))
                        },
                        label = "rankPage",
                    ) { state ->
                        when (state) {
                            FeedPhase.CONTENT -> RankingPage(
                                modeValue = mode.value,
                                items = items,
                                isLoading = isLoading || (hasMore && !isActivated && items.isEmpty()),
                                isLoadingMore = isLoadingMore,
                                hasMore = hasMore,
                                error = error,
                                onRetry = onRetry,
                                onLoadMore = onLoadMore,
                                emptyText = emptyText,
                                filter = filter,
                                filterKey = filterKey,
                                filteredEmptyText = filteredEmptyText,
                                gridMinColumnWidth = gridMinColumnWidth,
                                itemContent = itemContent,
                                modifier = Modifier.fillMaxSize(),
                            )
                            FeedPhase.ERROR -> ErrorBox(
                                message = error,
                                onRetry = { onRetry(mode.value) },
                                modifier = Modifier.fillMaxSize(),
                            )
                            FeedPhase.EMPTY -> EmptyBox(emptyText, Modifier.fillMaxSize())
                            FeedPhase.LOADING -> Box(Modifier.fillMaxSize()) { skeleton() }
                        }
                    }
                }
            }
        }
    }
}

/**
 * [items] 含被过滤隐藏项，名次取其在原列表中的位置；条目挂 `animateItem` 实现进榜淡入与重排平滑。
 */
@Composable
private fun <T> RankingPage(
    modeValue: String,
    items: List<T>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    error: String?,
    onRetry: (String) -> Unit,
    onLoadMore: (String) -> Unit,
    emptyText: String,
    filter: ((T) -> Boolean)?,
    filterKey: Any?,
    filteredEmptyText: String?,
    gridMinColumnWidth: Dp?,
    itemContent: @Composable (T, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canContinue = hasMore && !isLoading
    when (feedPhase(loading = isLoading, hasItems = items.isNotEmpty() || canContinue, hasError = error != null)) {
        FeedPhase.LOADING -> LoadingBox(modifier)
        FeedPhase.ERROR -> ErrorBox(
            message = error,
            onRetry = { onRetry(modeValue) },
            modifier = modifier,
        )
        FeedPhase.EMPTY -> EmptyBox(emptyText, modifier)
        FeedPhase.CONTENT -> {
            // 名次取该项在原始 items 中的位置 + 1（过滤只隐藏不匹配项，不重排）。
            // 结果随 (items, filterKey) 记忆：filter 谓词本身不稳定，避免每次重组全量重算
            val visibleIndexed = remember(items, filterKey) {
                items.withIndex().filter { (_, item) ->
                    filter == null || filter(item)
                }.toList()
            }
            if (visibleIndexed.isNotEmpty()) {
                if (gridMinColumnWidth != null) {
                    val gridState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()
                    LazyStaggeredGridPrefetchEffect(
                        state = gridState,
                        itemCount = visibleIndexed.size,
                        hasMore = canContinue,
                        isLoadingMore = isLoadingMore,
                        error = error,
                        onLoadMore = { onLoadMore(modeValue) },
                    )
                    LazyVerticalStaggeredGrid(
                        state = gridState,
                        columns = StaggeredGridCells.Adaptive(gridMinColumnWidth),
                        modifier = modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.xs, bottom = Spacing.xl),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalItemSpacing = Spacing.sm,
                    ) {
                        // key 用原始榜单索引：过滤只隐藏不改序、分页只追加，列表内稳定唯一
                        itemsIndexed(visibleIndexed, key = { _, indexed -> indexed.index }) { _, (index, item) ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                itemContent(item, index + 1)
                            }
                        }
                        if (canContinue) {
                            item(key = "load_more", span = StaggeredGridItemSpan.FullLine) {
                                LoadMoreItem(
                                    isLoadingMore = isLoadingMore,
                                    onLoadMore = { onLoadMore(modeValue) },
                                    hasMore = hasMore,
                                    error = error,
                                )
                            }
                        }
                    }
                } else {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    LazyListPrefetchEffect(
                        state = listState,
                        itemCount = visibleIndexed.size,
                        hasMore = canContinue,
                        isLoadingMore = isLoadingMore,
                        error = error,
                        onLoadMore = { onLoadMore(modeValue) },
                    )
                    LazyColumn(
                        state = listState,
                        modifier = modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.xs, bottom = Spacing.xl),
                    ) {
                        itemsIndexed(visibleIndexed, key = { _, indexed -> indexed.index }) { _, (index, item) ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                itemContent(item, index + 1)
                            }
                        }
                        if (canContinue) {
                            item(key = "load_more") {
                                LoadMoreItem(
                                    isLoadingMore = isLoadingMore,
                                    onLoadMore = { onLoadMore(modeValue) },
                                    hasMore = hasMore,
                                    error = error,
                                )
                            }
                        }
                    }
                }
            } else if (canContinue) {
                // 受上限保护的自动续页入口：跳过连续被过滤的空页
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                ) {
                    item(key = "load_more") {
                        LoadMoreItem(
                            isLoadingMore = isLoadingMore,
                            onLoadMore = { onLoadMore(modeValue) },
                            hasMore = hasMore,
                            error = error,
                            itemCount = items.size,
                            autoContinueWhenEmpty = true,
                        )
                    }
                }
            } else {
                EmptyBox(filteredEmptyText ?: emptyText, modifier)
            }
        }
    }
}
