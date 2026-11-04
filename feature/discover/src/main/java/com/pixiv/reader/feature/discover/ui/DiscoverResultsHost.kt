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

/** 结果态：PrimaryScrollableTabRow + HorizontalPager（作品 / 小说 / 用户三段，按类型渲染 HOT / 普通结果）。 */
@Composable
internal fun SearchResultPager(
    viewModel: DiscoverViewModel,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
) {
    // 初始页跟随 VM 当前类型：小说标签深链 setType(NOVEL) 后直落小说栏，
    // 否则 pager 首组合 page=0 的同步 effect 会把类型翻回插画并触发重复搜索
    val pagerState = rememberPagerState(
        initialPage = viewModel.type.value.ordinal,
        pageCount = { SearchType.entries.size },
    )
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
        // 类型页签：作品 / 小说 / 用户（PrimaryScrollableTabRow；选中态跟 Pager 落页，点击反向滚页）
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

/**
 * 热门模式：插画一次性完整列表（无分页）；请求在途且无数据时显示骨架屏。
 *
 * @param viewModel 发现页 ViewModel（提供热门插画列表与在途状态）
 * @param onOpenIllust 点击插画卡回调（插画 ID）
 * @param onOpenUser 点击作者头像回调（用户 ID）
 * @return 无返回值
 */
@Composable
private fun HotIllustGrid(
    viewModel: DiscoverViewModel,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    val popular by viewModel.popularIllusts.collectAsStateWithLifecycle()
    val loading by viewModel.popularIllustsLoading.collectAsStateWithLifecycle()
    // 请求在途且尚无数据 → 骨架屏；仅在加载完成仍为空时才显示空态
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
        onOpenUser = onOpenUser,
    )
}

/**
 * 热门模式：小说一次性完整列表（无分页）；请求在途且无数据时显示骨架屏。
 *
 * @param viewModel 发现页 ViewModel（提供热门小说列表与在途状态）
 * @param onOpenNovel 点击小说卡回调（小说 ID）
 * @param onOpenUser 点击作者头像回调（用户 ID）
 * @param onOpenSeries 点击系列回调（系列 ID）
 * @return 无返回值
 */
@Composable
private fun HotNovelList(
    viewModel: DiscoverViewModel,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
) {
    val popular by viewModel.popularNovels.collectAsStateWithLifecycle()
    val loading by viewModel.popularNovelsLoading.collectAsStateWithLifecycle()
    // 请求在途且尚无数据 → 骨架屏；仅在加载完成仍为空时才显示空态
    if (loading && popular.isEmpty()) {
        NovelSearchSkeleton()
        return
    }
    if (popular.isEmpty()) {
        EmptyBox(stringResource(R.string.search_no_hot))
        return
    }
    // 动效方案在组合期取好：item lambda 不是组合上下文，无法在其中读取 MaterialTheme
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
                // 进场淡入 + 列表刷新时位置平滑过渡
                modifier = Modifier.animateItem(
                    fadeInSpec = motionScheme.fastEffectsSpec<Float>(),
                    placementSpec = motionScheme.fastSpatialSpec<IntOffset>(),
                ),
            )
        }
    }
}
