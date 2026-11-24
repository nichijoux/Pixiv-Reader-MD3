package com.pixiv.reader.feature.discover.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ui.component.card.NovelCard
import com.pixiv.reader.core.ui.component.card.toCardData
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.grid.IllustWaterfallGrid
import com.pixiv.reader.core.ui.component.grid.IllustWaterfallSkeleton
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.discover.R
import com.pixiv.reader.feature.discover.state.DiscoverViewModel
import com.pixiv.reader.feature.discover.state.SearchType
import kotlinx.coroutines.launch

@Composable
internal fun SearchResultPager(
    viewModel: DiscoverViewModel,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
) {
    // 初始页跟随 VM 当前类型，否则深链 setType(NOVEL) 后首组合 page=0 会同步回插画并重复搜索
    val pagerState = rememberPagerState(
        initialPage = viewModel.type.value.ordinal,
        pageCount = { SearchType.entries.size },
    )
    val scope = rememberCoroutineScope()
    val filters by viewModel.filters.collectAsStateWithLifecycle()

    LaunchedEffect(pagerState.currentPage) {
        val page = pagerState.currentPage
        if (page in SearchType.entries.indices) {
            viewModel.setType(SearchType.entries[page])
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(
            selectedTabIndex = pagerState.currentPage.coerceIn(0, SearchType.entries.size - 1),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            SearchType.entries.forEachIndexed { index, t ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(stringResource(t.labelRes)) },
                )
            }
        }
        HorizontalPager(state = pagerState) { page ->
            when (SearchType.entries.getOrNull(page)) {
                SearchType.ILLUST -> if (filters.sort == "popular_preview") {
                    HotIllustGrid(viewModel, onOpenIllust, onOpenUser)
                } else {
                    IllustSearchResults(viewModel, onOpenIllust, onOpenUser)
                }

                SearchType.NOVEL -> if (filters.sort == "popular_preview") {
                    HotNovelList(viewModel, onOpenNovel, onOpenUser, onOpenSeries)
                } else {
                    NovelSearchResults(
                        viewModel,
                        onOpenNovel,
                        onOpenUser,
                        onOpenSeries
                    )
                }

                SearchType.USER -> UserSearchResults(viewModel, onOpenUser)
                null -> {}
            }
        }
    }
}

@Composable
private fun HotIllustGrid(
    viewModel: DiscoverViewModel,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    val popular by viewModel.popularIllusts.collectAsStateWithLifecycle()
    val loading by viewModel.popularIllustsLoading.collectAsStateWithLifecycle()
    if (loading && popular.isEmpty()) {
        IllustWaterfallSkeleton()
        return
    }
    if (popular.isEmpty()) {
        EmptyBox(stringResource(R.string.search_no_hot))
        return
    }
    IllustWaterfallGrid(
        illusts = popular,
        onItemClick = onOpenIllust,
        onLoadMore = {},
        hasMore = false,
        isLoadingMore = false,
        onToggleFavorite = { id, fav -> viewModel.toggleIllustFavorite(id, fav) },
        onTagClick = { tag ->
            viewModel.onQueryChange(tag)
            viewModel.search()
        },
        onOpenUser = onOpenUser,
    )
}

@Composable
private fun HotNovelList(
    viewModel: DiscoverViewModel,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
) {
    val popular by viewModel.popularNovels.collectAsStateWithLifecycle()
    val loading by viewModel.popularNovelsLoading.collectAsStateWithLifecycle()
    if (loading && popular.isEmpty()) {
        NovelSearchSkeleton()
        return
    }
    if (popular.isEmpty()) {
        EmptyBox(stringResource(R.string.search_no_hot))
        return
    }
    // item lambda 非组合上下文，需在外部提前读取 motionScheme
    val motionScheme = MaterialTheme.motionScheme
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.xs, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.smPlus),
    ) {
        items(popular, key = { it.id }) { novel ->
            NovelCard(
                novel = novel.toCardData(),
                onClick = { onOpenNovel(novel.id) },
                onOpenAuthor = { novel.user?.id?.let(onOpenUser) },
                onToggleFavorite = { fav -> viewModel.toggleNovelFavorite(novel.id, fav) },
                onTagClick = { tag ->
                    viewModel.onQueryChange(tag)
                    viewModel.search()
                },
                onSeriesClick = { novel.series?.id?.let(onOpenSeries) },
                modifier = Modifier.animateItem(
                    fadeInSpec = motionScheme.fastEffectsSpec<Float>(),
                    placementSpec = motionScheme.fastSpatialSpec<IntOffset>(),
                ),
            )
        }
    }
}
