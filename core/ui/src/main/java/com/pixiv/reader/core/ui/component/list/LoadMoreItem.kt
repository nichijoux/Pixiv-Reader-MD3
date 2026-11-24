package com.pixiv.reader.core.ui.component.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.R

/** 过滤后空页最多连续自动翻页次数，防止全屏蔽列表无限触发请求。 */
private const val MAX_AUTO_CONTINUE_PAGES = 10

/**
 * 列表触底分页项；开启 [autoContinueWhenEmpty] 时仅在 [itemCount] 推进后自动续翻（最多 10 页），
 * 发生 [error] 后立即停止自动请求改为手动重试。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadMoreItem(
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    hasMore: Boolean = true,
    error: String? = null,
    itemCount: Int? = null,
    autoContinueWhenEmpty: Boolean = false,
) {
    val loadMore by rememberUpdatedState(onLoadMore)
    var requested by remember { mutableStateOf(false) }
    var autoPages by remember(autoContinueWhenEmpty) { mutableStateOf(0) }
    var lastAutoItemCount by remember(autoContinueWhenEmpty) { mutableStateOf<Int?>(null) }
    LaunchedEffect(hasMore, isLoadingMore, error, itemCount, autoContinueWhenEmpty) {
        if (!hasMore || isLoadingMore || error != null) return@LaunchedEffect
        if (autoContinueWhenEmpty) {
            if (autoPages >= MAX_AUTO_CONTINUE_PAGES) return@LaunchedEffect
            if (lastAutoItemCount != null && itemCount == lastAutoItemCount) return@LaunchedEffect
            lastAutoItemCount = itemCount
            autoPages += 1
            loadMore()
        } else {
            if (requested) return@LaunchedEffect
            requested = true
            loadMore()
        }
    }
    Box(
        modifier = modifier.fillMaxWidth().height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isLoadingMore -> LoadingIndicator()
            hasMore && error != null -> {
                TextButton(
                    onClick = {
                        autoPages = 0
                        // 将当前条目数记为已手动触发过，避免成功返回 0 条时立即再次自动触发
                        lastAutoItemCount = itemCount
                        onLoadMore()
                    },
                ) {
                    Text(stringResource(R.string.retry))
                }
            }
            hasMore && autoContinueWhenEmpty && autoPages >= MAX_AUTO_CONTINUE_PAGES -> {
                TextButton(
                    onClick = {
                        autoPages = 0
                        lastAutoItemCount = itemCount
                        onLoadMore()
                    },
                ) {
                    Text(stringResource(R.string.load_more))
                }
            }
        }
    }
}

fun LazyListScope.loadMoreFooter(
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    error: String? = null,
) {
    if (hasMore) {
        item(key = "load_more") {
            LoadMoreItem(
                isLoadingMore = isLoadingMore,
                onLoadMore = onLoadMore,
                error = error,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun LazyStaggeredGridScope.loadMoreFooter(
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    span: StaggeredGridItemSpan,
    error: String? = null,
) {
    if (hasMore) {
        item(key = "load_more", span = span) {
            LoadMoreItem(
                isLoadingMore = isLoadingMore,
                onLoadMore = onLoadMore,
                error = error,
            )
        }
    }
}

private const val DEFAULT_LIST_PREFETCH_DISTANCE = 5
private const val DEFAULT_GRID_PREFETCH_DISTANCE = 8

/**
 * 纯判定函数：当前末尾可见索引距列表尾部不足 [prefetchDistance] 项时返回 true，
 * 用于在用户滑到最底撞墙前提前静默拉取下一页数据。
 */
internal fun shouldPrefetchNextPage(
    lastVisibleIndex: Int,
    itemCount: Int,
    prefetchDistance: Int,
    headerCount: Int = 0,
): Boolean {
    if (itemCount <= 0 || lastVisibleIndex < 0) return false
    val lastContentIndex = headerCount + itemCount - 1
    val threshold = (lastContentIndex - prefetchDistance.coerceAtLeast(0)).coerceAtLeast(headerCount)
    return lastVisibleIndex >= threshold
}

/**
 * [LazyColumn] 提前分页触发器：与尾部 [loadMoreFooter] 配合使用，提前 [prefetchDistance] 项静默续页；
 * 按 [itemCount] 单调去重防空页死循环，下拉刷新（条目数回落）时自动复位。
 */
@Composable
fun LazyListPrefetchEffect(
    state: LazyListState,
    itemCount: Int,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    onLoadMore: () -> Unit,
    headerCount: Int = 0,
    prefetchDistance: Int = DEFAULT_LIST_PREFETCH_DISTANCE,
) {
    val latestOnLoadMore by rememberUpdatedState(onLoadMore)
    val shouldLoadMore by remember(state, itemCount, headerCount, prefetchDistance) {
        derivedStateOf {
            val lastVisible = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            shouldPrefetchNextPage(lastVisible, itemCount, prefetchDistance, headerCount)
        }
    }
    var lastTriggeredCount by remember { mutableIntStateOf(-1) }
    LaunchedEffect(shouldLoadMore, hasMore, isLoadingMore, error, itemCount) {
        if (itemCount < lastTriggeredCount) lastTriggeredCount = -1
        if (shouldLoadMore && hasMore && !isLoadingMore && error == null && itemCount > lastTriggeredCount) {
            lastTriggeredCount = itemCount
            latestOnLoadMore()
        }
    }
}

/** [LazyVerticalStaggeredGrid] 提前分页触发器（双列/多列瀑布流默认提前 8 项续页）。 */
@Composable
fun LazyStaggeredGridPrefetchEffect(
    state: LazyStaggeredGridState,
    itemCount: Int,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    onLoadMore: () -> Unit,
    headerCount: Int = 0,
    prefetchDistance: Int = DEFAULT_GRID_PREFETCH_DISTANCE,
) {
    val latestOnLoadMore by rememberUpdatedState(onLoadMore)
    val shouldLoadMore by remember(state, itemCount, headerCount, prefetchDistance) {
        derivedStateOf {
            val lastVisible = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            shouldPrefetchNextPage(lastVisible, itemCount, prefetchDistance, headerCount)
        }
    }
    var lastTriggeredCount by remember { mutableIntStateOf(-1) }
    LaunchedEffect(shouldLoadMore, hasMore, isLoadingMore, error, itemCount) {
        if (itemCount < lastTriggeredCount) lastTriggeredCount = -1
        if (shouldLoadMore && hasMore && !isLoadingMore && error == null && itemCount > lastTriggeredCount) {
            lastTriggeredCount = itemCount
            latestOnLoadMore()
        }
    }
}
