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
import androidx.compose.material3.Text
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.network.comment.CommentListViewModel
import com.pixiv.reader.core.network.illust.IllustViewModel
import com.pixiv.reader.core.ui.component.card.RankingIllustCard
import com.pixiv.reader.core.ui.component.detail.IllustDetailPane
import com.pixiv.reader.core.ui.component.detail.IllustDetailStrings
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentTitle
import com.pixiv.reader.core.ui.component.layout.ListDetailOverlay
import com.pixiv.reader.core.ui.component.layout.isDetailPaneEnabled
import com.pixiv.reader.core.ui.component.list.RankingDateChipRow
import com.pixiv.reader.core.ui.component.list.RankingDatePickerButton
import com.pixiv.reader.core.ui.component.list.RankingIllustSkeleton
import com.pixiv.reader.core.ui.component.list.RankingList

/**
 * 插画排行榜全屏页：分段 Tab + 左右滑动切换（复用通用 [RankingList]）。
 *
 * 平板（内容区 ≥704dp，[isDetailPaneEnabled]）：Master-Detail 双栏——列表先全宽浏览，
 * 点击排名行后列表左移让位 + 右侧详情 pane 滑入（[ListDetailOverlay] + [IllustDetailPane]，
 * 评论内嵌 pane，返回键三级导航）；小屏退化单栏，点击行直接全屏跳详情。
 *
 * 每段数据由 ViewModel 内独立 PagedState 承载（RankingList 按段 collect），滑动切回已加载段
 * 不重复请求、无过渡动画。
 *
 * 支持按日期回看历史榜单：TopAppBar 日历入口选日期（仅昨天及更早），TabRow 上方日期 chip
 * 显示/清除；「mode × 日期」各段独立缓存，切回已看过的日期不重复请求。
 *
 * @param onBack 返回
 * @param onOpenIllust 点击排名行打开插画/漫画详情（小屏单栏路径）
 * @param onOpenUser 点击作者打开用户主页
 * @param onOpenViewer 点击图片打开全屏查看器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IllustRankingRoute(
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenViewer: (Long, Int) -> Unit,
    viewModel: IllustRankingViewModel = hiltViewModel(),
) {
    val notificationHostState = rememberNotificationHostState()
    UiMessageEffect(viewModel.message, notificationHostState)
    // 日期筛选状态（null = 最新榜）：驱动 TopAppBar 入口着色与 TabRow 上方日期 chip 行
    val currentDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    // pane 模式判定（全屏页无 NavigationRail，不减 rail 宽）——顶层捕获，
    // 与 ListDetailOverlay 内部判定一致；点击分流用同一值
    val paneEnabled = isDetailPaneEnabled(subtractRail = false)
    var selected by remember { mutableStateOf<Illust?>(null) }
    // 详情/评论 ViewModel（同一 backstack entry 作用域），pane 内部按选中项 switchTo
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
                    AdaptiveContentTitle(stringResource(R.string.illust_ranking_title))
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
                    // 日期筛选入口：查看过去某天的历史榜单（替换原空 MoreVert 占位）
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
                            emptyText = stringResource(R.string.illust_ranking_empty),
                            skeleton = { RankingIllustSkeleton() },
                            gridMinColumnWidth = 200.dp,
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
                            RankingIllustCard(
                                rank = rank,
                                illust = item,
                                // 点击分流：pane 启用 → 进右栏；否则全屏跳转（paneEnabled 顶层捕获）
                                onClick = { if (paneEnabled) selected = item else onOpenIllust(item.id) },
                                onToggleFavorite = { fav -> viewModel.toggleIllustFavorite(item.id, fav) },
                                onOpenAuthor = { item.user?.id?.let(onOpenUser) },
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
                        placeholder = stringResource(R.string.manga_ranking_preview_placeholder),
                        onClose = { selected = null },
                        onOpenUser = onOpenUser,
                        onOpenViewer = onOpenViewer,
                        commentVm = commentVm,
                        viewModel = detailVm,
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
                emptyText = stringResource(R.string.illust_ranking_empty),
                skeleton = { RankingIllustSkeleton() },
                gridMinColumnWidth = 200.dp,
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
                RankingIllustCard(
                    rank = rank,
                    illust = item,
                    onClick = { onOpenIllust(item.id) },
                    onToggleFavorite = { fav -> viewModel.toggleIllustFavorite(item.id, fav) },
                    onOpenAuthor = { item.user?.id?.let(onOpenUser) },
                )
            }
        }
    }
}
