package com.pixiv.reader.core.ui.component.list

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.common.ui.RankingModeInfo
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.FeedPhase
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.feedback.feedPhase
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.theme.Spacing
import kotlinx.coroutines.launch

/** 漫画/插画排行榜瀑布流最小列宽：[RankingList] 与 [RankingIllustSkeleton] 必须同值，保证骨架列数与真实列表一致。 */
val RANKING_GRID_MIN_COLUMN_WIDTH = 200.dp

/** 段选择行居中阈值：段数 ≤ 此值用 PrimaryTabRow 均分占满（页签居中），超过改用可滑动页签。 */
private const val CENTERED_TAB_MAX = 4

/**
 * 通用排行榜容器（数据驱动，供漫画/插画/小说排行榜复用）。
 *
 * ## 交互
 * 顶部段选择行按段数自适应（≤[CENTERED_TAB_MAX] 段 `PrimaryTabRow` 均分占满、页签居中；
 * 更多段 `PrimaryScrollableTabRow` 内容宽度可滑动）+ `HorizontalPager` 左右滑动切换；
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
 * 加载态用**骨架占位**（调用方经 [skeleton] 传入与真实条目布局一致的骨架，如漫画/插画榜用
 * `RankingIllustSkeleton`、小说榜用 `NovelFeedSkeleton`），错误/空复用 `core:ui` StatusViews；
 * 错误与触底加载均作用于**该页自己的** PagedState；
 * [PagedState.hasMore] 为 true 时列表尾部自动触发 [onLoadMore]。
 *
 * @param T 榜单条目类型（漫画/插画为 `Illust`，小说为 `Novel`，行渲染由 [itemContent] 决定）
 * @param modes 分段配置（label 资源 + mode 值）
 * @param onModeSelect 滑动/点 Tab 切到某 mode 时回调（调用方在此按需加载该段）
 * @param stateFor 按 mode 值返回该段独立的 [PagedState]（每次需返回同一实例，如 `pages.getOrPut`）
 * @param onRetry 某段加载失败重试（参数为该段 mode 值）
 * @param onLoadMore 某段触底加载下一页（参数为该段 mode 值）
 * @param emptyText 空态文案（调用方传入本地化文案）
 * @param filter 条目过滤谓词（null = 不过滤）。命中项保留其在原始榜单中的**真实名次**（过滤只隐藏
 *               不匹配项，不重排）；过滤后本页无匹配但榜单还有下一页时自动续载，直到出现匹配或耗尽
 * @param filteredEmptyText 过滤后无任何命中且榜单耗尽时的空态文案（null 时回落 [emptyText]）
 * @param skeleton 加载骨架占位（调用方应传入与 itemContent 布局一致的骨架；默认空占位，需调用方指定，
 *                 如漫画/插画榜 `RankingIllustSkeleton`、小说榜 `NovelFeedSkeleton`）
 * @param gridMinColumnWidth 条目瀑布流自适应列宽（dp）。null = 单列列表（小说等大卡）；
 *                           非 null 时条目按 `StaggeredGridCells.Adaptive` 瀑布流分列
 *                           （宽屏 2-3 列，pane 让位/小屏自动减少列数；卡片宽高比各异，
 *                           各列独立流动不按行对齐），排名顺序不变、尾部加载项横跨整行
 * @param stateKey 段状态键（如当前日期筛选）：变更时各页经 [stateFor] 取到新维度的 [PagedState]
 *                 实例（页级 remember 键含 stateKey），并重新触发当前段 [onModeSelect] 加载；
 *                 默认空串 = 无额外维度（Tab 选中位不受影响，各段旧数据仍在 VM 缓存）
 * @param listHeader 列表头（渲染在 TabRow 上方、限宽内容块内，如日期筛选 chip 行）；null = 无。
 *                   平板 pane 让位时随列表整体移动，与 TabRow/列表左缘对齐
 * @param itemContent 条目渲染（参数为 条目 + 排名序号，从 1 开始）；漫画/插画可用 `IllustCard(rank = …)`
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

    // 滑动切页 → 同步模式（切换后由调用方按需加载该段榜单）
    LaunchedEffect(pagerState.currentPage) {
        val page = pagerState.currentPage
        if (page in modes.indices) {
            onModeSelect(modes[page].value)
        }
    }

    // stateKey 变更（如切换日期）→ 当前段换用新的 PagedState，需重新触发当前 mode 加载
    // （pagerState.currentPage 未变，上面的 LaunchedEffect 不会重跑；首次组合时会与上面
    // 重复回调一次 onPageSelected，由 ViewModel 侧 initialized 集合保证幂等）
    LaunchedEffect(stateKey) {
        val page = pagerState.currentPage
        if (page in modes.indices) {
            onModeSelect(modes[page].value)
        }
    }

    // 平板限宽居中：段选择行 + 列表整体经 AdaptiveContentBox 不超过 MAX_CONTENT_WIDTH_DP（手机自然占满）
    AdaptiveContentBox(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 列表头（如日期筛选 chip 行）：位于 TabRow 上方、限宽内容块内，随 pane 让位整体移动
            listHeader?.invoke()
            // 段选择按段数自适应：段少（≤4，如发现页 AI/年代/壁纸榜）用 PrimaryTabRow 均分
            // 占满整行（页签视觉居中，不出现左侧拥挤）；段多（主榜单 5-7 段）用
            // PrimaryScrollableTabRow（内容宽度可横向滑动）
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
                    // 该页独立的 PagedState（实例由 ViewModel 缓存，数据驻留 VM；
                    // stateKey（日期维度）变更时按新复合键取到新实例并重新加载）
                    val paged = remember(mode.value, stateKey) { stateFor(mode.value) }
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
                    // 三态切换淡入淡出：Expressive effects 弹簧（transitionSpec 非组合上下文，
                    // 规格在组合期先取好再闭包捕获）
                    val rankFade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                    AnimatedContent(
                        // 用"该页自身内容状态"：已就绪页切回时状态不变 → 不重播过渡；
                        // 首次数据到位（Loading→Content）只淡入（骨架占位淡出 + 内容淡入），无跳动位移
                        targetState = contentState,
                        transitionSpec = {
                            fadeIn(animationSpec = rankFade)
                                .togetherWith(fadeOut(animationSpec = rankFade))
                        },
                        label = "rankPage",
                        // Compose 1.7 AnimatedContent 内部 SharedTransitionScope 要求 content 应用传入
                        // 的 modifier，否则 lookahead pass 拿不到根坐标 → 冷启动布局崩
                        // "Uninitialized LayoutCoordinates"（首次运行正常、二次打开必现）
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
                                filter = filter,
                                filteredEmptyText = filteredEmptyText,
                                gridMinColumnWidth = gridMinColumnWidth,
                                itemContent = itemContent,
                                modifier = Modifier.fillMaxSize(),
                            )
                            RankContentState.Error -> ErrorBox(
                                message = error,
                                onRetry = { onRetry(mode.value) },
                                modifier = Modifier.fillMaxSize(),
                            )
                            RankContentState.Loading -> Box(Modifier.fillMaxSize()) { skeleton() }
                        }
                    }
                }
            }
        }
    }
}

/** 排行榜页内容三态：加载占位 / 错误 / 有数据。作为 [AnimatedContent] 的 targetState。 */
private enum class RankContentState { Loading, Error, Content }

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
    filteredEmptyText: String?,
    gridMinColumnWidth: Dp?,
    itemContent: @Composable (T, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (feedPhase(loading = isLoading, hasItems = items.isNotEmpty(), hasError = error != null)) {
        FeedPhase.LOADING -> LoadingBox(modifier)
        FeedPhase.ERROR -> ErrorBox(
            message = error,
            onRetry = { onRetry(modeValue) },
            modifier = modifier,
        )
        FeedPhase.EMPTY -> EmptyBox(emptyText, modifier)
        FeedPhase.CONTENT -> {
            // 保留真实排名：rank 取该项在原始 items 中的位置 + 1（即榜单真实名次）。
            // 过滤只隐藏不匹配项，不改变其余项排名——封面左上角显示的是其在榜单中的真实名次。
            val visibleIndexed = items.withIndex().filter { (_, item) ->
                filter == null || filter(item)
            }
            if (visibleIndexed.isNotEmpty()) {
                // 网格模式（gridMinColumnWidth 非 null，漫画/插画榜）：瀑布流自适应分列 2-3 列
                // （卡片宽高比各异，各列独立流动不按行对齐）；否则单列列表（小说榜大卡）
                if (gridMinColumnWidth != null) {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Adaptive(gridMinColumnWidth),
                        modifier = modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.xs, bottom = Spacing.xl),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalItemSpacing = Spacing.sm,
                    ) {
                        itemsIndexed(visibleIndexed) { _, (index, item) ->
                            itemContent(item, index + 1)
                        }
                        if (hasMore) {
                            // 尾部加载项横跨整行（不占用条目列）
                            item(key = "load_more", span = StaggeredGridItemSpan.FullLine) {
                                LoadMoreItem(
                                    isLoadingMore = isLoadingMore,
                                    onLoadMore = { onLoadMore(modeValue) },
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.xs, bottom = Spacing.xl),
                    ) {
                        itemsIndexed(visibleIndexed) { _, (index, item) ->
                            itemContent(item, index + 1)
                        }
                        if (hasMore) {
                            item(key = "load_more") {
                                LoadMoreItem(
                                    isLoadingMore = isLoadingMore,
                                    onLoadMore = { onLoadMore(modeValue) },
                                )
                            }
                        }
                    }
                }
            } else if (hasMore) {
                // 「保证展示全部」：过滤后本页无匹配但还有下一页 → 渲染 LoadMoreItem，
                // 其 LaunchedEffect(Unit) 可见即自动续载下一页，直到出现匹配项或榜单耗尽
                // （hasMore=false）。耗尽后仍有匹配则展示匹配全集，皆空才落空态。
                LazyColumn(
                    modifier = modifier.fillMaxSize(),
                ) {
                    item(key = "load_more") {
                        LoadMoreItem(
                            isLoadingMore = isLoadingMore,
                            onLoadMore = { onLoadMore(modeValue) },
                        )
                    }
                }
            } else {
                EmptyBox(filteredEmptyText ?: emptyText, modifier)
            }
        }
    }
}
