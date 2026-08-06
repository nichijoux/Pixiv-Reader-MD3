package com.pixiv.reader.core.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.common.MAX_CONTENT_WIDTH_DP
import com.pixiv.reader.core.common.RankingModeInfo
import com.pixiv.reader.core.network.paging.PagedState
import kotlinx.coroutines.launch

/**
 * 通用排行榜容器（数据驱动，供漫画/插画/小说排行榜复用）。
 *
 * ## 交互
 * 顶部 `ScrollableTabRow` 分段（[modes] 任意数量）+ `HorizontalPager` 左右滑动切换；
 * 点 Tab `animateScrollToPage` 平滑滑动，滑动切页后回调 [onModeSelect]（触发调用方加载该段）。
 *
 * ## 每段独立分页（消除滑动突兀与状态错配）
 * 各 mode 由调用方提供**独立**的 [PagedState]（[stateFor] 按 mode 值取，实例在 ViewModel 层
 * 缓存、数据驻留 VM，滑动切回/旋转不丢）；HorizontalPager 每页只 collect **自己 mode** 的
 * PagedState——已加载过的段滑动即时显示自己的榜单，未加载段显示加载占位，滑到位加载完成后
 * 以 [AnimatedContent] 淡入，避免"邻页复用当前内容造成滑动闪换"。
 *
 * [AnimatedContent] 的 targetState 使用**该页自身的内容状态**（加载/错误/内容）：首次数据到位
 * （加载→内容）**纯淡入**（骨架占位淡出 + 内容淡入，无位移跳动）；之后滑动离开再切回
 * **已就绪页状态不变 → 不重播过渡**（直接静态显示该页数据），避免"每次切回已有列整页
 * 从下方上滑跳一下"。
 *
 * ## 状态
 * 加载态用**骨架占位** [RankingSkeleton]（仿行布局的灰色占位块 + 呼吸脉冲），错误/空复用
 * `core:ui` StatusViews；错误与触底加载均作用于**该页自己的** PagedState；
 * [PagedState.hasMore] 为 true 时列表尾部自动触发 [onLoadMore]。
 *
 * @param T 榜单条目类型（漫画/插画为 `Illust`，小说为 `Novel`，行渲染由 [itemContent] 决定）
 * @param modes 分段配置（label 资源 + mode 值）
 * @param onModeSelect 滑动/点 Tab 切到某 mode 时回调（调用方在此按需加载该段）
 * @param stateFor 按 mode 值返回该段独立的 [PagedState]（每次需返回同一实例，如 `pages.getOrPut`）
 * @param onRetry 某段加载失败重试（参数为该段 mode 值）
 * @param onLoadMore 某段触底加载下一页（参数为该段 mode 值）
 * @param emptyText 空态文案（调用方传入本地化文案）
 * @param skeleton 加载骨架占位（默认仿 [RankingRow] 布局；小说榜等条目为竖版卡片的调用方可自定义，
 *                 如 `NovelFeedSkeleton`，保证骨架与真实条目布局一致）
 * @param itemContent 条目渲染（参数为 条目 + 排名序号，从 1 开始）；插画/漫画可用 [RankingRow]
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
    skeleton: @Composable () -> Unit = { RankingSkeleton() },
    itemContent: @Composable (T, Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { modes.size.coerceAtLeast(1) },
    )

    // 滑动切页 → 同步模式（切换后由调用方按需加载该段榜单）
    LaunchedEffect(pagerState.currentPage) {
        val page = pagerState.currentPage
        if (page in modes.indices) {
            onModeSelect(modes[page].value)
        }
    }

    // 平板限宽居中：TabRow + 列表整体不超过 MAX_CONTENT_WIDTH_DP（手机 <760 自然占满）
    AdaptiveContentBox(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 限宽生效（平板）→ PrimaryTabRow 均分占满居中；手机 → ScrollableTabRow 内容宽度可滑动
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isWide = maxWidth >= MAX_CONTENT_WIDTH_DP.dp
                val selectedIndex = pagerState.currentPage.coerceIn(0, (modes.size - 1).coerceAtLeast(0))
                if (isWide) {
                    PrimaryTabRow(
                        selectedTabIndex = selectedIndex,
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
                    ScrollableTabRow(
                        selectedTabIndex = selectedIndex,
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
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                val mode = modes.getOrNull(page)
                if (mode == null) {
                    LoadingBox()
                } else {
                    // 该页独立的 PagedState（实例由 ViewModel 缓存，数据驻留 VM）
                    val paged = remember(mode.value) { stateFor(mode.value) }
                    val items by paged.items.collectAsStateWithLifecycle()
                    val isLoading by paged.isLoading.collectAsStateWithLifecycle()
                    val isLoadingMore by paged.isLoadingMore.collectAsStateWithLifecycle()
                    val hasMore by paged.hasMore.collectAsStateWithLifecycle()
                    val error by paged.error.collectAsStateWithLifecycle()
                    val contentState = when {
                        error != null && items.isEmpty() -> RankContentState.Error
                        items.isNotEmpty() -> RankContentState.Content
                        else -> RankContentState.Loading
                    }
                    AnimatedContent(
                        // 用"该页自身内容状态"：已就绪页切回时状态不变 → 不重播过渡；
                        // 首次数据到位（Loading→Content）只淡入（骨架占位淡出 + 内容淡入），无跳动位移
                        targetState = contentState,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(240))
                                .togetherWith(fadeOut(animationSpec = tween(160)))
                        },
                        label = "rankPage",
                    ) { state ->
                        when (state) {
                            RankContentState.Content -> RankingPage(
                                modeValue = mode.value,
                                items = items,
                                isLoading = isLoading,
                                isLoadingMore = isLoadingMore,
                                hasMore = hasMore,
                                error = error,
                                onRetry = onRetry,
                                onLoadMore = onLoadMore,
                                emptyText = emptyText,
                                itemContent = itemContent,
                            )
                            RankContentState.Error -> ErrorBox(
                                message = error,
                                onRetry = { onRetry(mode.value) },
                            )
                            RankContentState.Loading -> skeleton()
                        }
                    }
                }
            }
        }
    }
}

/** 排行榜页内容三态：加载占位 / 错误 / 有数据。作为 [AnimatedContent] 的 targetState。 */
private enum class RankContentState { Loading, Error, Content }

/**
 * 排行榜加载骨架占位：仿 [RankingRow] 布局（28dp 序号位 + 88dp 封面 + 标题/作者/收藏文本条）
 * 渲染 9 行灰色圆角占位块，呼吸 alpha 脉冲，替代全屏转圈——数据到位后淡入真实列表。
 */
@Composable
private fun RankingSkeleton() {
    val color = skeletonPulseColor(label = "rankingSkeleton")
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(9) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(AppShapes.small)
                        .background(color),
                )
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(AppShapes.card)
                        .background(color),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(color),
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = Spacing.sm)
                            .fillMaxWidth(0.65f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(color),
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = Spacing.sm)
                            .width(64.dp)
                            .height(18.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(color),
                    )
                }
            }
        }
    }
}

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
    itemContent: @Composable (T, Int) -> Unit,
) {
    when {
        isLoading && items.isEmpty() -> LoadingBox()
        error != null && items.isEmpty() -> ErrorBox(message = error, onRetry = { onRetry(modeValue) })
        items.isEmpty() -> EmptyBox(emptyText)
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.xs, bottom = Spacing.xl),
        ) {
            itemsIndexed(items) { index, item ->
                itemContent(item, index + 1)
            }
            if (hasMore) {
                item(key = "load_more") {
                    LaunchedEffect(Unit) { onLoadMore(modeValue) }
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
