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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * 我的收藏（P5）：插画/小说收藏 + 标签筛选 + 分页。
 *
 * @param onBack 返回
 * @param onOpenIllust 打开作品详情
 * @param onOpenNovel 打开小说详情
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkRoute(
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    // 小说分区标签点击（搜小说类型）；空则复用 [onSearchTag]
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
                // 类型分段（作品 / 小说）+ 滑动分页：SegmentedPager 双向同步
                // （点击分段 / 左右滑动均可切换；落页同步当前类型，VM 内部负责重置标签 / 加载对应列表）
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
                        // 标签筛选（当前类型的标签）
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
                        when (tab) {
                            BookmarkType.ILLUST -> BookmarkIllustList(
                                paged = viewModel.illustPaged,
                                onOpenIllust = onOpenIllust,
                                onOpenUser = onOpenUser,
                                onLoadMore = viewModel::loadMore,
                            )
                            BookmarkType.NOVEL -> BookmarkNovelList(
                                paged = viewModel.novelPaged,
                                onOpenNovel = onOpenNovel,
                                onOpenUser = onOpenUser,
                                onOpenSeries = onOpenSeries,
                                onToggleFavorite = { id, fav -> viewModel.toggleNovelFavorite(id, fav) },
                                onTagClick = onNovelSearchTag ?: onSearchTag,
                                onLoadMore = viewModel::loadMore,
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * 插画收藏列表：三态门槛 + 瀑布流（触底翻页回调透传 [IllustWaterfallGrid]）。
 *
 * @param paged 插画收藏分页状态
 * @param onOpenIllust 卡片点击（打开作品详情）
 * @param onOpenUser 作者行点击（打开用户主页）
 * @param onLoadMore 触底加载更多回调（同时用作失败重试）
 * @return 无返回值
 */
@Composable
private fun BookmarkIllustList(
    paged: PagedState<com.pixiv.api.model.Illust>,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    PagedFeed(
        paged = paged,
        emptyText = stringResource(R.string.bookmark_empty),
        onRetry = onLoadMore,
    ) { state ->
        IllustWaterfallGrid(
            illusts = state.items,
            onItemClick = onOpenIllust,
            onLoadMore = onLoadMore,
            hasMore = state.hasMore,
            isLoadingMore = state.isLoadingMore,
            contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.xs, bottom = Spacing.xl),
            onOpenUser = onOpenUser,
        )
    }
}

/**
 * 小说收藏列表：三态门槛 + NovelCard 列表（触底加载）。
 *
 * @param paged 小说收藏分页状态
 * @param onOpenNovel 卡片点击（打开小说详情）
 * @param onOpenUser 作者行点击（打开用户主页）
 * @param onOpenSeries 系列标题点击（打开系列详情）
 * @param onToggleFavorite 收藏切换回调
 * @param onTagClick 标签点击（跳搜索）
 * @param onLoadMore 触底加载更多回调（同时用作失败重试）
 * @return 无返回值
 */
@Composable
private fun BookmarkNovelList(
    paged: PagedState<com.pixiv.api.model.Novel>,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onTagClick: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    PagedFeed(
        paged = paged,
        emptyText = stringResource(R.string.bookmark_empty),
        onRetry = onLoadMore,
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
                )
            }
            loadMoreFooter(hasMore = state.hasMore, isLoadingMore = state.isLoadingMore, onLoadMore = onLoadMore)
        }
    }
}
