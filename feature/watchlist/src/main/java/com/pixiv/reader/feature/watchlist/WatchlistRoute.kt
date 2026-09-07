package com.pixiv.reader.feature.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.list.LoadMoreItem
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes

/**
 * 追更页：小说 / 漫画系列追更列表。
 *
 * 两种形态（按入口 `type` 参数）：
 * - 作品 Tab 顶栏铃铛（`type=manga`）：**单分段纯漫画**——不显示类型切换，漫画系列瀑布流。
 * - 我的页入口（默认 novel）：小说 / 漫画 SegmentedButton 分段；小说为 [SeriesCard] 列表
 *   （与小说 Tab 追更页签完全一致：封面/简介/完结态/字数经 [com.pixiv.reader.core.network.session.SeriesDetailCache]
 *   进程缓存补齐，点击打开系列详情页），漫画段与作品入口同款瀑布流。
 *
 * @param onBack 返回
 * @param initialType 初始类型（"novel" / "manga"，路由参数；manga = 作品页入口纯漫画形态）
 * @param onOpenSeries 打开小说系列详情（小说卡片点击，对齐小说 Tab 追更页签）
 * @param onOpenIllust 打开插画/漫画详情（漫画卡片点击 = 最新一话）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistRoute(
    onBack: () -> Unit,
    initialType: String = WatchlistViewModel.TYPE_NOVEL,
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
    // 作品页入口（type=manga）：单分段纯漫画形态
    val mangaOnly = initialType == WatchlistViewModel.TYPE_MANGA

    // 列表就绪后补齐系列信息：小说段 → 系列详情（封面/简介），漫画段 → 首话封面
    LaunchedEffect(items, type) {
        when (type) {
            WatchlistViewModel.TYPE_MANGA -> viewModel.loadMangaCovers(items)
            else -> viewModel.loadNovelInfos(items)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.watchlist_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        AdaptiveContentBox(modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 类型分段：仅多类型形态显示（作品入口纯漫画不显示）
                if (!mangaOnly) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    ) {
                        SegmentedButton(
                            selected = type == WatchlistViewModel.TYPE_NOVEL,
                            onClick = { viewModel.selectType(WatchlistViewModel.TYPE_NOVEL) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.watchlist_type_novel)) },
                        )
                        SegmentedButton(
                            selected = type == WatchlistViewModel.TYPE_MANGA,
                            onClick = { viewModel.selectType(WatchlistViewModel.TYPE_MANGA) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.watchlist_type_manga)) },
                        )
                    }
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
                        onLoadMore = { viewModel.loadMore(type) },
                        onOpenIllust = onOpenIllust,
                        onRemove = viewModel::removeWatchlist,
                    )
                    else -> NovelWatchlistList(
                        items = items,
                        infos = novelInfos,
                        hasMore = hasMore,
                        isLoadingMore = isLoadingMore,
                        onLoadMore = { viewModel.loadMore(type) },
                        onOpenSeries = onOpenSeries,
                    )
                }
            }
        }
    }
}

/**
 * 小说追更列表：[SeriesCard] 系列（与小说 Tab 追更页签同一组件与数据补齐方式），
 * 点击打开系列详情页；触底加载。
 *
 * @param items 小说追更系列
 * @param infos 系列详情（seriesId → 封面/简介/完结态/字数，异步补齐）
 * @param hasMore 是否还有下一页
 * @param isLoadingMore 加载更多中
 * @param onLoadMore 触底加载更多回调
 * @param onOpenSeries 卡片点击（打开系列详情页）
 */
@Composable
private fun NovelWatchlistList(
    items: List<WatchlistSeries>,
    infos: Map<Long, SeriesDetailInfo>,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onOpenSeries: (Long) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // 沉浸式底部：尾部避开系统导航栏（与小说 Tab 追更页签一致）
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
                    // 封面/简介/连载状态/字数来自 getNovelSeries 详情（SeriesDetailCache，与小说 Tab 追更页签同缓存）
                    coverUrl = info?.coverUrl,
                    caption = info?.caption,
                    isConcluded = info?.isConcluded,
                    partsCount = series.published_content_count,
                    totalChars = info?.totalChars ?: 0,
                    // 最近更新时间（ISO 取前 10 位 yyyy-MM-dd，作者行右侧展示）
                    updatedAt = series.last_published_content_datetime?.take(10),
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

/**
 * 漫画追更瀑布流：系列封面卡（3:4 封面 + 话数徽章 + 取消追更钮 + 标题/作者）+ 触底加载。
 * 封面缺失时书本图标兜底，异步补齐后渐次刷新。
 */
@Composable
private fun MangaWatchlistGrid(
    items: List<WatchlistSeries>,
    covers: Map<Long, String>,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onRemove: (WatchlistSeries) -> Unit,
) {
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
            )
        }
        if (hasMore) {
            item(key = "load_more", span = StaggeredGridItemSpan.FullLine) {
                LoadMoreItem(isLoadingMore = isLoadingMore, onLoadMore = onLoadMore)
            }
        }
    }
}

/**
 * 漫画追更卡片：封面（3:4）+ 左上话数徽章 + 右上取消追更钮 + 底部标题/作者。
 *
 * @param series 漫画追更系列
 * @param coverUrl 系列封面（异步补齐；null 用书本图标兜底）
 * @param onClick 卡片点击（打开最新一话详情）
 * @param onRemove 取消追更
 */
@Composable
private fun MangaWatchlistCard(
    series: WatchlistSeries,
    coverUrl: String?,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
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
            // 左上话数徽章（黑底白字，与排行卡徽标同语汇）
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Spacing.sm),
                color = Color.Black.copy(alpha = 0.45f),
                shape = AppShapes.small,
            ) {
                Text(
                    text = stringResource(R.string.watchlist_manga_chapters, series.published_content_count),
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
            // 右上取消追更（半透明圆底小钮，与收藏浮层按钮同规格）
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.sm)
                    .size(Sizes.s28)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(onClick = onRemove),
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
