package com.pixiv.reader.feature.manga

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.pixiv.reader.core.ranking.ui.RankingDateChipRow
import com.pixiv.reader.core.ranking.ui.RankingDatePickerButton
import com.pixiv.reader.core.ranking.ui.RANKING_GRID_MIN_COLUMN_WIDTH
import com.pixiv.reader.core.ranking.ui.RankingIllustSkeleton
import com.pixiv.reader.core.ranking.ui.RankingList

/**
 * 插画/漫画排行榜共享页（两榜 UI 一致，仅资源与 VM 不同）：分段 Tab + 滑动切换 + 按日期回看历史榜。
 * 平板启用 Master-Detail；详情 pane 文案两榜共用 `manga_illust_*`（历史同源复制，保持不变）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IllustRankingScreen(
    title: String,
    emptyText: String,
    placeholderText: String,
    viewModel: IllustRankingViewModelBase,
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenViewer: (Long, Int) -> Unit,
    onSearchTag: (String) -> Unit = {},
) {
    val notificationHostState = rememberNotificationHostState()
    UiMessageEffect(viewModel.message, notificationHostState)
    // null = 最新榜（驱动日历入口着色与日期 chip 行）
    val currentDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    // 全屏页无 NavigationRail，不减 rail 宽；必须与 ListDetailOverlay 内部判定一致
    val paneEnabled = isDetailPaneEnabled(subtractRail = false)
    var selected by remember { mutableStateOf<Illust?>(null) }
    // 同一 backstack entry 作用域；无选中时不加载（init 无路由参数不预载）
    val detailVm: IllustViewModel = hiltViewModel()
    val commentVm: CommentListViewModel = hiltViewModel()
    val detailStrings = IllustDetailStrings(
        loadRetry = stringResource(R.string.manga_illust_load_retry),
        fullscreen = stringResource(R.string.manga_illust_fullscreen),
        statView = stringResource(R.string.manga_illust_stat_view),
        statBookmark = stringResource(R.string.manga_illust_stat_bookmark),
        statPages = stringResource(R.string.manga_illust_stat_pages),
        expand = stringResource(R.string.manga_illust_expand),
        collapse = stringResource(R.string.manga_illust_collapse),
        follow = stringResource(R.string.manga_illust_follow),
        followed = stringResource(R.string.manga_illust_followed),
        related = stringResource(R.string.manga_illust_related),
        bookmark = stringResource(R.string.manga_illust_bookmark),
        bookmarked = stringResource(R.string.manga_illust_bookmarked),
        download = stringResource(R.string.manga_illust_download),
        comments = stringResource(R.string.manga_illust_comments),
    )
    Scaffold(
        snackbarHost = { NotificationHost(notificationHostState) },
        topBar = {
            TopAppBar(
                title = {
                    AdaptiveContentTitle(title)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.manga_cd_back),
                        )
                    }
                },
                actions = {
                    RankingDatePickerButton(
                        selectedDate = currentDate,
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
                    // RankingList 内部还有一层 760 限宽，取较小者生效
                    AdaptiveContentBox(maxWidth = listMax) {
                        RankingList(
                            modes = viewModel.modes,
                            onModeSelect = viewModel::onPageSelected,
                            stateFor = viewModel::stateFor,
                            onRetry = viewModel::retry,
                            onLoadMore = viewModel::loadMore,
                            emptyText = emptyText,
                            skeleton = { RankingIllustSkeleton(gridMinColumnWidth = RANKING_GRID_MIN_COLUMN_WIDTH) },
                            gridMinColumnWidth = RANKING_GRID_MIN_COLUMN_WIDTH,
                            stateKey = currentDate.orEmpty(),
                            listHeader = {
                                currentDate?.let { date ->
                                    RankingDateChipRow(
                                        date = date,
                                        onSelectDate = viewModel::selectDate,
                                        onClear = { viewModel.selectDate(null) },
                                    )
                                }
                            },
                        ) { item, rank ->
                            IllustCard(
                                rank = rank,
                                coverHeight = 220.dp,
                                illust = item,
                                onClick = { if (paneEnabled) selected = item else onOpenIllust(item.id) },
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
                        placeholder = placeholderText,
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
                onRetry = viewModel::retry,
                onLoadMore = viewModel::loadMore,
                modifier = Modifier.padding(padding),
                emptyText = emptyText,
                skeleton = { RankingIllustSkeleton(gridMinColumnWidth = RANKING_GRID_MIN_COLUMN_WIDTH) },
                gridMinColumnWidth = RANKING_GRID_MIN_COLUMN_WIDTH,
                stateKey = currentDate.orEmpty(),
                listHeader = {
                    currentDate?.let { date ->
                        RankingDateChipRow(
                            date = date,
                            onSelectDate = viewModel::selectDate,
                            onClear = { viewModel.selectDate(null) },
                        )
                    }
                },
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
