package com.pixiv.reader.feature.comic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.ComicBanner
import com.pixiv.reader.core.common.LoadState
import com.pixiv.reader.feature.comic.state.ComicRankingMode
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.layout.BackTopAppBar
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.component.input.PillSegmentedRow
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.comic.R
import com.pixiv.reader.feature.comic.state.ComicHomeViewModel
import com.pixiv.reader.feature.comic.state.ComicRankingState
import com.pixiv.reader.feature.comic.state.ComicTopState
import kotlinx.coroutines.launch

private enum class ComicHomeTab(val labelRes: Int) {
    TOP(R.string.comic_tab_top),

    RANKING(R.string.comic_tab_ranking),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicHomeRoute(
    onBack: () -> Unit,
    onOpenWork: (Long) -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: ComicHomeViewModel = hiltViewModel(),
) {
    val top by viewModel.top.collectAsStateWithLifecycle()
    val ranking by viewModel.ranking.collectAsStateWithLifecycle()
    val notificationHost = rememberNotificationHostState()
    UiMessageEffect(viewModel.message, notificationHost)

    val pagerState = rememberPagerState(pageCount = { ComicHomeTab.entries.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            BackTopAppBar(
                title = stringResource(R.string.comic_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = stringResource(R.string.comic_cd_search),
                        )
                    }
                },
            )
        },
        snackbarHost = { NotificationHost(notificationHost) },
    ) { padding ->
        AdaptiveContentBox(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                // 以页码为值：点击反向滚页，选中态跟 Pager 落页。
                PillSegmentedRow(
                    options = ComicHomeTab.entries.mapIndexed { index, tab -> index to tab.labelRes },
                    selected = pagerState.currentPage,
                    onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    when (ComicHomeTab.entries.getOrNull(page)) {
                        ComicHomeTab.TOP -> ComicTopTab(top = top, onOpenWork = onOpenWork, onRetry = viewModel::loadTop)
                        ComicHomeTab.RANKING -> ComicRankingTab(
                            ranking = ranking,
                            onOpenWork = onOpenWork,
                            onSelectMode = viewModel::selectRankingMode,
                            onSelectLabel = viewModel::selectLabel,
                            onRetry = viewModel::retry,
                        )
                        null -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun ComicTopTab(
    top: ComicTopState,
    onOpenWork: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    // 动效方案在组合期取好：item lambda 不是组合上下文，无法在其中读取 MaterialTheme
    val motionScheme = MaterialTheme.motionScheme
    when (top) {
        LoadState.Loading -> ComicHomeSkeleton(showRankingHeader = false)
        is LoadState.Error -> ErrorBox(message = top.message, onRetry = onRetry)
        is LoadState.Content -> AdaptiveContentBox {
            val data = top.data
            LazyVerticalGrid(
                columns = GridCells.Fixed(GRID_COLUMNS),
                state = rememberLazyGridState(),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (data.banners.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "comic_top_banners") {
                        ComicBannerRow(banners = data.banners, onOpenWork = onOpenWork)
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }, key = "comic_top_recent_title") {
                    Text(
                        text = stringResource(R.string.comic_section_recent),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
                if (data.recentWorks.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "comic_top_empty") {
                        EmptyBox(text = stringResource(R.string.comic_empty_recent))
                    }
                }
                items(data.recentWorks, key = { it.id }) { work ->
                    ComicWorkCard(
                        title = work.title.orEmpty(),
                        author = work.author.orEmpty(),
                        coverUrl = work.thumbnailImageUrl ?: work.mainImageUrl,
                        storiesCount = work.storiesCount,
                        onClick = { onOpenWork(work.id) },
                        // 进场淡入 + 刷新时位置平滑过渡
                        modifier = Modifier.animateItem(
                            fadeInSpec = motionScheme.fastEffectsSpec<Float>(),
                            placementSpec = motionScheme.fastSpatialSpec<IntOffset>(),
                        ),
                    )
                }
            }
        }
    }
}

/** banner 横滑行：站内作品页链接可直达详情，其余链接点击忽略（v1 不内嵌网页）。 */
@Composable
private fun ComicBannerRow(banners: List<ComicBanner>, onOpenWork: (Long) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(banners.size, key = { banners[it].id }) { index ->
            val banner = banners[index]
            val workId = parseComicWorkId(banner.url)
            PixivImage(
                url = banner.imageUrl,
                contentDescription = stringResource(R.string.comic_cd_banner),
                modifier = Modifier
                    .width(BANNER_WIDTH)
                    .aspectRatio(
                        if (banner.width > 0 && banner.height > 0) {
                            banner.width.toFloat() / banner.height
                        } else {
                            2f
                        },
                    )
                    .clickable(enabled = workId != null) { workId?.let(onOpenWork) },
            )
        }
    }
}

@Composable
private fun ComicRankingTab(
    ranking: ComicRankingState,
    onOpenWork: (Long) -> Unit,
    onSelectMode: (ComicRankingMode) -> Unit,
    onSelectLabel: (String) -> Unit,
    onRetry: () -> Unit,
) {
    // 动效方案在组合期取好：item lambda 不是组合上下文，无法在其中读取 MaterialTheme
    val motionScheme = MaterialTheme.motionScheme
    val loadState = ranking.loadState
    when {
        loadState is LoadState.Loading && ranking.labels.isEmpty() ->
            ComicHomeSkeleton(showRankingHeader = true)
        loadState is LoadState.Error -> ErrorBox(message = loadState.message, onRetry = onRetry)
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            state = rememberLazyGridState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "comic_ranking_modes") {
                val modes = ComicRankingMode.entries
                PillSegmentedRow(
                    options = modes.map { mode ->
                        mode to if (mode == ComicRankingMode.WEEKLY) {
                            R.string.comic_ranking_mode_weekly
                        } else {
                            R.string.comic_ranking_mode_popularity
                        }
                    },
                    selected = ranking.selectedMode,
                    onSelect = onSelectMode,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "comic_ranking_labels") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(ranking.labels.size) { index ->
                        val label = ranking.labels[index]
                        FilterChip(
                            selected = ranking.selectedLabel == label,
                            onClick = { onSelectLabel(label) },
                            label = { Text(label) },
                        )
                    }
                }
            }
            if (loadState is LoadState.Content) {
                if (loadState.data.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "comic_ranking_empty") {
                        EmptyBox(text = stringResource(R.string.comic_empty_ranking))
                    }
                }
                items(loadState.data, key = { "${ranking.selectedMode}-${ranking.selectedLabel}-${it.id}" }) { work ->
                    ComicWorkCard(
                        title = work.title.orEmpty(),
                        author = work.author.orEmpty(),
                        coverUrl = work.thumbnailImageUrl ?: work.mainImageUrl,
                        storiesCount = work.storiesCount,
                        onClick = { onOpenWork(work.id) },
                        // key 含模式/标签前缀：切榜即整榜换 key → 全量重新进场淡入（此处正是想要的语义）
                        modifier = Modifier.animateItem(
                            fadeInSpec = motionScheme.fastEffectsSpec<Float>(),
                            placementSpec = motionScheme.fastSpatialSpec<IntOffset>(),
                        ),
                    )
                }
            }
        }
    }
}

/** 平板由 AdaptiveContentBox 限宽后仍适用。 */
private const val GRID_COLUMNS = 3

private val BANNER_WIDTH = 280.dp
