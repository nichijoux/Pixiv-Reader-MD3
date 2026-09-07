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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.Illust
import com.pixiv.api.model.MangaSeriesItem
import com.pixiv.api.model.Novel
import com.pixiv.api.model.NovelSeriesItem
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.SeriesDetailInfo
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.grid.IllustWaterfallGrid
import com.pixiv.reader.core.ui.component.list.LoadMoreItem
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.card.NovelCard
import com.pixiv.reader.core.ui.component.card.SeriesCard
import com.pixiv.reader.core.ui.component.card.SeriesCardData
import com.pixiv.reader.core.ui.component.card.toCardData
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.user.R

/** 插画 / 漫画分区内容：三态 + 瀑布流（漫画复用同一组件）。 */
@Composable
internal fun SectionIllust(
    paged: PagedState<Illust>,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val items by paged.items.collectAsStateWithLifecycle()
    val isLoading by paged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by paged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by paged.hasMore.collectAsStateWithLifecycle()
    val error by paged.error.collectAsStateWithLifecycle()

    when {
        isLoading && items.isEmpty() -> LoadingBox()
        error != null && items.isEmpty() -> ErrorBox(message = error.orEmpty(), onRetry = onRetry)
        items.isEmpty() -> EmptyBox(stringResource(R.string.user_empty_illust))
        else -> IllustWaterfallGrid(
            illusts = items,
            onItemClick = onOpenIllust,
            onLoadMore = onLoadMore,
            hasMore = hasMore,
            isLoadingMore = isLoadingMore,
            // 沉浸式底部：尾部避开系统导航栏（Scaffold 已不垫内容）
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

/** 小说分区内容：三态 + NovelCard 列表（触底加载）。 */
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
    val items by paged.items.collectAsStateWithLifecycle()
    val isLoading by paged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by paged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by paged.hasMore.collectAsStateWithLifecycle()
    val error by paged.error.collectAsStateWithLifecycle()

    when {
        isLoading && items.isEmpty() -> LoadingBox()
        error != null && items.isEmpty() -> ErrorBox(message = error.orEmpty(), onRetry = onRetry)
        items.isEmpty() -> EmptyBox(stringResource(R.string.user_empty_novel))
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // 沉浸式底部：尾部避开系统导航栏（Scaffold 已不垫内容）
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.xs,
                bottom = Spacing.xl + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.smPlus),
        ) {
            items(items, key = { it.id }) { novel ->
                NovelCard(
                    novel = novel.toCardData(),
                    onClick = { onOpenNovel(novel.id) },
                    onOpenAuthor = { novel.user?.id?.let(onOpenUser) },
                    onToggleFavorite = { fav -> onToggleFavorite(novel.id, fav) },
                    onTagClick = onTagClick,
                    onSeriesClick = { novel.series?.id?.let(onOpenSeries) },
                )
            }
            if (hasMore) {
                item(key = "load_more") {
                    LoadMoreItem(isLoadingMore = isLoadingMore, onLoadMore = onLoadMore)
                }
            }
        }
    }
}

/**
 * 系列分区内容：小说系列 / 漫画系列 二段切换 + SeriesCard 列表（触底加载）。
 * 小说段为既有行为（SeriesDetailInfo 补封面/更新时间）；漫画段复用 SeriesCard，
 * 封面直接取列表项 `cover_image_urls`；段切换为本分区内部状态（rememberSaveable）。
 */
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
    // 二段切换（true = 漫画系列）
    var mangaTab by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        ) {
            SegmentedButton(
                selected = !mangaTab,
                onClick = { mangaTab = false },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.user_series_tab_novel)) },
            )
            SegmentedButton(
                selected = mangaTab,
                onClick = { mangaTab = true },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.user_series_tab_manga)) },
            )
        }
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

/** 小说系列列表（原系列分区内容：三态 + SeriesCard + 触底加载）。 */
@Composable
private fun NovelSeriesList(
    paged: PagedState<NovelSeriesItem>,
    infos: Map<Long, SeriesDetailInfo>,
    onOpenSeries: (Long) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val items by paged.items.collectAsStateWithLifecycle()
    val isLoading by paged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by paged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by paged.hasMore.collectAsStateWithLifecycle()
    val error by paged.error.collectAsStateWithLifecycle()

    when {
        isLoading && items.isEmpty() -> LoadingBox()
        error != null && items.isEmpty() -> ErrorBox(message = error.orEmpty(), onRetry = onRetry)
        items.isEmpty() -> EmptyBox(stringResource(R.string.user_empty_series))
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // 沉浸式底部：尾部避开系统导航栏（Scaffold 已不垫内容）
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.xs,
                bottom = Spacing.xl + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.smPlus),
        ) {
            items(items, key = { it.id }) { series ->
                val info = infos[series.id]
                SeriesCard(
                    data = SeriesCardData(
                        title = series.title.orEmpty(),
                        caption = series.caption,
                        coverUrl = info?.coverUrl,
                        partsCount = series.content_count,
                        totalChars = series.total_character_count,
                        isConcluded = series.is_concluded,
                        // 与追更页一致：作者行显示最近更新时间（最新册发布日期），不显示已追更徽章
                        updatedAt = info?.updatedAt?.take(10),
                        authorName = series.user?.name,
                        authorAvatarUrl = series.user?.profile_image_urls?.best(),
                    ),
                    onClick = { onOpenSeries(series.id) },
                )
            }
            if (hasMore) {
                item(key = "load_more") {
                    LoadMoreItem(isLoadingMore = isLoadingMore, onLoadMore = onLoadMore)
                }
            }
        }
    }
}

/** 漫画系列列表（三态 + SeriesCard 复用：封面取列表项 cover_image_urls，触底加载）。 */
@Composable
private fun MangaSeriesList(
    paged: PagedState<MangaSeriesItem>,
    onOpenMangaSeries: (Long) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val items by paged.items.collectAsStateWithLifecycle()
    val isLoading by paged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by paged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by paged.hasMore.collectAsStateWithLifecycle()
    val error by paged.error.collectAsStateWithLifecycle()

    when {
        isLoading && items.isEmpty() -> LoadingBox()
        error != null && items.isEmpty() -> ErrorBox(message = error.orEmpty(), onRetry = onRetry)
        items.isEmpty() -> EmptyBox(stringResource(R.string.user_empty_manga_series))
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.xs,
                bottom = Spacing.xl + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.smPlus),
        ) {
            items(items, key = { it.id }) { series ->
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
                )
            }
            if (hasMore) {
                item(key = "load_more_manga") {
                    LoadMoreItem(isLoadingMore = isLoadingMore, onLoadMore = onLoadMore)
                }
            }
        }
    }
}

/** 系列卡片已上移至 core:ui（SeriesCard + SeriesCardData），用户页 / 追更列表共用。 */

