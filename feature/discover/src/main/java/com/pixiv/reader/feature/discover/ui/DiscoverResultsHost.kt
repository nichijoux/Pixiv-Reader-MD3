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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ui.component.card.NovelCard
import com.pixiv.reader.core.ui.component.card.toCardData
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.grid.IllustWaterfallGrid
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.discover.R
import com.pixiv.reader.feature.discover.state.DiscoverViewModel
import com.pixiv.reader.feature.discover.state.SearchType
import kotlinx.coroutines.launch

/** 结果态：TabRow + HorizontalPager（按类型渲染 HOT / 普通结果）。 */
@Composable
internal fun SearchResultPager(
    viewModel: DiscoverViewModel,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { SearchType.entries.size })
    val scope = rememberCoroutineScope()
    val filters by viewModel.filters.collectAsStateWithLifecycle()

    // 滑动切页 → 同步类型
    LaunchedEffect(pagerState.currentPage) {
        val page = pagerState.currentPage
        if (page in SearchType.entries.indices) {
            viewModel.setType(SearchType.entries[page])
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
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

/** 热门模式：插画一次性完整列表（无分页）。 */
@Composable
private fun HotIllustGrid(
    viewModel: DiscoverViewModel,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    val popular by viewModel.popularIllusts.collectAsStateWithLifecycle()
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
        onOpenUser = onOpenUser,
    )
}

/** 热门模式：小说一次性完整列表（无分页）。 */
@Composable
private fun HotNovelList(
    viewModel: DiscoverViewModel,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
) {
    val popular by viewModel.popularNovels.collectAsStateWithLifecycle()
    if (popular.isEmpty()) {
        EmptyBox(stringResource(R.string.search_no_hot))
        return
    }
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
            )
        }
    }
}
