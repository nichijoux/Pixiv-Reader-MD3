package com.pixiv.reader.core.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.common.RankingModeInfo
import kotlinx.coroutines.launch

/**
 * 通用排行榜容器（数据驱动，供漫画/插画/小说排行榜复用）。
 *
 * ## 交互
 * 顶部 `ScrollableTabRow` 分段（[modes] 任意数量）+ `HorizontalPager` 左右滑动切换；
 * 点 Tab `animateScrollToPage` 平滑滑动，滑动切页后回调 [onModeSelect]（触发调用方按新 mode 重载数据）。
 *
 * ## 每页独立数据快照（消除滑动突兀）
 * [dataKey] 为"数据就绪标识"（调用方在 `loadInitial` 完成后递增）：变化时把当前 [items]
 * 按 [selectedValue] 缓存进内部快照；HorizontalPager 每页只渲染**自己 mode** 的快照——
 * 已加载过的段滑动即时显示自己的榜单，未加载段显示加载占位，滑到位加载完成后以
 * [AnimatedContent] 淡入，避免"邻页复用当前内容造成滑动闪换"。
 *
 * ## 状态
 * 三态（加载/错误/空）复用 `core:ui` StatusViews；错误与触底加载仅作用于当前 mode 页；
 * [hasMore] 时列表尾部自动触发 [onLoadMore]。
 *
 * @param T 榜单条目类型（漫画/插画为 `Illust`，小说为 `Novel`，行渲染由 [itemContent] 决定）
 * @param modes 分段配置（label 资源 + mode 值）
 * @param selectedValue 当前选中的 mode 值（由调用方状态驱动）
 * @param onModeSelect 滑动/点 Tab 切到某 mode 时回调
 * @param items 当前榜单列表（按 [selectedValue] 对应的数据）
 * @param dataKey 数据就绪标识：每次 `loadInitial` 完成后递增，触发快照缓存与淡入过渡
 * @param emptyText 空态文案（调用方传入本地化文案）
 * @param itemContent 条目渲染（参数为 条目 + 排名序号，从 1 开始）；插画/漫画可用 [RankingRow]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> RankingList(
    modes: List<RankingModeInfo>,
    selectedValue: String,
    onModeSelect: (String) -> Unit,
    items: List<T>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    emptyText: String,
    dataKey: Any = Unit,
    itemContent: @Composable (T, Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val initialIndex = modes.indexOfFirst { it.value == selectedValue }.coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { modes.size.coerceAtLeast(1) },
    )

    // 每段数据快照：dataKey 变化（数据就绪）时把当前 items 缓存到对应段，供各页独立渲染
    val snapshots = remember { mutableStateMapOf<String, List<T>>() }
    LaunchedEffect(dataKey) {
        snapshots[selectedValue] = items
    }

    // 滑动切页 → 同步模式（切换后由调用方重载对应榜单）
    LaunchedEffect(pagerState.currentPage) {
        val page = pagerState.currentPage
        if (page in modes.indices) {
            onModeSelect(modes[page].value)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage.coerceIn(0, (modes.size - 1).coerceAtLeast(0)),
            edgePadding = 8.dp,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            modes.forEachIndexed { index, mode ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(stringResource(mode.labelRes)) },
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            val mode = modes.getOrNull(page)
            val snapshot = mode?.let { snapshots[it.value] }
            if (mode == null || snapshot == null) {
                // 该段尚未加载过：加载占位（滑动到位后 onModeSelect 触发加载）
                LoadingBox()
            } else {
                val isCurrent = mode.value == selectedValue
                AnimatedContent(
                    targetState = dataKey,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(240)) +
                            slideInVertically(animationSpec = tween(240)) { it / 10 })
                            .togetherWith(fadeOut(animationSpec = tween(160)))
                    },
                    label = "rankPage",
                ) {
                    RankingPage(
                        items = snapshot,
                        isLoading = isLoading && isCurrent,
                        isLoadingMore = isLoadingMore,
                        hasMore = hasMore && isCurrent,
                        error = if (isCurrent) error else null,
                        onRetry = onRetry,
                        onLoadMore = onLoadMore,
                        emptyText = emptyText,
                        itemContent = itemContent,
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> RankingPage(
    items: List<T>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    emptyText: String,
    itemContent: @Composable (T, Int) -> Unit,
) {
    when {
        isLoading && items.isEmpty() -> LoadingBox()
        error != null && items.isEmpty() -> ErrorBox(message = error, onRetry = onRetry)
        items.isEmpty() -> EmptyBox(emptyText)
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        ) {
            itemsIndexed(items) { index, item ->
                itemContent(item, index + 1)
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
}