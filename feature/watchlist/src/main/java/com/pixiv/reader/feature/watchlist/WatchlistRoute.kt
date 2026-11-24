package com.pixiv.reader.feature.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.WatchlistSeries
import com.pixiv.reader.core.network.session.SeriesDetailInfo
import com.pixiv.reader.core.ui.component.card.SeriesBookCover
import com.pixiv.reader.core.ui.component.card.SeriesCard
import com.pixiv.reader.core.ui.component.card.SeriesCardData
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.component.input.PillSegmentedRow
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.layout.BackTopAppBar
import com.pixiv.reader.core.ui.component.list.loadMoreFooter
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.ImageScrim
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes

/**
 * 追更页。两种形态由入口 type 决定：作品 Tab 顶栏铃铛（type=manga）不显示分段；
 * 我的页入口为小说/漫画胶囊分段，小说段与小说 Tab 追更页签同组件、同缓存。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistRoute(
    onBack: () -> Unit,
    initialType: WatchlistSegment = WatchlistSegment.NOVEL,
    onOpenSeries: (Long) -> Unit,
    onOpenIllust: (Long) -> Unit = {},
    viewModel: WatchlistViewModel = hiltViewModel(),
) {
    val type by viewModel.type.collectAsStateWithLifecycle()
    val mangaCovers by viewModel.mangaCovers.collectAsStateWithLifecycle()
    val novelInfos by viewModel.novelInfos.collectAsStateWithLifecycle()
    val paged = viewModel.stateFor(type)
    val items by paged.items.collectAsStateWithLifecycle()
    val isLoading by paged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by paged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by paged.hasMore.collectAsStateWithLifecycle()
    val error by paged.error.collectAsStateWithLifecycle()
    val mangaOnly = initialType == WatchlistSegment.MANGA
    val notificationHostState = rememberNotificationHostState()

    UiMessageEffect(viewModel.message, notificationHostState)

    // 列表就绪后补齐：小说段取系列详情，漫画段取首话封面（数据驻留 VM，列表项本身不带）
    LaunchedEffect(items, type) {
        when (type) {
            WatchlistViewModel.TYPE_MANGA -> viewModel.loadMangaCovers(items)
            else -> viewModel.loadNovelInfos(items)
        }
    }

    Scaffold(
        snackbarHost = { NotificationHost(notificationHostState) },
        topBar = {
            BackTopAppBar(title = stringResource(R.string.watchlist_title), onBack = onBack)
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        AdaptiveContentBox(modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (!mangaOnly) {
                    PillSegmentedRow(
                        options = listOf(
                            WatchlistViewModel.TYPE_NOVEL to R.string.watchlist_type_novel,
                            WatchlistViewModel.TYPE_MANGA to R.string.watchlist_type_manga,
                        ),
                        selected = type,
                        onSelect = viewModel::selectType,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    )
                }
                when {
                    isLoading && items.isEmpty() -> LoadingBox()
                    error != null && items.isEmpty() -> ErrorBox(
                        message = error.orEmpty(),
                        onRetry = { viewModel.retry(type) }
                    )

                    items.isEmpty() -> EmptyBox(stringResource(R.string.watchlist_empty))
                    type == WatchlistViewModel.TYPE_MANGA -> MangaWatchlistGrid(
                        items = items,
                        covers = mangaCovers,
                        hasMore = hasMore,
                        isLoadingMore = isLoadingMore,
                        error = error,
                        onLoadMore = { viewModel.loadMore(type) },
                        onOpenIllust = onOpenIllust,
                        onRemove = viewModel::removeWatchlist,
                    )
                    else -> NovelWatchlistList(
                        items = items,
                        infos = novelInfos,
                        hasMore = hasMore,
                        isLoadingMore = isLoadingMore,
                        error = error,
                        onLoadMore = { viewModel.loadMore(type) },
                        onOpenSeries = onOpenSeries,
                    )
                }
            }
        }
    }
}

/** 与小说 Tab 追更页签同一组件与数据补齐方式。 */
@Composable
private fun NovelWatchlistList(
    items: List<WatchlistSeries>,
    infos: Map<Long, SeriesDetailInfo>,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    onLoadMore: () -> Unit,
    onOpenSeries: (Long) -> Unit,
) {
    // 动效方案在组合期取好：item lambda 不是组合上下文，无法在其中读取 MaterialTheme
    val motionScheme = MaterialTheme.motionScheme
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.lg,
            end = Spacing.lg,
            top = Spacing.sm,
            bottom = Spacing.lg + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.smPlus),
    ) {
        items(items, key = { "novel_${it.id}" }) { series ->
            val info = infos[series.id]
            SeriesCard(
                data = SeriesCardData(
                    title = if (series.isMasked) {
                        stringResource(R.string.watchlist_masked_series)
                    } else {
                        series.title
                    },
                    coverUrl = info?.coverUrl,
                    caption = info?.caption,
                    isConcluded = info?.isConcluded,
                    partsCount = series.published_content_count,
                    totalChars = info?.totalChars ?: 0,
                    // 最近更新时间（ISO 取前 10 位为 yyyy-MM-dd）
                    updatedAt = series.last_published_content_datetime?.take(10),
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
            hasMore = hasMore,
            isLoadingMore = isLoadingMore,
            onLoadMore = onLoadMore,
            error = error,
        )
    }
}

/**
 * 漫画追更瀑布流：3:4 封面 + 话数徽章 + 取消追更钮 + 标题/作者。
 */
@Composable
private fun MangaWatchlistGrid(
    items: List<WatchlistSeries>,
    covers: Map<Long, String>,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    onLoadMore: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onRemove: (WatchlistSeries) -> Unit,
) {
    // 动效方案在组合期取好：item lambda 不是组合上下文，无法在其中读取 MaterialTheme
    val motionScheme = MaterialTheme.motionScheme
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalItemSpacing = Spacing.sm,
    ) {
        items(items, key = { it.id }) { series ->
            MangaWatchlistCard(
                series = series,
                coverUrl = covers[series.id],
                onClick = { series.latest_content_id?.let(onOpenIllust) },
                onRemove = { onRemove(series) },
                modifier = Modifier.animateItem(
                    fadeInSpec = motionScheme.fastEffectsSpec<Float>(),
                    placementSpec = motionScheme.fastSpatialSpec<IntOffset>(),
                ),
            )
        }
        loadMoreFooter(
            hasMore = hasMore,
            isLoadingMore = isLoadingMore,
            onLoadMore = onLoadMore,
            span = StaggeredGridItemSpan.FullLine,
            error = error,
        )
    }
}

@Composable
private fun MangaWatchlistCard(
    series: WatchlistSeries,
    coverUrl: String?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(AppShapes.cardLarge)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(AppShapes.card),
        ) {
            if (!coverUrl.isNullOrBlank()) {
                PixivImage(
                    url = coverUrl,
                    contentDescription = series.title,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                SeriesBookCover(modifier = Modifier.fillMaxSize(), iconSize = 48.dp)
            }
            // 左上话数徽章（ImageScrim 70% 黑底白字，与排行卡徽标同语汇）
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Spacing.sm),
                color = ImageScrim,
                shape = AppShapes.small,
            ) {
                Text(
                    text = stringResource(R.string.watchlist_manga_chapters, series.published_content_count),
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
            // 右上取消追更（外层 48dp 热区满足 M3 最小触控目标，内层 32dp ImageScrim 视觉圆底）
            val removeInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(Sizes.s48)
                    .clickable(
                        role = Role.Button,
                        interactionSource = removeInteraction,
                        indication = null,
                        onClick = onRemove,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(Sizes.s32)
                        .clip(CircleShape)
                        .background(ImageScrim)
                        .indication(removeInteraction, ripple(bounded = true, color = Color.White)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsOff,
                        contentDescription = stringResource(R.string.watchlist_unwatch),
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(Spacing.smPlus)) {
            Text(
                text = if (series.isMasked) stringResource(R.string.watchlist_masked_series) else series.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!series.user?.name.isNullOrBlank()) {
                Text(
                    text = series.user?.name.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}
