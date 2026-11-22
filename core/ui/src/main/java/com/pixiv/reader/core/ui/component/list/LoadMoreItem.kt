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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.R

/** 见底空白时最多自动连续翻页跳过空页的次数，防止全屏蔽列表无限触发请求。 */
private const val MAX_AUTO_CONTINUE_PAGES = 10

/**
 * 列表触底分页底部组件。
 *
 * 常规列表在首次进入视口时触发一次下一页请求；当可见列表因过滤完全为空且开启
 * [autoContinueWhenEmpty] 时，仅在底层条目数推进后继续自动翻页，最多连续自动翻页
 * 10 页。发生错误后立即停止自动请求，由用户点击按钮手动重试。
 *
 * @param isLoadingMore 是否正在加载下一页
 * @param onLoadMore 触发加载下一页的回调
 * @param modifier 修饰符
 * @param hasMore 是否还有下一页
 * @param error 最近一次分页错误信息；非空时不自动重试，仅展示手动重试按钮
 * @param itemCount 底层总条目数，仅在 [autoContinueWhenEmpty] 为 true 时用于检测翻页进度
 * @param autoContinueWhenEmpty 可见结果为空时是否允许有限次自动连续翻页跳过空页
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
        // 终态、加载中或出错时均不再自动请求，防止无休止重试或空转。
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
                        // 将当前条目数记为已手动触发过，避免若成功返回 0 条时立即再次自动触发。
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

/**
 * 触底加载 footer（[LazyListScope] 扩展）：[hasMore] 时在列表尾部追加自动触发的加载项，
 * 收敛各页重复的 `if (hasMore) { item(key = "load_more") { LoadMoreItem(...) } }` 壳。
 *
 * @param hasMore 是否还有下一页；false 不追加任何 item
 * @param isLoadingMore 翻页请求进行中（footer 显示转圈）
 * @param onLoadMore footer 进入可视区时触发一次的翻页回调
 * @param error 分页错误信息；非空时显示手动重试按钮
 */
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

/**
 * 触底加载 footer（瀑布流 staggered 网格版，语义同 [loadMoreFooter]）。
 *
 * @param hasMore 是否还有下一页；false 不追加任何 item
 * @param isLoadingMore 翻页请求进行中（footer 显示转圈）
 * @param onLoadMore footer 进入可视区时触发一次的翻页回调
 * @param span footer 占用的跨列范围（通栏 footer 传 [StaggeredGridItemSpan.FullLine]）
 * @param error 分页错误信息；非空时显示手动重试按钮
 */
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
