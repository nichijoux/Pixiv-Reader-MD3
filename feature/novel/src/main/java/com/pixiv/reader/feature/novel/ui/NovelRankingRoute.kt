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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.comment.state.CommentListViewModel
import com.pixiv.reader.core.network.novel.NovelViewModel
import com.pixiv.reader.core.ui.component.card.NovelCard
import com.pixiv.reader.core.ui.component.card.toCardData
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentTitle
import com.pixiv.reader.core.ui.component.layout.ListDetailOverlay
import com.pixiv.reader.core.ui.component.layout.isDetailPaneEnabled
import com.pixiv.reader.core.ranking.ui.RankingDateChipRow
import com.pixiv.reader.core.ranking.ui.RankingDatePickerButton
import com.pixiv.reader.core.ranking.ui.RankingList
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.novel.R
import com.pixiv.reader.feature.novel.state.NovelLanguageFilter
import com.pixiv.reader.feature.novel.state.NovelRankingViewModel
import com.pixiv.reader.feature.novel.state.labelRes
import com.pixiv.reader.feature.novel.state.matchesLanguageFilter

/**
 * 小说排行榜全屏页：分段 Tab + 滑动切换（复用 [RankingList]），卡片封面叠名次徽标。
 * 平板（内容区 ≥704dp）走 Master-Detail：列表让位 + 右栏 [NovelDetailPane]（评论/下载内嵌，
 * 返回键三级导航）；小屏单栏点击直接全屏跳详情。
 * 各段 PagedState 独立缓存：切回已加载段不重复请求、无过渡动画；日期与语言筛选互不影响。
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
    UiMessageEffect(viewModel.message, notificationHostState)
    val languageFilter by viewModel.languageFilter.collectAsStateWithLifecycle()
    // 日期筛选状态：null = 最新榜
    val currentDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }
    // 全屏页无 NavigationRail 故不减 rail 宽；顶层捕获一次，与 ListDetailOverlay 内部判定一致
    val paneEnabled = isDetailPaneEnabled(subtractRail = false)
    var selected by remember { mutableStateOf<Novel?>(null) }
    // 同一 backstack entry 作用域：pane 内部按选中项 switchTo，详情/评论/下载弹窗均由 pane 自管
    val detailVm: NovelViewModel = hiltViewModel()
    val commentVm: CommentListViewModel = hiltViewModel()

    // 单栏/双栏共用：仅 modifier 与点击分流不同
    val rankingList: @Composable (Modifier, (Novel) -> Unit) -> Unit = { listModifier, onItemClick ->
        RankingList(
            modes = viewModel.modes,
            onModeSelect = viewModel::onPageSelected,
            stateFor = viewModel::stateFor,
            onRetry = viewModel::retry,
            onLoadMore = viewModel::loadMore,
            modifier = listModifier,
            emptyText = stringResource(R.string.novel_ranking_empty),
            filter = { novel -> novel.matchesLanguageFilter(languageFilter) },
            // 过滤谓词闭包随筛选值变化：以枚举本身作为过滤结果缓存键
            filterKey = languageFilter,
            filteredEmptyText = stringResource(R.string.novel_ranking_filter_empty),
            skeleton = { NovelFeedSkeleton(showBannerHeader = false) },
            stateKey = currentDate.orEmpty(),
            listHeader = {
                // 日期 chip 行：限宽内容块内，pane 让位时随列表移动
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
                onClick = { onItemClick(item) },
                onOpenAuthor = { item.user?.id?.let(onOpenUser) },
                onToggleFavorite = { fav -> viewModel.toggleNovelFavorite(item.id, fav) },
                onTagClick = onSearchTag,
                onSeriesClick = { item.series?.id?.let(onOpenSeries) },
                modifier = Modifier.padding(bottom = Spacing.smPlus),
            )
        }
    }

    Scaffold(
        snackbarHost = { NotificationHost(notificationHostState) },
        topBar = {
            TopAppBar(
                title = {
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
                            shape = AppShapes.large,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
            // pane 模式：列表全宽浏览，点击后左移让位 + 右侧 pane 滑入
            ListDetailOverlay(
                selected = selected,
                onClose = { selected = null },
                modifier = Modifier.padding(padding),
                listContent = { listMax ->
                    // 外层限宽跟随让位值（RankingList 内部还有一层 760 限宽，取较小者生效）
                    AdaptiveContentBox(maxWidth = listMax) {
                        rankingList(Modifier) { item -> selected = item }
                    }
                },
                detailPane = {
                    NovelDetailPane(
                        selectedId = selected?.id,
                        placeholder = stringResource(R.string.novel_ranking_preview_placeholder),
                        onOpenReader = onOpenReader,
                        onOpenUser = onOpenUser,
                        onOpenSeries = onOpenSeries,
                        onSearchTag = onSearchTag,
                        commentVm = commentVm,
                        viewModel = detailVm,
                    )
                },
            )
        } else {
            // 小屏单栏：点击直接跳全屏详情
            rankingList(Modifier.padding(padding)) { onOpenNovel(it.id) }
        }
    }
}
