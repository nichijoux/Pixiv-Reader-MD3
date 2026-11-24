package com.pixiv.reader.feature.discover.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.comment.state.CommentListViewModel
import com.pixiv.reader.core.comment.state.CommentTarget
import com.pixiv.reader.core.comment.ui.CommentPane
import com.pixiv.reader.core.network.illust.IllustViewModel
import com.pixiv.reader.core.ui.component.card.IllustCard
import com.pixiv.reader.core.ui.component.detail.IllustDetailPane
import com.pixiv.reader.core.ui.component.detail.IllustDetailStrings
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentTitle
import com.pixiv.reader.core.ui.component.layout.ListDetailOverlay
import com.pixiv.reader.core.ui.component.layout.isDetailPaneEnabled
import com.pixiv.reader.core.ranking.ui.RANKING_GRID_MIN_COLUMN_WIDTH
import com.pixiv.reader.core.ranking.ui.RankingDateChipRow
import com.pixiv.reader.core.ranking.ui.RankingDatePickerButton
import com.pixiv.reader.core.ranking.ui.RankingIllustSkeleton
import com.pixiv.reader.core.ranking.ui.RankingList
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.discover.R
import com.pixiv.reader.feature.discover.state.AiRankingViewModel
import com.pixiv.reader.feature.discover.state.FilteredRankingViewModel
import com.pixiv.reader.feature.discover.state.PeriodRankingViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilteredRankingScreen(
    title: String,
    filter: (Illust) -> Boolean,
    filterKey: String,
    filteredEmptyText: String,
    eraChips: Boolean,
    viewModel: FilteredRankingViewModel,
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenViewer: (Long, Int) -> Unit,
    onSearchTag: (String) -> Unit,
) {
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val paneEnabled = isDetailPaneEnabled(subtractRail = false)
    var selected by remember { mutableStateOf<Illust?>(null) }
    val detailVm: IllustViewModel = hiltViewModel()
    val commentVm: CommentListViewModel = hiltViewModel()
    val detailStrings = IllustDetailStrings(
        loadRetry = stringResource(R.string.ranking_illust_load_retry),
        fullscreen = stringResource(R.string.ranking_detail_fullscreen),
        statView = stringResource(R.string.ranking_detail_stat_view),
        statBookmark = stringResource(R.string.ranking_detail_stat_bookmark),
        statPages = stringResource(R.string.ranking_detail_stat_pages),
        expand = stringResource(R.string.ranking_detail_expand),
        collapse = stringResource(R.string.ranking_detail_collapse),
        follow = stringResource(R.string.ranking_detail_follow),
        followed = stringResource(R.string.ranking_detail_followed),
        related = stringResource(R.string.ranking_detail_related),
        bookmark = stringResource(R.string.ranking_detail_bookmark),
        bookmarked = stringResource(R.string.ranking_detail_bookmarked),
        download = stringResource(R.string.ranking_detail_download),
        comments = stringResource(R.string.ranking_detail_comments),
    )

    val notificationHostState = rememberNotificationHostState()
    UiMessageEffect(viewModel.message, notificationHostState)

    Scaffold(
        snackbarHost = { NotificationHost(notificationHostState) },
        topBar = {
            TopAppBar(
                title = { AdaptiveContentTitle(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    RankingDatePickerButton(
                        selectedDate = selectedDate,
                        onSelectDate = viewModel::selectDate,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        if (paneEnabled) {
            ListDetailOverlay(
                selected = selected,
                onClose = { selected = null },
                modifier = Modifier.padding(padding),
                listContent = { listMax ->
                    AdaptiveContentBox(maxWidth = listMax) {
                        RankingList(
                            modes = viewModel.modes,
                            onModeSelect = viewModel::onPageSelected,
                            stateFor = viewModel::stateFor,
                            // 日期变更时换复合键，让 RankingList 绑定对应日期的 PagedState
                            stateKey = selectedDate.orEmpty(),
                            onRetry = viewModel::retry,
                            onLoadMore = viewModel::loadMore,
                            emptyText = filteredEmptyText,
                            filter = filter,
                            filterKey = filterKey,
                            filteredEmptyText = filteredEmptyText,
                            skeleton = { RankingIllustSkeleton(gridMinColumnWidth = RANKING_GRID_MIN_COLUMN_WIDTH) },
                            gridMinColumnWidth = RANKING_GRID_MIN_COLUMN_WIDTH,
                            listHeader = { EraHeader(selectedDate, eraChips, viewModel) },
                        ) { item, rank ->
                            IllustCard(
                                rank = rank,
                                coverHeight = 220.dp,
                                illust = item,
                                onClick = { selected = item },
                                onToggleFavorite = { fav -> viewModel.toggleIllustFavorite(item.id, fav) },
                                onOpenAuthor = { item.user?.id?.let(onOpenUser) },
                                onTagClick = onSearchTag,
                            )
                        }
                    }
                },
                detailPane = {
                    IllustDetailPane(
                        selectedId = selected?.id,
                        strings = detailStrings,
                        placeholder = stringResource(R.string.ranking_preview_placeholder),
                        onOpenUser = onOpenUser,
                        onOpenViewer = onOpenViewer,
                        comments = { onBackToDetail ->
                            CommentPane(
                                commentVm = commentVm,
                                onOpenUser = onOpenUser,
                                onBackToDetail = onBackToDetail,
                            )
                        },
                        onOpenComments = { selected?.let { commentVm.switchTo(CommentTarget.ILLUST, it.id) } },
                        viewModel = detailVm,
                        onSearchTag = onSearchTag,
                    )
                },
            )
        } else {
            RankingList(
                modes = viewModel.modes,
                onModeSelect = viewModel::onPageSelected,
                stateFor = viewModel::stateFor,
                stateKey = selectedDate.orEmpty(),
                onRetry = viewModel::retry,
                onLoadMore = viewModel::loadMore,
                modifier = Modifier.padding(padding),
                emptyText = filteredEmptyText,
                filter = filter,
                filterKey = filterKey,
                filteredEmptyText = filteredEmptyText,
                skeleton = { RankingIllustSkeleton(gridMinColumnWidth = RANKING_GRID_MIN_COLUMN_WIDTH) },
                gridMinColumnWidth = RANKING_GRID_MIN_COLUMN_WIDTH,
                listHeader = { EraHeader(selectedDate, eraChips, viewModel) },
            ) { item, rank ->
                IllustCard(
                    rank = rank,
                    coverHeight = 220.dp,
                    illust = item,
                    onClick = { onOpenIllust(item.id) },
                    onToggleFavorite = { fav -> viewModel.toggleIllustFavorite(item.id, fav) },
                    onOpenAuthor = { item.user?.id?.let(onOpenUser) },
                    onTagClick = onSearchTag,
                )
            }
        }
    }
}

@Composable
private fun EraHeader(
    selectedDate: String?,
    eraChips: Boolean,
    viewModel: FilteredRankingViewModel,
) {
    if (eraChips) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            listOf(
                "2010" to "2010-12-31",
                "2015" to "2015-12-31",
                "2020" to "2020-12-31",
                "2024" to "2024-12-31",
            ).forEach { (label, date) ->
                Surface(
                    color = if (selectedDate == date) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    shape = AppShapes.pill,
                    modifier = Modifier.clip(AppShapes.pill).clickable { viewModel.selectDate(date) },
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selectedDate == date) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    )
                }
            }
        }
    }
    selectedDate?.let { date ->
        RankingDateChipRow(
            date = date,
            onSelectDate = viewModel::selectDate,
            onClear = { viewModel.selectDate(null) },
        )
    }
}

@Composable
fun AiRankingRoute(
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenViewer: (Long, Int) -> Unit,
    onSearchTag: (String) -> Unit,
    viewModel: AiRankingViewModel = hiltViewModel(),
) {
    FilteredRankingScreen(
        title = stringResource(R.string.ranking_ai_title),
        // 服务端 mode=day_ai 已完成 AI 过滤，常规榜不含 AI 作品，客户端无需再滤
        filter = { true },
        filterKey = "ai",
        filteredEmptyText = stringResource(R.string.ranking_ai_empty),
        eraChips = false,
        viewModel = viewModel,
        onBack = onBack,
        onOpenIllust = onOpenIllust,
        onOpenUser = onOpenUser,
        onOpenViewer = onOpenViewer,
        onSearchTag = onSearchTag,
    )
}

@Composable
fun WallpaperRankingRoute(
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenViewer: (Long, Int) -> Unit,
    onSearchTag: (String) -> Unit,
    viewModel: PeriodRankingViewModel = hiltViewModel(),
) {
    FilteredRankingScreen(
        title = stringResource(R.string.ranking_wallpaper_title),
        filter = { it.width > it.height && it.width >= 1920 },
        filterKey = "wallpaper",
        filteredEmptyText = stringResource(R.string.ranking_wallpaper_empty),
        eraChips = false,
        viewModel = viewModel,
        onBack = onBack,
        onOpenIllust = onOpenIllust,
        onOpenUser = onOpenUser,
        onOpenViewer = onOpenViewer,
        onSearchTag = onSearchTag,
    )
}

@Composable
fun EraRankingRoute(
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenViewer: (Long, Int) -> Unit,
    onSearchTag: (String) -> Unit,
    viewModel: PeriodRankingViewModel = hiltViewModel(),
) {
    FilteredRankingScreen(
        title = stringResource(R.string.ranking_era_title),
        filter = { true },
        filterKey = "era",
        filteredEmptyText = stringResource(R.string.ranking_era_empty),
        eraChips = true,
        viewModel = viewModel,
        onBack = onBack,
        onOpenIllust = onOpenIllust,
        onOpenUser = onOpenUser,
        onOpenViewer = onOpenViewer,
        onSearchTag = onSearchTag,
    )
}
