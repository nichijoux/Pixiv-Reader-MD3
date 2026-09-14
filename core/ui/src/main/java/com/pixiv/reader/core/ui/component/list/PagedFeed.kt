package com.pixiv.reader.core.ui.component.list

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.FeedPhase
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.feedback.feedPhase

/**
 * [PagedState] 五流聚合快照：一次 collect 替代各页重复的五行
 * `xxx.collectAsStateWithLifecycle()` 样板。
 *
 * @param T 条目类型
 * @property items 当前已加载条目
 * @property isLoading 首载 / 下拉刷新进行中
 * @property isLoadingMore 触底翻页进行中
 * @property hasMore 是否还有下一页
 * @property error 最近一次加载失败的文案；null = 无错误
 */
data class PagedUiState<T>(
    val items: List<T>,
    val isLoading: Boolean,
    val isLoadingMore: Boolean,
    val hasMore: Boolean,
    val error: String?,
)

/**
 * [PagedState] 的 Compose 聚合订阅：items/isLoading/isLoadingMore/hasMore/error 五个流
 * 收成单个 [PagedUiState]。
 *
 * @param T 条目类型
 * @return 生命周期感知的聚合快照（任一成员流变化即重组）
 */
@Composable
fun <T> PagedState<T>.collectAsPagedUiState(): PagedUiState<T> {
    val items by items.collectAsStateWithLifecycle()
    val isLoading by isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by hasMore.collectAsStateWithLifecycle()
    val error by error.collectAsStateWithLifecycle()
    return PagedUiState(items, isLoading, isLoadingMore, hasMore, error)
}

/**
 * 分页内容三态门槛（[feedPhase] 权威判定）：首载无内容 → [loadingContent]；失败无内容 →
 * [ErrorBox]（[onRetry] 重试）；空态 → [EmptyBox]（[emptyText]）；有内容 → [content]
 * （聚合快照交给调用方渲染列表，触底加载用 `state.hasMore/isLoadingMore` 配
 * `loadMoreFooter`）。
 *
 * 收敛各列表页重复的「五流 collect + 手写三态 when」样板，并统一走 [feedPhase] 判定，
 * 消除分支顺序写错的隐患（error 先于 loading 判定会吞掉首载骨架）。
 *
 * @param T 条目类型
 * @param paged 分页状态（通常来自 ViewModel）
 * @param emptyText 空态文案
 * @param onRetry 失败重试回调
 * @param modifier 三态容器 Modifier
 * @param loadingContent 首载占位（默认 [LoadingBox]；骨架页传自定义骨架）
 * @param content 有内容时的渲染（参数为聚合快照）
 * @return 无返回值
 */
@Composable
fun <T> PagedFeed(
    paged: PagedState<T>,
    emptyText: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    loadingContent: @Composable () -> Unit = { LoadingBox() },
    content: @Composable (state: PagedUiState<T>) -> Unit,
) {
    val state = paged.collectAsPagedUiState()
    when (feedPhase(loading = state.isLoading, hasItems = state.items.isNotEmpty(), hasError = state.error != null)) {
        FeedPhase.LOADING -> Box(modifier) { loadingContent() }
        FeedPhase.ERROR -> Box(modifier) { ErrorBox(message = state.error, onRetry = onRetry) }
        FeedPhase.EMPTY -> Box(modifier) { EmptyBox(emptyText) }
        FeedPhase.CONTENT -> content(state)
    }
}
