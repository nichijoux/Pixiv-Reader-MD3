package com.pixiv.reader.feature.novel.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.network.comment.CommentListViewModel
import com.pixiv.reader.core.network.novel.NovelViewModel
import com.pixiv.reader.core.ui.component.card.NovelCard
import com.pixiv.reader.core.ui.component.card.toCardData
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.feedback.toNotificationType
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentTitle
import com.pixiv.reader.core.ui.component.layout.ListDetailOverlay
import com.pixiv.reader.core.ui.component.layout.isDetailPaneEnabled
import com.pixiv.reader.core.ui.component.list.RankingDateChipRow
import com.pixiv.reader.core.ui.component.list.RankingDatePickerButton
import com.pixiv.reader.core.ui.component.list.RankingList
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.novel.R
import com.pixiv.reader.feature.novel.state.NovelLanguageFilter
import com.pixiv.reader.feature.novel.state.NovelRankingViewModel
import com.pixiv.reader.feature.novel.state.labelRes
import com.pixiv.reader.feature.novel.state.matchesLanguageFilter

/**
 * 小说排行榜全屏页：分段 Tab + 左右滑动切换（复用通用 [RankingList]）。
 * 条目使用通用 [NovelCard]（上下两部分布局），封面左上角叠加排名徽标（[NovelCard.rank]）。
 *
 * 平板（内容区 ≥704dp，[isDetailPaneEnabled]）：Master-Detail 双栏——列表先全宽浏览，
 * 点击卡片后列表左移让位 + 右侧详情 pane 滑入（[ListDetailOverlay] + [NovelDetailPane]，
 * 评论/下载弹窗内嵌 pane，返回键三级导航）；小屏退化单栏，点击直接全屏跳详情。
 *
 * 每段数据由 ViewModel 内独立 PagedState 承载（RankingList 按段 collect），滑动切回已加载段
 * 不重复请求、无过渡动画。
 *
 * 支持按日期回看历史榜单：TopAppBar 日历入口（语言筛选旁）选日期（仅昨天及更早），TabRow
 * 上方日期 chip 显示/清除；「mode × 日期」各段独立缓存，语言筛选与日期筛选互不影响。
 *
 * @param onBack 返回
 * @param onOpenNovel 点击卡片（含封面）打开小说详情（小屏单栏路径）
 * @param onOpenUser 点击作者行打开用户主页
 * @param onSearchTag 点击标签搜索（排行榜页暂不跳转，由调用方决定）
 * @param onOpenReader 右栏「开始阅读」打开阅读器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelRankingRoute(
    onBack: () -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenReader: (Long) -> Unit,
    viewModel: NovelRankingViewModel = hiltViewModel(),
) {
    val notificationHostState = rememberNotificationHostState()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.message.collect { msg ->
            notificationHostState.show(
                context.getString(msg.res, *msg.args.toTypedArray()),
                type = msg.type.toNotificationType()
            )
        }
    }
    val languageFilter by viewModel.languageFilter.collectAsStateWithLifecycle()
    // 日期筛选状态（null = 最新榜）：驱动 TopAppBar 入口着色与 TabRow 上方日期 chip 行
    val currentDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }
    // pane 模式判定（全屏页无 NavigationRail，不减 rail 宽）——顶层捕获，
    // 与 ListDetailOverlay 内部判定一致；点击分流用同一值
    val paneEnabled = isDetailPaneEnabled(subtractRail = false)
    var selected by remember { mutableStateOf<Novel?>(null) }
    // 详情/评论 ViewModel（同一 backstack entry 作用域），pane 内部按选中项 switchTo；
    // 详情、评论区、下载弹窗全部由 NovelDetailPane 内嵌管理
    val detailVm: NovelViewModel = hiltViewModel()
    val commentVm: CommentListViewModel = hiltViewModel()

    Scaffold(
        snackbarHost = { NotificationHost(notificationHostState) },
        topBar = {
            TopAppBar(
                title = {
                    // 平板限宽居中（与下方 RankingList 内容对齐）
                    AdaptiveContentTitle(stringResource(R.string.novel_ranking_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.novel_cd_back),
                        )
                    }
                },
                actions = {
                    Box {
                        TextButton(
                            onClick = { menuExpanded = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        ) {
                            Icon(
                                Icons.Filled.Translate,
                                contentDescription = null,
                                modifier = Modifier.width(18.dp),
                            )
                            Text(
                                text = stringResource(languageFilter.labelRes()),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = Spacing.xs),
                            )
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = stringResource(R.string.novel_cd_language_filter),
                                modifier = Modifier.width(18.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            NovelLanguageFilter.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(option.labelRes())) },
                                    leadingIcon = {
                                        if (option == languageFilter) {
                                            Icon(Icons.Filled.Check, contentDescription = null)
                                        } else {
                                            Spacer(Modifier.width(24.dp))
                                        }
                                    },
                                    onClick = {
                                        viewModel.setLanguageFilter(option)
                                        menuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    // 日期筛选入口：查看过去某天的历史榜单（与语言筛选并列）
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
            // pane 模式：列表全宽浏览，点击后左移让位 + 右侧详情 pane 滑入（与小说 Tab 同款）
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
                            emptyText = stringResource(R.string.novel_ranking_empty),
                            filter = { novel -> novel.matchesLanguageFilter(languageFilter) },
                            filteredEmptyText = stringResource(R.string.novel_ranking_filter_empty),
                            skeleton = { NovelFeedSkeleton(showBannerHeader = false) },
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
                            NovelCard(
                                novel = item.toCardData(),
                                rank = rank,
                                // 点击分流：pane 启用 → 进右栏；否则全屏跳转（paneEnabled 顶层捕获）
                                onClick = { if (paneEnabled) selected = item else onOpenNovel(item.id) },
                                onOpenAuthor = { item.user?.id?.let(onOpenUser) },
                                onToggleFavorite = { fav -> viewModel.toggleNovelFavorite(item.id, fav) },
                                onTagClick = onSearchTag,
                                onSeriesClick = { item.series?.id?.let(onOpenSeries) },
                                modifier = Modifier.padding(bottom = Spacing.smPlus),
                            )
                        }
                    }
                },
                detailPane = {
                    // 右侧详情 pane：内嵌 NovelViewModel + CommentListViewModel（本页作用域），
                    // 详情/评论区/下载弹窗全部内嵌，返回键三级导航由 pane 自管
                    NovelDetailPane(
                        selectedId = selected?.id,
                        placeholder = stringResource(R.string.novel_ranking_preview_placeholder),
                        onClose = { selected = null },
                        onOpenReader = onOpenReader,
                        onOpenUser = onOpenUser,
                        onOpenSeries = onOpenSeries,
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
                emptyText = stringResource(R.string.novel_ranking_empty),
                filter = { novel -> novel.matchesLanguageFilter(languageFilter) },
                filteredEmptyText = stringResource(R.string.novel_ranking_filter_empty),
                skeleton = { NovelFeedSkeleton(showBannerHeader = false) },
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
                NovelCard(
                    novel = item.toCardData(),
                    rank = rank,
                    onClick = { onOpenNovel(item.id) },
                    onOpenAuthor = { item.user?.id?.let(onOpenUser) },
                    onToggleFavorite = { fav -> viewModel.toggleNovelFavorite(item.id, fav) },
                    onTagClick = onSearchTag,
                    onSeriesClick = { item.series?.id?.let(onOpenSeries) },
                    modifier = Modifier.padding(bottom = Spacing.smPlus),
                )
            }
        }
    }
}
