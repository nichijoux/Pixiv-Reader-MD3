package com.pixiv.reader.feature.bookmark

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.layout.BackTopAppBar
import com.pixiv.reader.core.ui.component.layout.SegmentedPager
import com.pixiv.reader.core.ui.component.list.PagedFeed
import com.pixiv.reader.core.ui.component.grid.IllustWaterfallGrid
import com.pixiv.reader.core.ui.component.list.loadMoreFooter
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.card.NovelCard
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.card.toCardData
import com.pixiv.reader.core.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkRoute(
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    // 小说分区标签搜小说类型；空则复用 [onSearchTag]
    onNovelSearchTag: ((String) -> Unit)? = null,
    viewModel: BookmarkViewModel = hiltViewModel(),
) {
    val type by viewModel.type.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val selectedTag by viewModel.selectedTag.collectAsStateWithLifecycle()

    val notificationHostState = rememberNotificationHostState()
    UiMessageEffect(viewModel.message, notificationHostState)

    Scaffold(
        topBar = {
            BackTopAppBar(title = stringResource(R.string.bookmark_title), onBack = onBack)
        },
        snackbarHost = { NotificationHost(notificationHostState) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        AdaptiveContentBox(modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                val pagerState = rememberPagerState(
                    initialPage = BookmarkType.entries.indexOf(type).coerceAtLeast(0),
                    pageCount = { BookmarkType.entries.size },
                )
                SegmentedPager(
                    tabs = BookmarkType.entries,
                    state = pagerState,
                    onSelect = viewModel::selectType,
                    tabLabel = { it.labelRes },
                    pagerModifier = Modifier.weight(1f),
                    between = {
                        // 标签筛选
                        if (tags.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                            ) {
                                item(key = "all") {
                                    FilterChip(
                                        selected = selectedTag == null,
                                        onClick = { viewModel.selectTag(null) },
                                        label = { Text(stringResource(R.string.bookmark_tag_all)) },
                                    )
                                }
                                items(tags, key = { it.name ?: it.hashCode() }) { tag ->
                                    FilterChip(
                                        selected = selectedTag == tag.name,
                                        onClick = { viewModel.selectTag(tag.name) },
                                        label = { Text(tag.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    )
                                }
                            }
                        }
                    },
                    pageContent = { _, tab ->
                        // 重试与触底分别绑定页面类型，预组合的相邻页不触发续页。
                        when (tab) {
                            BookmarkType.ILLUST -> BookmarkIllustList(
                                paged = viewModel.illustPaged,
                                onOpenIllust = onOpenIllust,
                                onOpenUser = onOpenUser,
                                onToggleFavorite = viewModel::toggleIllustFavorite,
                                onRetry = { viewModel.retry(tab) },
                                onLoadMore = { viewModel.loadMore(tab) },
                                isCurrentPage = tab == type,
                            )
                            BookmarkType.NOVEL -> BookmarkNovelList(
                                paged = viewModel.novelPaged,
                                onOpenNovel = onOpenNovel,
                                onOpenUser = onOpenUser,
                                onOpenSeries = onOpenSeries,
                                onToggleFavorite = viewModel::toggleNovelFavorite,
                                onTagClick = onNovelSearchTag ?: onSearchTag,
                                onRetry = { viewModel.retry(tab) },
                                onLoadMore = { viewModel.loadMore(tab) },
                                isCurrentPage = tab == type,
                            )
                        }
                    },
                )
            }
        }
    }
}

/** 插画收藏：三态门槛 + 瀑布流。 */
@Composable
private fun BookmarkIllustList(
    paged: PagedState<com.pixiv.api.model.Illust>,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    isCurrentPage: Boolean,
) {
    PagedFeed(
        paged = paged,
        emptyText = stringResource(R.string.bookmark_empty),
        onRetry = onRetry,
    ) { state ->
        IllustWaterfallGrid(
            illusts = state.items,
            onItemClick = onOpenIllust,
            onLoadMore = onLoadMore,
            hasMore = isCurrentPage && state.hasMore,
            isLoadingMore = state.isLoadingMore,
            error = state.error,
            contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.xs, bottom = Spacing.xl),
            onToggleFavorite = onToggleFavorite,
            onOpenUser = onOpenUser,
        )
    }
}

/** 小说收藏：三态门槛 + NovelCard 列表。 */
@Composable
private fun BookmarkNovelList(
    paged: PagedState<com.pixiv.api.model.Novel>,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onTagClick: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    isCurrentPage: Boolean,
) {
    // 动效方案在组合期取好：item lambda 不是组合上下文，无法在其中读取 MaterialTheme
    val motionScheme = MaterialTheme.motionScheme
    PagedFeed(
        paged = paged,
        emptyText = stringResource(R.string.bookmark_empty),
        onRetry = onRetry,
    ) { state ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.xs, bottom = Spacing.xl),
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
                hasMore = isCurrentPage && state.hasMore,
                isLoadingMore = state.isLoadingMore,
                onLoadMore = onLoadMore,
                error = state.error,
            )
        }
    }
}
