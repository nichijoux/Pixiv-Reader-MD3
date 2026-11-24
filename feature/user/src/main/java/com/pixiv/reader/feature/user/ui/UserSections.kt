package com.pixiv.reader.feature.user.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import com.pixiv.api.model.Illust
import com.pixiv.api.model.MangaSeriesItem
import com.pixiv.api.model.Novel
import com.pixiv.api.model.NovelSeriesItem
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.SeriesDetailInfo
import com.pixiv.reader.core.ui.component.list.PagedFeed
import com.pixiv.reader.core.ui.component.grid.IllustWaterfallGrid
import com.pixiv.reader.core.ui.component.list.LoadMoreItem
import com.pixiv.reader.core.ui.component.list.loadMoreFooter
import com.pixiv.reader.core.ui.component.card.NovelCard
import com.pixiv.reader.core.ui.component.card.SeriesCard
import com.pixiv.reader.core.ui.component.card.SeriesCardData
import com.pixiv.reader.core.ui.component.card.toCardData
import com.pixiv.reader.core.ui.component.input.PillSegmentedRow
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.user.R

@Composable
internal fun SectionIllust(
    paged: PagedState<Illust>,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    PagedFeed(
        paged = paged,
        emptyText = stringResource(R.string.user_empty_illust),
        onRetry = onRetry,
    ) { state ->
        IllustWaterfallGrid(
            illusts = state.items,
            onItemClick = onOpenIllust,
            onLoadMore = onLoadMore,
            hasMore = state.hasMore,
            isLoadingMore = state.isLoadingMore,
            error = state.error,
            // Scaffold 已不垫内容，尾部需自行避开系统导航栏
            contentPadding = PaddingValues(
                start = Spacing.md,
                end = Spacing.md,
                top = Spacing.xs,
                bottom = Spacing.xl + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
            onToggleFavorite = onToggleFavorite,
            onOpenUser = onOpenUser,
        )
    }
}

@Composable
internal fun SectionNovel(
    paged: PagedState<Novel>,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onTagClick: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    // 动效方案在组合期取好：item lambda 不是组合上下文，无法在其中读取 MaterialTheme
    val motionScheme = MaterialTheme.motionScheme
    PagedFeed(
        paged = paged,
        emptyText = stringResource(R.string.user_empty_novel),
        onRetry = onRetry,
    ) { state ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Scaffold 已不垫内容，尾部需自行避开系统导航栏
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.xs,
                bottom = Spacing.xl + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.smPlus),
        ) {
            items(state.items, key = { it.id }) { novel ->
                NovelCard(
                    novel = novel.toCardData(),
                    onClick = { onOpenNovel(novel.id) },
                    onOpenAuthor = { novel.user?.id?.let(onOpenUser) },
                    onToggleFavorite = { fav -> onToggleFavorite(novel.id, fav) },
                    onTagClick = onTagClick,
                    onSeriesClick = { novel.series?.id?.let(onOpenSeries) },
                    modifier = Modifier.animateItem(
                        fadeInSpec = motionScheme.fastEffectsSpec<Float>(),
                        placementSpec = motionScheme.fastSpatialSpec<IntOffset>(),
                    ),
                )
            }
            loadMoreFooter(
                hasMore = state.hasMore,
                isLoadingMore = state.isLoadingMore,
                onLoadMore = onLoadMore,
                error = state.error,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SectionSeries(
    paged: PagedState<NovelSeriesItem>,
    infos: Map<Long, SeriesDetailInfo>,
    mangaPaged: PagedState<MangaSeriesItem>,
    onOpenSeries: (Long) -> Unit,
    onOpenMangaSeries: (Long) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryManga: () -> Unit,
    onLoadMoreManga: () -> Unit,
) {
    var mangaTab by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        PillSegmentedRow(
            options = listOf(
                false to R.string.user_series_tab_novel,
                true to R.string.user_series_tab_manga,
            ),
            selected = mangaTab,
            onSelect = { mangaTab = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
        if (!mangaTab) {
            NovelSeriesList(
                paged = paged,
                infos = infos,
                onOpenSeries = onOpenSeries,
                onRetry = onRetry,
                onLoadMore = onLoadMore,
            )
        } else {
            MangaSeriesList(
                paged = mangaPaged,
                onOpenMangaSeries = onOpenMangaSeries,
                onRetry = onRetryManga,
                onLoadMore = onLoadMoreManga,
            )
        }
    }
}

@Composable
private fun NovelSeriesList(
    paged: PagedState<NovelSeriesItem>,
    infos: Map<Long, SeriesDetailInfo>,
    onOpenSeries: (Long) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    // 动效方案在组合期取好：item lambda 不是组合上下文，无法在其中读取 MaterialTheme
    val motionScheme = MaterialTheme.motionScheme
    PagedFeed(
        paged = paged,
        emptyText = stringResource(R.string.user_empty_series),
        onRetry = onRetry,
    ) { state ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Scaffold 已不垫内容，尾部需自行避开系统导航栏
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.xs,
                bottom = Spacing.xl + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.smPlus),
        ) {
            items(state.items, key = { it.id }) { series ->
                val info = infos[series.id]
                SeriesCard(
                    data = SeriesCardData(
                        title = series.title.orEmpty(),
                        caption = series.caption,
                        coverUrl = info?.coverUrl,
                        partsCount = series.content_count,
                        totalChars = series.total_character_count,
                        isConcluded = series.is_concluded,
                        // 与追更页一致：作者行显示最近更新时间，不显示已追更徽章
                        updatedAt = info?.updatedAt?.take(10),
                        authorName = series.user?.name,
                        authorAvatarUrl = series.user?.profile_image_urls?.best(),
                    ),
                    onClick = { onOpenSeries(series.id) },
                    modifier = Modifier.animateItem(
                        fadeInSpec = motionScheme.fastEffectsSpec<Float>(),
                        placementSpec = motionScheme.fastSpatialSpec<IntOffset>(),
                    ),
                )
            }
            loadMoreFooter(
                hasMore = state.hasMore,
                isLoadingMore = state.isLoadingMore,
                onLoadMore = onLoadMore,
                error = state.error,
            )
        }
    }
}

@Composable
private fun MangaSeriesList(
    paged: PagedState<MangaSeriesItem>,
    onOpenMangaSeries: (Long) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    // 动效方案在组合期取好：item lambda 不是组合上下文，无法在其中读取 MaterialTheme
    val motionScheme = MaterialTheme.motionScheme
    PagedFeed(
        paged = paged,
        emptyText = stringResource(R.string.user_empty_manga_series),
        onRetry = onRetry,
    ) { state ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.xs,
                bottom = Spacing.xl + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.smPlus),
        ) {
            items(state.items, key = { it.id }) { series ->
                SeriesCard(
                    data = SeriesCardData(
                        title = series.title.orEmpty(),
                        caption = series.caption,
                        coverUrl = series.cover_image_urls?.medium
                            ?: series.cover_image_urls?.square_medium,
                        partsCount = series.series_work_count,
                        authorName = series.user?.name,
                        authorAvatarUrl = series.user?.profile_image_urls?.best(),
                    ),
                    onClick = { onOpenMangaSeries(series.id) },
                    modifier = Modifier.animateItem(
                        fadeInSpec = motionScheme.fastEffectsSpec<Float>(),
                        placementSpec = motionScheme.fastSpatialSpec<IntOffset>(),
                    ),
                )
            }
            // 独立 key 与小说系列列表的 footer 区分（历史行为）
            if (state.hasMore) {
                item(key = "load_more_manga") {
                    LoadMoreItem(
                        isLoadingMore = state.isLoadingMore,
                        onLoadMore = onLoadMore,
                        error = state.error,
                    )
                }
            }
        }
    }
}

