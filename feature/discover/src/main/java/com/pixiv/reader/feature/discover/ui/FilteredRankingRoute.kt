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


/**
 * 「排行数据 + 客户端过滤」榜单页共用骨架（AI 榜 / 壁纸榜 / 年代榜）：
 * RankingList（日/周/月分段 + 日期回看）+ 调用方过滤谓词（保留真实名次）。
 * 平板 Master-Detail 与单栏分流同常规排行榜页。
 *
 * @param title 顶栏标题
 * @param filter 客户端过滤谓词（true 保留）
 * @param filteredEmptyText 过滤后为空时的空态文案
 * @param eraChips 是否显示年代快捷 chips（年代榜专用：点击跳到该年代末的历史榜）
 * @param viewModel 共用排行 ViewModel（AI 榜用 day_ai 段、壁纸/年代榜用日/周/月段，
 *        按 backstack entry 各自实例化）
 * @param onBack 返回
 * @param onOpenIllust 点击排名行打开作品详情（小屏单栏路径）
 * @param onOpenUser 点击作者打开用户主页
 * @param onOpenViewer 点击图片打开全屏查看器
 * @param onSearchTag 标签点击跳标签搜索
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilteredRankingScreen(
    title: String,
    filter: (Illust) -> Boolean,
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
                            // 日期维度变更时按新复合键取新 PagedState 并重新加载（与漫画/插画/小说排行页同款）
                            stateKey = selectedDate.orEmpty(),
                            onRetry = viewModel::retry,
                            onLoadMore = viewModel::loadMore,
                            emptyText = filteredEmptyText,
                            filter = filter,
                            filteredEmptyText = filteredEmptyText,
                            skeleton = { RankingIllustSkeleton(gridMinColumnWidth = RANKING_GRID_MIN_COLUMN_WIDTH) },
                            gridMinColumnWidth = RANKING_GRID_MIN_COLUMN_WIDTH,
                            listHeader = { EraHeader(selectedDate, eraChips, viewModel) },
                        ) { item, rank ->
                            IllustCard(
                                rank = rank,
                                coverHeight = 220.dp,
                                illust = item,
                                // 双栏 pane 内点击仅选中右侧详情（paneEnabled 恒真分支）
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
                // 日期维度变更时按新复合键取新 PagedState 并重新加载（与漫画/插画/小说排行页同款）
                stateKey = selectedDate.orEmpty(),
                onRetry = viewModel::retry,
                onLoadMore = viewModel::loadMore,
                modifier = Modifier.padding(padding),
                emptyText = filteredEmptyText,
                filter = filter,
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

/**
 * 列表头部：年代快捷 chips（年代榜）+ 当前选中日期 chip 行。
 * 年代 chips 点击 = selectDate 到该年代末的历史榜（如 2010 → 2010-12-31）。
 *
 * @param selectedDate 当前选中的历史日期（yyyy-MM-dd），null = 最新榜
 * @param eraChips 是否显示年代快捷 chips（年代榜专用）
 * @param viewModel 共用排行 ViewModel（读选中日期 + 切换日期）
 * @return 无返回值
 */
@Composable
private fun EraHeader(
    selectedDate: String?,
    eraChips: Boolean,
    viewModel: FilteredRankingViewModel,
) {
    if (!eraChips) return
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
    selectedDate?.let { date ->
        RankingDateChipRow(
            date = date,
            onSelectDate = viewModel::selectDate,
            onClear = { viewModel.selectDate(null) },
        )
    }
}

/**
 * AI 榜：pixiv 官方 AI 生成日榜（服务端 `mode=day_ai` 已过滤出 AI 作品，保留真实名次）。
 * 常规日/周/月榜不收录 AI 作品（illust_ai_type 恒为 1），此前客户端 isAi 过滤恒为空，
 * 故必须走专用 mode；过滤谓词放行为真，空态文案仅服务端榜单为空时出现。
 */
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
        filter = { true },
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

/** 壁纸榜：常规排行数据中过滤横屏高分辨率作品（width > height 且 ≥1920px）。 */
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

/** 年代榜：带年代快捷 chips 的历史榜单（回到当年某天看榜单）。 */
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
