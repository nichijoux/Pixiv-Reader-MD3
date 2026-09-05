package com.pixiv.reader.feature.follow.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.component.card.IllustCard
import com.pixiv.reader.core.ui.component.card.NovelCard
import com.pixiv.reader.core.ui.component.card.toCardData
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.SkeletonBlock
import com.pixiv.reader.core.ui.component.feedback.skeletonPulseColor
import com.pixiv.reader.core.ui.component.grid.IllustWaterfallSkeleton
import com.pixiv.reader.core.ui.component.list.LoadMoreItem
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.feature.follow.R
import com.pixiv.reader.feature.follow.data.FollowFeedItem
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.staggeredgrid.items as gridItems

/**
 * 右列单个类型段的动态流。
 *
 * - 手机（[isCompact]）：单列流（LazyColumn，卡片全宽）
 * - 平板：masonry 瀑布流（`StaggeredGridCells.Adaptive(240.dp)`：列宽 ≥240dp 时自动多列，
 *   保证 NovelCard 横版信息区不被挤压——用户 v5 确认的宽度下限）
 * - 触底：列表尾部加载 item 自动触发 [onLoadMore]（混合流交替推进两流下一页）
 *
 * 插画 / 小说卡片完全复用 core:ui 的 [IllustCard] / [NovelCard] 组件。
 */
@Composable
internal fun FollowFeed(
    items: List<FollowFeedItem>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasError: Boolean,
    isCompact: Boolean,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onToggleIllustFavorite: (Long, Boolean) -> Unit,
    onToggleNovelFavorite: (Long, Boolean) -> Unit,
) {
    when {
        isLoading && items.isEmpty() -> if (isCompact) FollowFeedListSkeleton() else IllustWaterfallSkeleton(
            minColumnWidth = 240.dp,
            contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = Spacing.lg),
        )

        hasError && items.isEmpty() -> ErrorBox(
            message = null,
            onRetry = onRetry,
            // 可滚动 → 空态也能触发下拉刷新（否则 PullToRefreshBox 收不到嵌套滚动）
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        )
        items.isEmpty() -> EmptyBox(
            stringResource(R.string.follow_empty),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        )
        else -> if (isCompact) {
            FollowFeedList(
                items = items,
                isLoadingMore = isLoadingMore,
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

/** 手机单列流。 */
@Composable
private fun FollowFeedList(
    items: List<FollowFeedItem>,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onToggleIllustFavorite: (Long, Boolean) -> Unit,
    onToggleNovelFavorite: (Long, Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // 沉浸式底部：尾部避开系统导航栏（手机端 inset 已被壳层消费，补 0）
        contentPadding = PaddingValues(
            start = Spacing.smPlus,
            end = Spacing.smPlus,
            top = Spacing.smPlus,
            bottom = Spacing.lg + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.smPlus),
    ) {
        lazyItems(items, key = { it.key }) { item ->
            FollowFeedItemCard(
                item = item,
                onOpenIllust = onOpenIllust,
                onOpenNovel = onOpenNovel,
                onOpenUser = onOpenUser,
                onOpenSeries = onOpenSeries,
                onToggleIllustFavorite = onToggleIllustFavorite,
                onToggleNovelFavorite = onToggleNovelFavorite,
            )
        }
        item(key = "load_more") {
            LoadMoreItem(isLoadingMore = isLoadingMore, onLoadMore = onLoadMore)
        }
    }
}

/** 平板瀑布流。 */
@Composable
private fun FollowFeedGrid(
    items: List<FollowFeedItem>,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onToggleIllustFavorite: (Long, Boolean) -> Unit,
    onToggleNovelFavorite: (Long, Boolean) -> Unit,
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(240.dp),
        modifier = Modifier.fillMaxSize(),
        // 沉浸式底部：尾部避开系统导航栏（手机端 inset 已被壳层消费，补 0）
        contentPadding = PaddingValues(
            start = Spacing.md,
            end = Spacing.md,
            top = Spacing.md,
            bottom = Spacing.lg + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalItemSpacing = Spacing.sm,
    ) {
        gridItems(items, key = { it.key }) { item ->
            FollowFeedItemCard(
                item = item,
                modifier = Modifier.fillMaxWidth(),
                onOpenIllust = onOpenIllust,
                onOpenNovel = onOpenNovel,
                onOpenUser = onOpenUser,
                onOpenSeries = onOpenSeries,
                onToggleIllustFavorite = onToggleIllustFavorite,
                onToggleNovelFavorite = onToggleNovelFavorite,
            )
        }
        item(span = StaggeredGridItemSpan.FullLine, key = "load_more") {
            LoadMoreItem(isLoadingMore = isLoadingMore, onLoadMore = onLoadMore)
        }
    }
}

/** 手机单列流骨架：封面块 + 标题 2 行 + 作者行（对齐单列卡片布局，脉冲动画）。 */
@Composable
private fun FollowFeedListSkeleton() {
    val color = skeletonPulseColor(label = "followFeedSkeleton")
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // 沉浸式底部：尾部避开系统导航栏（手机端 inset 已被壳层消费，补 0）
        contentPadding = PaddingValues(
            start = Spacing.smPlus,
            end = Spacing.smPlus,
            top = Spacing.smPlus,
            bottom = Spacing.lg + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
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
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
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

/** 单条卡片：插画 → IllustCard，小说 → NovelCard（组件复用）。 */
@Composable
private fun FollowFeedItemCard(
    item: FollowFeedItem,
    modifier: Modifier = Modifier,
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
                onToggleFavorite = { fav -> onToggleIllustFavorite(illust.id, fav) },
                onOpenAuthor = { illust.user?.id?.let(onOpenUser) },
            )
        }

        is FollowFeedItem.NovelItem -> {
            val novel = item.novel
            NovelCard(
                novel = novel.toCardData(),
                onClick = { onOpenNovel(novel.id) },
                // 关注流紧凑排版：封面缩窄（默认 104dp → 88dp）
                coverWidth = 88.dp,
                onOpenAuthor = { novel.user?.id?.let(onOpenUser) },
                onToggleFavorite = { fav -> onToggleNovelFavorite(novel.id, fav) },
                onTagClick = {},
                onSeriesClick = { novel.series?.id?.let(onOpenSeries) },
                modifier = modifier,
            )
        }
    }
}

/** 列表 key（插画 / 小说前缀区分，避免混合流内 id 冲突）。 */
private val FollowFeedItem.key: String
    get() = when (this) {
        is FollowFeedItem.IllustItem -> "i_${illust.id}"
        is FollowFeedItem.NovelItem -> "n_${novel.id}"
    }

