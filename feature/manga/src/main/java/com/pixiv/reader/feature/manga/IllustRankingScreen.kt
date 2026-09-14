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
 * 插画/漫画排行榜共享页面（两榜 UI 完全一致，仅资源与 VM 不同）：分段 Tab + 左右滑动切换
 * （复用通用 [RankingList]），支持按日期回看历史榜单（TopAppBar 日历入口 + TabRow 上方日期
 * chip，`mode × 日期` 各段独立缓存）。
 *
 * 平板（内容区 ≥704dp，[isDetailPaneEnabled]）：Master-Detail 双栏——列表先全宽浏览，
 * 点击排名行后列表左移让位 + 右侧详情 pane 滑入（[ListDetailOverlay] + [IllustDetailPane]，
 * 评论内嵌 pane，返回键三级导航）；小屏退化单栏，点击行直接全屏跳详情。
 *
 * 详情 pane 文案两榜共用 `manga_illust_*` 资源（历史上两页即同源复制，保持不变）。
 *
 * @param title 页面标题（TopAppBar，调用方 `stringResource` 解析）
 * @param emptyText 榜单空态文案
 * @param placeholderText 详情 pane 未选中占位文案
 * @param viewModel 榜单 ViewModel（漫画/插画各自的 Hilt 子类实例）
 * @param onBack 返回
 * @param onOpenIllust 点击排名行打开插画/漫画详情（小屏单栏路径）
 * @param onOpenUser 点击作者打开用户主页
 * @param onOpenViewer 点击图片打开全屏查看器
 * @param onSearchTag 标签点击跳转标签搜索（卡片标签与详情 pane 标签共用）
 * @return 无返回值
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
    // 日期筛选状态（null = 最新榜）：驱动 TopAppBar 入口着色与 TabRow 上方日期 chip 行
    val currentDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    // pane 模式判定（全屏页无 NavigationRail，不减 rail 宽）——顶层捕获，
    // 与 ListDetailOverlay 内部判定一致；点击分流用同一值
    val paneEnabled = isDetailPaneEnabled(subtractRail = false)
    var selected by remember { mutableStateOf<Illust?>(null) }
    // 详情/评论 ViewModel（同一 backstack entry 作用域，与榜单 VM 共存），
    // pane 内部按选中项 switchTo 换作品加载；无选中时不加载（init 无路由参数不预载）。
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
                    // 平板限宽居中（与下方 RankingList 内容对齐）
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
                    // 日期筛选入口：查看过去某天的历史榜单
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
            // pane 模式：列表全宽浏览，点击后左移让位 + 右侧详情 pane 滑入（与首页/作品页同款）
            ListDetailOverlay(
                selected = selected,
                onClose = { selected = null },
                modifier = Modifier.padding(padding),
                listContent = { listMax ->
                    // 外层限宽跟随让位值（RankingList 内部还有一层 760 限宽，取较小者生效）
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
                                // 日期 chip 行：TabRow 上方、限宽内容块内（pane 让位时随列表移动）
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
                                // 点击分流：pane 启用 → 进右栏；否则全屏跳转（paneEnabled 顶层捕获）
                                onClick = { if (paneEnabled) selected = item else onOpenIllust(item.id) },
                                onToggleFavorite = { fav -> viewModel.toggleIllustFavorite(item.id, fav) },
                                onOpenAuthor = { item.user?.id?.let(onOpenUser) },
                                onTagClick = onSearchTag,
                            )
                        }
                    }
                },
                detailPane = {
                    // 右侧详情 pane：内嵌 IllustViewModel + CommentListViewModel（本页作用域），
                    // 选中项变化 pane 内部 switchTo；评论内嵌、返回键三级导航由 pane 自管
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
            // 小屏（内容区 < 704dp）：单栏列表，点击行直接跳全屏详情（原行为不变）
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
                    // 日期 chip 行：TabRow 上方、限宽内容块内
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
