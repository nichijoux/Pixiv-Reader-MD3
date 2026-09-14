package com.pixiv.reader.core.ui.component.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 触底加载项（LazyColumn item 尾部）：进入可视区自动触发一次 [onLoadMore]，
 * 加载中显示 56dp 高的 Expressive `LoadingIndicator` 占位（无加载时保持占位高度，避免列表尾部跳动）。
 *
 * 替代此前在 10+ 个列表页复制的 `item(key="load_more"){LaunchedEffect+Box}` 样板。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadMoreItem(
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onLoadMore() }
    Box(
        modifier = modifier.fillMaxWidth().height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoadingMore) {
            LoadingIndicator()
        }
    }
}

/**
 * 触底加载 footer（[LazyListScope] 扩展）：[hasMore] 时在列表尾部追加自动触发的加载项，
 * 收敛各页重复的 `if (hasMore) { item(key = "load_more") { LoadMoreItem(...) } }` 壳。
 *
 * @param hasMore 是否还有下一页；false 不追加任何 item
 * @param isLoadingMore 翻页请求进行中（footer 显示转圈）
 * @param onLoadMore footer 进入可视区时触发一次的翻页回调
 */
fun LazyListScope.loadMoreFooter(
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
) {
    if (hasMore) {
        item(key = "load_more") {
            LoadMoreItem(isLoadingMore = isLoadingMore, onLoadMore = onLoadMore)
        }
    }
}

/**
 * 触底加载 footer（瀑布流 staggered 网格版，语义同 [loadMoreFooter]）。
 *
 * @param hasMore 是否还有下一页；false 不追加任何 item
 * @param isLoadingMore 翻页请求进行中（footer 显示转圈）
 * @param onLoadMore footer 进入可视区时触发一次的翻页回调
 * @param span footer 占用的跨列范围（通栏 footer 传 [StaggeredGridItemSpan.FullLine]）
 */
@OptIn(ExperimentalFoundationApi::class)
fun LazyStaggeredGridScope.loadMoreFooter(
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    span: StaggeredGridItemSpan,
) {
    if (hasMore) {
        item(key = "load_more", span = span) {
            LoadMoreItem(isLoadingMore = isLoadingMore, onLoadMore = onLoadMore)
        }
    }
}
