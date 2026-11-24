package com.pixiv.reader.feature.follow.ui

import androidx.compose.foundation.background
import com.pixiv.reader.core.common.ui.WindowSizeClass
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.component.card.IllustCard
import com.pixiv.reader.core.ui.component.card.NovelCard
import com.pixiv.reader.core.ui.component.card.toCardData
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.FeedPhase
import com.pixiv.reader.core.ui.component.feedback.feedPhase
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.SkeletonBlock
import com.pixiv.reader.core.ui.component.feedback.skeletonPulseColor
import com.pixiv.reader.core.ui.component.grid.IllustWaterfallSkeleton
import com.pixiv.reader.core.ui.component.layout.LocalNavBarBottomPadding
import com.pixiv.reader.core.ui.component.list.loadMoreFooter
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.feature.follow.R
import com.pixiv.reader.feature.follow.data.FollowFeedItem
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.staggeredgrid.items as gridItems

/**
 * 右列单个类型段：手机单列流，平板瀑布流（列宽 <240dp 会挤压 NovelCard 横版信息区，故取 240dp 下限）。
 */
@Composable
internal fun FollowFeed(
    items: List<FollowFeedItem>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    hasError: Boolean,
    windowClass: WindowSizeClass,
    scrollToTopKey: Int = 0,
    onSearchTag: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onToggleIllustFavorite: (Long, Boolean) -> Unit,
    onToggleNovelFavorite: (Long, Boolean) -> Unit,
) {
    val isCompact = windowClass == WindowSizeClass.Compact
    when (feedPhase(loading = isLoading, hasItems = items.isNotEmpty(), hasError = hasError)) {
        FeedPhase.LOADING -> if (isCompact) FollowFeedListSkeleton() else IllustWaterfallSkeleton(
            minColumnWidth = 240.dp,
            contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = Spacing.lg),
        )

        FeedPhase.ERROR -> ErrorBox(
            message = null,
            onRetry = onRetry,
            // 可滚动 → 空态也能触发下拉刷新（否则 PullToRefreshBox 收不到嵌套滚动）
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        )
        FeedPhase.EMPTY -> EmptyBox(
            stringResource(R.string.follow_empty),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        )
        FeedPhase.CONTENT -> if (isCompact) {
            FollowFeedList(
                items = items,
                isLoadingMore = isLoadingMore,
                hasMore = hasMore,
                loadMoreError = if (hasError) "" else null,
                scrollToTopKey = scrollToTopKey,
                onSearchTag = onSearchTag,
                onLoadMore = onLoadMore,
                onOpenIllust = onOpenIllust,
                onOpenNovel = onOpenNovel,
                onOpenUser = onOpenUser,
                onOpenSeries = onOpenSeries,
                onToggleIllustFavorite = onToggleIllustFavorite,
                onToggleNovelFavorite = onToggleNovelFavorite,
            )
        } else {
            FollowFeedGrid(
                items = items,
                isLoadingMore = isLoadingMore,
                hasMore = hasMore,
                loadMoreError = if (hasError) "" else null,
                scrollToTopKey = scrollToTopKey,
                onSearchTag = onSearchTag,
                onLoadMore = onLoadMore,
                onOpenIllust = onOpenIllust,
                onOpenNovel = onOpenNovel,
                onOpenUser = onOpenUser,
                onOpenSeries = onOpenSeries,
                onToggleIllustFavorite = onToggleIllustFavorite,
                onToggleNovelFavorite = onToggleNovelFavorite,
            )
        }
    }
}

@Composable
private fun FollowFeedList(
    items: List<FollowFeedItem>,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    loadMoreError: String?,
    scrollToTopKey: Int = 0,
    onSearchTag: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onToggleIllustFavorite: (Long, Boolean) -> Unit,
    onToggleNovelFavorite: (Long, Boolean) -> Unit,
) {
    // 动效方案在组合期取好：item lambda 不是组合上下文，无法在其中读取 MaterialTheme
    val motionScheme = MaterialTheme.motionScheme
    val listState = rememberLazyListState()
    // 回顶信号：首组合只记录 key 不触发；key 变化（当前 tab 被再次点击）时滚回首项
    var lastScrollKey by remember { mutableIntStateOf(scrollToTopKey) }
    LaunchedEffect(scrollToTopKey) {
        if (scrollToTopKey != lastScrollKey) {
            lastScrollKey = scrollToTopKey
            listState.animateScrollToItem(0)
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // 沉浸式底部：尾部额外避开系统导航栏（手机端 inset 已被壳层消费，补 0）
        contentPadding = PaddingValues(
            start = Spacing.smPlus,
            end = Spacing.smPlus,
            top = Spacing.smPlus,
            bottom = Spacing.lg + LocalNavBarBottomPadding.current + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.smPlus),
    ) {
        lazyItems(items, key = { it.key }) { item ->
            FollowFeedItemCard(
                item = item,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(
                        fadeInSpec = motionScheme.fastEffectsSpec<Float>(),
                        placementSpec = motionScheme.fastSpatialSpec<IntOffset>(),
                    ),
                onSearchTag = onSearchTag,
                onOpenIllust = onOpenIllust,
                onOpenNovel = onOpenNovel,
                onOpenUser = onOpenUser,
                onOpenSeries = onOpenSeries,
                onToggleIllustFavorite = onToggleIllustFavorite,
                onToggleNovelFavorite = onToggleNovelFavorite,
            )
        }
        // 只有当前类型段仍可续页时才挂载触底项，出错时停用自动续载交由用户手动重试。
        loadMoreFooter(
            hasMore = hasMore,
            isLoadingMore = isLoadingMore,
            onLoadMore = onLoadMore,
            error = loadMoreError,
        )
    }
}

/** 平板端瀑布流动态列表。 */
@Composable
private fun FollowFeedGrid(
    items: List<FollowFeedItem>,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    loadMoreError: String?,
    scrollToTopKey: Int = 0,
    onSearchTag: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onToggleIllustFavorite: (Long, Boolean) -> Unit,
    onToggleNovelFavorite: (Long, Boolean) -> Unit,
) {
    val gridState = rememberLazyStaggeredGridState()
    // 动效方案在组合期取好：item lambda 不是组合上下文，无法在其中读取 MaterialTheme
    val motionScheme = MaterialTheme.motionScheme
    // 回顶信号：首组合只记录 key 不触发；key 变化（当前 tab 被再次点击）时滚回首项
    var lastScrollKey by remember { mutableIntStateOf(scrollToTopKey) }
    LaunchedEffect(scrollToTopKey) {
        if (scrollToTopKey != lastScrollKey) {
            lastScrollKey = scrollToTopKey
            gridState.animateScrollToItem(0)
        }
    }
    LazyVerticalStaggeredGrid(
        state = gridState,
        columns = StaggeredGridCells.Adaptive(240.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.md,
            end = Spacing.md,
            top = Spacing.md,
            bottom = Spacing.lg + LocalNavBarBottomPadding.current + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalItemSpacing = Spacing.sm,
    ) {
        gridItems(items, key = { it.key }) { item ->
            FollowFeedItemCard(
                item = item,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(
                        fadeInSpec = motionScheme.fastEffectsSpec<Float>(),
                        placementSpec = motionScheme.fastSpatialSpec<IntOffset>(),
                    ),
                onSearchTag = onSearchTag,
                onOpenIllust = onOpenIllust,
                onOpenNovel = onOpenNovel,
                onOpenUser = onOpenUser,
                onOpenSeries = onOpenSeries,
                onToggleIllustFavorite = onToggleIllustFavorite,
                onToggleNovelFavorite = onToggleNovelFavorite,
            )
        }
        // 只有当前类型段仍可续页时才挂载通栏触底项，出错时停用自动续载交由用户手动重试。
        loadMoreFooter(
            hasMore = hasMore,
            isLoadingMore = isLoadingMore,
            onLoadMore = onLoadMore,
            span = StaggeredGridItemSpan.FullLine,
            error = loadMoreError,
        )
    }
}

/** 手机单列流骨架：封面块 + 标题 2 行 + 作者行（对齐单列卡片布局）。 */
@Composable
private fun FollowFeedListSkeleton() {
    val color = skeletonPulseColor(label = "followFeedSkeleton")
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.smPlus,
            end = Spacing.smPlus,
            top = Spacing.smPlus,
            bottom = Spacing.lg + LocalNavBarBottomPadding.current + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.smPlus),
    ) {
        items(count = 3) {
            Column(
                modifier = Modifier
                    .clip(AppShapes.cardLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(AppShapes.cardLargeTop),
                    color = color,
                )
                Column(modifier = Modifier.padding(Spacing.smPlus)) {
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(14.dp)
                            .clip(AppShapes.tiny),
                        color = color,
                    )
                    SkeletonBlock(
                        modifier = Modifier
                            .padding(top = Spacing.sm)
                            .fillMaxWidth(0.55f)
                            .height(12.dp)
                            .clip(AppShapes.tiny),
                        color = color,
                    )
                    Row(
                        modifier = Modifier.padding(top = Spacing.smPlus),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SkeletonBlock(
                            modifier = Modifier
                                .size(Sizes.s20)
                                .clip(CircleShape),
                            color = color,
                        )
                        SkeletonBlock(
                            modifier = Modifier
                                .padding(start = Spacing.xsPlus)
                                .width(80.dp)
                                .height(10.dp)
                                .clip(AppShapes.tiny),
                            color = color,
                        )
                    }
                }
            }
        }
    }
}

/** 插画 → IllustCard，小说 → NovelCard。 */
@Composable
private fun FollowFeedItemCard(
    item: FollowFeedItem,
    modifier: Modifier = Modifier,
    onSearchTag: (String) -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onToggleIllustFavorite: (Long, Boolean) -> Unit,
    onToggleNovelFavorite: (Long, Boolean) -> Unit,
) {
    when (item) {
        is FollowFeedItem.IllustItem -> {
            val illust = item.illust
            IllustCard(
                illust = illust,
                onClick = { onOpenIllust(illust.id) },
                modifier = modifier,
                onTagClick = onSearchTag,
                onToggleFavorite = { fav -> onToggleIllustFavorite(illust.id, fav) },
                onOpenAuthor = { illust.user?.id?.let(onOpenUser) },
            )
        }

        is FollowFeedItem.NovelItem -> {
            val novel = item.novel
            NovelCard(
                novel = novel.toCardData(),
                onClick = { onOpenNovel(novel.id) },
                coverWidth = 88.dp,
                onOpenAuthor = { novel.user?.id?.let(onOpenUser) },
                onToggleFavorite = { fav -> onToggleNovelFavorite(novel.id, fav) },
                onTagClick = onSearchTag,
                onSeriesClick = { novel.series?.id?.let(onOpenSeries) },
                modifier = modifier,
            )
        }
    }
}

/** 插画/小说前缀区分：混合流内同 id 不会撞 key。 */
private val FollowFeedItem.key: String
    get() = when (this) {
        is FollowFeedItem.IllustItem -> "i_${illust.id}"
        is FollowFeedItem.NovelItem -> "n_${novel.id}"
    }

