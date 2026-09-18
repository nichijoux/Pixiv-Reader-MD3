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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.ComicBanner
import com.pixiv.reader.feature.comic.state.ComicRankingMode
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.layout.BackTopAppBar
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.comic.R
import com.pixiv.reader.feature.comic.state.ComicHomeViewModel
import com.pixiv.reader.feature.comic.state.ComicRankingState
import com.pixiv.reader.feature.comic.state.ComicTopState
import kotlinx.coroutines.launch

/** 首页双页签（沿项目 SegmentedButton + Pager 惯式）。 */
private enum class ComicHomeTab(val labelRes: Int) {
    /** 更新页（banner + 最近更新）。 */
    TOP(R.string.comic_tab_top),

    /** 排行页（周榜 / 人气 × 分类标签）。 */
    RANKING(R.string.comic_tab_ranking),
}

/**
 * COMIC 首页路由：更新（banner 位 + 最近更新）与排行（模式 × 分类标签）双页签，
 * 顶栏提供搜索入口；作品点击进详情。
 *
 * @param onBack 返回回调（上层 safeBack）
 * @param onOpenWork 进作品详情（作品 id）
 * @param onOpenSearch 进搜索页
 * @param viewModel Hilt 注入的首页 VM
 * @return 无返回值
 */
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
                // tab 分段：点击反向滚页（选中态跟 Pager 落页）
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                ) {
                    ComicHomeTab.entries.forEachIndexed { index, tab ->
                        SegmentedButton(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = ComicHomeTab.entries.size),
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
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

/**
 * 「更新」页：banner 横滑 + 最近更新作品网格。
 *
 * @param top 首页数据状态
 * @param onOpenWork 进详情回调
 * @param onRetry 整页重试
 * @return 无返回值
 */
@Composable
private fun ComicTopTab(
    top: ComicTopState,
    onOpenWork: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    when {
        top.isLoading && top.recentWorks.isEmpty() -> LoadingBox()
        top.error != null && top.recentWorks.isEmpty() -> ErrorBox(message = top.error, onRetry = onRetry)
        else -> AdaptiveContentBox {
            LazyVerticalGrid(
                columns = GridCells.Fixed(GRID_COLUMNS),
                state = rememberLazyGridState(),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (top.banners.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "comic_top_banners") {
                        ComicBannerRow(banners = top.banners, onOpenWork = onOpenWork)
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }, key = "comic_top_recent_title") {
                    Text(
                        text = stringResource(R.string.comic_section_recent),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
                if (top.recentWorks.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "comic_top_empty") {
                        EmptyBox(text = stringResource(R.string.comic_empty_recent))
                    }
                }
                items(top.recentWorks, key = { it.id }) { work ->
                    ComicWorkCard(
                        title = work.title.orEmpty(),
                        author = work.author.orEmpty(),
                        coverUrl = work.thumbnailImageUrl ?: work.mainImageUrl,
                        storiesCount = work.storiesCount,
                        onClick = { onOpenWork(work.id) },
                    )
                }
            }
        }
    }
}

/**
 * banner 横滑行：站内作品页链接可直达详情，其余链接点击忽略（v1 不内嵌网页）。
 *
 * @param banners banner 列表
 * @param onOpenWork 进详情回调
 * @return 无返回值
 */
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

/**
 * 「排行」页：模式分段（周榜 / 人气）+ 分类标签横滑 + 榜单网格。
 *
 * @param ranking 排行数据状态
 * @param onOpenWork 进详情回调
 * @param onSelectMode 切换模式
 * @param onSelectLabel 切换标签
 * @param onRetry 整页重试
 * @return 无返回值
 */
@Composable
private fun ComicRankingTab(
    ranking: ComicRankingState,
    onOpenWork: (Long) -> Unit,
    onSelectMode: (ComicRankingMode) -> Unit,
    onSelectLabel: (String) -> Unit,
    onRetry: () -> Unit,
) {
    when {
        ranking.isLoading && ranking.works.isEmpty() && ranking.labels.isEmpty() -> LoadingBox()
        ranking.error != null && ranking.works.isEmpty() -> ErrorBox(message = ranking.error, onRetry = onRetry)
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            state = rememberLazyGridState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "comic_ranking_modes") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val modes = ComicRankingMode.entries
                    modes.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = ranking.selectedMode == mode,
                            onClick = { onSelectMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                            modifier = Modifier.weight(1f),
                            label = {
                                Text(
                                    stringResource(
                                        if (mode == ComicRankingMode.WEEKLY) {
                                            R.string.comic_ranking_mode_weekly
                                        } else {
                                            R.string.comic_ranking_mode_popularity
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
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
            if (ranking.works.isEmpty() && !ranking.isLoading) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "comic_ranking_empty") {
                    EmptyBox(text = stringResource(R.string.comic_empty_ranking))
                }
            }
            items(ranking.works, key = { "${ranking.selectedMode}-${ranking.selectedLabel}-${it.id}" }) { work ->
                ComicWorkCard(
                    title = work.title.orEmpty(),
                    author = work.author.orEmpty(),
                    coverUrl = work.thumbnailImageUrl ?: work.mainImageUrl,
                    storiesCount = work.storiesCount,
                    onClick = { onOpenWork(work.id) },
                )
            }
        }
    }
}

/** 榜单网格列数（手机 3 列，平板由 AdaptiveContentBox 限宽后仍适用）。 */
private const val GRID_COLUMNS = 3

/** banner 卡基准宽（横滑行内的固定宽度，比例按元数据自适应）。 */
private val BANNER_WIDTH = 280.dp
