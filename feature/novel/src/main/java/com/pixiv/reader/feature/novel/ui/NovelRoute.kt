package com.pixiv.reader.feature.novel.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentTitle
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.list.loadMoreFooter
import com.pixiv.reader.core.ranking.ui.RankingBanner
import com.pixiv.reader.core.ui.component.card.SeriesCard
import com.pixiv.reader.core.ui.component.card.SeriesCardData
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.comment.state.CommentListViewModel
import com.pixiv.reader.core.network.novel.NovelViewModel
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.layout.ListDetailOverlay
import com.pixiv.reader.core.ui.component.layout.isDetailPaneEnabled
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.novel.R
import com.pixiv.reader.feature.novel.state.NovelFeedViewModel
import com.pixiv.reader.feature.novel.state.NovelSeriesViewModel
import kotlinx.coroutines.launch

/**
 * 小说 Tab：推荐 / 关注 / 追更 三段页签（PrimaryTabRow 等宽铺满）+ 推荐页排行榜入口 banner。
 *
 * 顶部与漫画 Tab 一致：`Scaffold + TopAppBar`（自带状态栏 inset），actions 为排行榜入口。
 * 推荐页：排行榜入口 banner（列表头部，随滚动）+ 推荐流；关注页：关注用户的新小说流；追更页：追更小说流。
 * 三个流各自独立 PagedState（数据驻留 VM），关注/追更首次进入才加载，切回不重复请求；
 * 均支持下拉刷新（PullToRefreshBox）。初始页由「我的-浏览设置-小说默认页」决定。
 * item 与搜索结果一致（NovelCard）：整卡（含封面）→ 详情、作者→主页、收藏、标签→搜索。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelRoute(
    reselectKey: Int = 0,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenNovelRanking: () -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenReader: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
    viewModel: NovelFeedViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    // Master-Detail：选中小说 / 系列 id（平板详情 pane，互斥；手机端不启用恒为 null 不生效）
    var selectedNovelId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedSeriesId by rememberSaveable { mutableStateOf<Long?>(null) }
    // pane 间互跳返回栈（"N:小说"/"S:系列"）：每次互跳压栈当前选中，
    // 返回键逐级还原上一个 pane，栈空才关闭 pane（防互跳链被返回键一步清空）
    var paneHistory by rememberSaveable { mutableStateOf(listOf<String>()) }
    val closePane = {
        selectedNovelId = null
        selectedSeriesId = null
        paneHistory = emptyList()
    }
    // 互跳前压栈当前选中（调用方先 push 再切换目标）
    val pushPaneHistory = {
        val current = selectedNovelId?.let { "N:$it" } ?: selectedSeriesId?.let { "S:$it" }
        if (current != null) paneHistory = paneHistory + current
    }
    // 返回键：栈非空逐级还原上一个选中（pane 间回退），栈空关闭 pane
    val popPaneOrClose = {
        val last = paneHistory.lastOrNull()
        if (last == null) {
            closePane()
        } else {
            paneHistory = paneHistory.dropLast(1)
            selectedNovelId = null
            selectedSeriesId = null
            when (last.substringBefore(":")) {
                "N" -> selectedNovelId = last.substringAfter(":").toLongOrNull()
                "S" -> selectedSeriesId = last.substringAfter(":").toLongOrNull()
            }
        }
    }
    // pane 启用判定（点击分流用；回调 lambda 非 composable 上下文，需在此捕获）
    val detailPaneEnabled = isDetailPaneEnabled()

    // 操作通知（收藏等）：collect VM message → NotificationHost
    val notificationHostState = rememberNotificationHostState()
    UiMessageEffect(viewModel.message, notificationHostState)

    // 首帧定位默认页（进程重建回偏好页；旋转保留当前页）
    var defaultTabApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!defaultTabApplied) {
            defaultTabApplied = true
            pagerState.scrollToPage(viewModel.loadDefaultTab().value.coerceIn(0, 1))
        }
    }

    // 关注/追更 Tab 首次进入才加载（数据已驻留则不重复请求）
    LaunchedEffect(pagerState.currentPage) {
        when (pagerState.currentPage) {
            1 -> viewModel.ensureFollowLoaded()
            2 -> viewModel.ensureWatchlistLoaded()
        }
    }

    Scaffold(
        snackbarHost = {
            // 沉浸式底部（contentWindowInsets=0）后 Scaffold 不再自动避开导航栏，
            // 通知条自行避让（手机端导航栏 inset 已被壳层消费，此处补 0，无双重避让）
            NotificationHost(
                notificationHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    // 平板限宽居中（与下方 AdaptiveContentBox 内容对齐）
                    AdaptiveContentTitle(stringResource(R.string.novel_title))
                },
                actions = {
                    IconButton(onClick = onOpenNovelRanking) {
                        Icon(
                            Icons.Filled.Leaderboard,
                            contentDescription = stringResource(R.string.novel_cd_ranking),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = Modifier.fillMaxSize(),
        // 沉浸式底部：不再由 Scaffold 垫高内容（平板上系统导航栏区域留给列表/详情直通）；
        // 手机端底部导航栏由壳层（AdaptiveNavScaffold）的 bottomBar 承担，行为不变
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        // 平板 Master-Detail：主列表左移 + 右侧详情 pane（手机不启用，退化为主列表原样）
        ListDetailOverlay(
            selected = selectedNovelId ?: selectedSeriesId,
            onClose = closePane,
            // 返回键沿 pane 互跳链逐级回退，栈空才关闭
            onBack = popPaneOrClose,
            // 消费已应用的 padding，内部 navigationBarsPadding/imePadding 按剩余可见 inset 自适应
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            listContent = { listMax ->
                // 主列表限宽跟随 pane 状态动态变化（未选中 760 / 选中让位）
                AdaptiveContentBox(maxWidth = listMax) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 类型页签：推荐 / 关注 / 追更（PrimaryTabRow 等宽铺满，对齐排行页约定；
                        // 选中态跟 Pager 落页，点击反向滚页）
                        PrimaryTabRow(
                            selectedTabIndex = pagerState.currentPage.coerceIn(0, 2),
                            containerColor = MaterialTheme.colorScheme.surface,
                        ) {
                            Tab(
                                selected = pagerState.currentPage == 0,
                                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                                text = { Text(stringResource(R.string.novel_tab_recommend)) },
                            )
                            Tab(
                                selected = pagerState.currentPage == 1,
                                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                                text = { Text(stringResource(R.string.novel_tab_follow)) },
                            )
                            Tab(
                                selected = pagerState.currentPage == 2,
                                onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                                text = { Text(stringResource(R.string.novel_tab_watchlist)) },
                            )
                        }
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.weight(1f),
                        ) { page ->
                            when (page) {
                                0 -> NovelFeedTab(
                                    kind = NovelFeedTabKind.RECOMMEND,
                                    scrollToTopKey = reselectKey,
                                    onOpenNovelRanking = onOpenNovelRanking,
                                    // 平板（pane 启用）→ 选中进右栏详情；手机 → 全屏路由跳转
                                    onOpenNovel = { id ->
                                        if (detailPaneEnabled) selectedNovelId = id else onOpenNovel(id)
                                    },
                                    onOpenUser = onOpenUser,
                                    onSearchTag = onSearchTag,
                                    // 系列 pane 启用 → 进右栏；否则全屏路由
                                    onOpenSeries = { id ->
                                        if (detailPaneEnabled) selectedSeriesId = id else onOpenSeries(id)
                                    },
                                    onToggleFavorite = viewModel::toggleNovelFavorite,
                                    viewModel = viewModel,
                                )
                                1 -> NovelFeedTab(
                                    kind = NovelFeedTabKind.FOLLOW,
                                    scrollToTopKey = reselectKey,
                                    onOpenNovel = { id ->
                                        if (detailPaneEnabled) selectedNovelId = id else onOpenNovel(id)
                                    },
                                    onOpenUser = onOpenUser,
                                    onSearchTag = onSearchTag,
                                    onOpenSeries = { id ->
                                        if (detailPaneEnabled) selectedSeriesId = id else onOpenSeries(id)
                                    },
                                    onToggleFavorite = viewModel::toggleNovelFavorite,
                                    viewModel = viewModel,
                                )
                                else -> NovelWatchlistTab(
                                    scrollToTopKey = reselectKey,
                                    onOpenSeries = { id ->
                                        if (detailPaneEnabled) selectedSeriesId = id else onOpenSeries(id)
                                    },
                                    onOpenUser = onOpenUser,
                                    viewModel = viewModel,
                                )
                            }
                        }
                    }
                }
            },
            detailPane = {
                // 右侧详情 pane：内嵌 NovelViewModel + CommentListViewModel（同一 backstack entry 作用域）
                val detailVm: NovelViewModel = hiltViewModel()
                val commentVm: CommentListViewModel = hiltViewModel()
                when {
                    selectedNovelId != null -> NovelDetailPane(
                        selectedId = selectedNovelId,
                        placeholder = stringResource(R.string.novel_ranking_preview_placeholder),
                        onOpenReader = onOpenReader,
                        onOpenUser = onOpenUser,
                        // 「查看完整系列」：pane 启用 → 压栈当前选中、切换到系列 pane；否则全屏路由
                        onOpenSeries = { id ->
                            if (detailPaneEnabled) {
                                pushPaneHistory()
                                selectedNovelId = null
                                selectedSeriesId = id
                            } else {
                                onOpenSeries(id)
                            }
                        },
                        commentVm = commentVm,
                        viewModel = detailVm,
                    )
                    else -> {
                        // 系列 pane：与本模块 NovelSeriesPane 直连（无槽位），分册点击切回小说 pane
                        val seriesVm: NovelSeriesViewModel = hiltViewModel()
                        NovelSeriesPane(
                            selectedId = selectedSeriesId,
                            placeholder = stringResource(R.string.novel_series_pane_placeholder),
                            onOpenNovel = { id ->
                                if (detailPaneEnabled) {
                                    pushPaneHistory()
                                    selectedSeriesId = null
                                    selectedNovelId = id
                                } else {
                                    onOpenNovel(id)
                                }
                            },
                            // 分册卡系列标题：压栈当前选中、原地切换系列（返回键可回退）
                            onOpenSeries = { id ->
                                pushPaneHistory()
                                selectedSeriesId = id
                            },
                            onOpenUser = onOpenUser,
                            onOpenCover = onOpenCover,
                            onSearchTag = onSearchTag,
                            viewModel = seriesVm,
                        )
                    }
                }
            },
        )
    }
}

/** 推荐 / 关注共用 tab 种类（差异：数据源、刷新回调、空态文案、推荐页排行榜 banner）。 */
private enum class NovelFeedTabKind { RECOMMEND, FOLLOW }

/**
 * 推荐页 / 关注页共用 tab：三态 + 下拉刷新 + 触底加载（[NovelPagedList]）。
 * 双胞胎实现（原 NovelRecommendTab / NovelFollowTab）按 [kind] 收敛差异：
 * 数据源（feed/follow 分页状态）、刷新/重试/加载更多回调、空态文案；推荐页列表头有排行榜入口 banner。
 *
 * @param kind tab 种类（RECOMMEND 推荐 / FOLLOW 关注）
 * @param scrollToTopKey 回顶信号（当前 tab 被再次点击时变化，滚回首项）
 * @param onOpenNovelRanking 打开小说排行榜（仅推荐页 banner 使用）
 * @param onOpenNovel 打开小说详情
 * @param onOpenUser 打开用户主页
 * @param onSearchTag 标签搜索
 * @param onOpenSeries 打开系列详情
 * @param onToggleFavorite 收藏 / 取消收藏（目标状态由卡片回调）
 * @param viewModel 小说信息流 ViewModel
 * @return 无返回值（渲染 Composable）
 */
@Composable
private fun NovelFeedTab(
    kind: NovelFeedTabKind,
    scrollToTopKey: Int = 0,
    onOpenNovelRanking: () -> Unit = {},
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    viewModel: NovelFeedViewModel,
) {
    // 差异收敛：数据源分页状态 / 刷新中状态（推荐 → feed，关注 → follow）
    val paged = when (kind) {
        NovelFeedTabKind.RECOMMEND -> viewModel.feed
        NovelFeedTabKind.FOLLOW -> viewModel.follow
    }
    val refreshingState = when (kind) {
        NovelFeedTabKind.RECOMMEND -> viewModel.isRefreshing
        NovelFeedTabKind.FOLLOW -> viewModel.isFollowRefreshing
    }
    val items by paged.items.collectAsStateWithLifecycle()
    val isLoading by paged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by paged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by paged.hasMore.collectAsStateWithLifecycle()
    val error by paged.error.collectAsStateWithLifecycle()
    val isRefreshing by refreshingState.collectAsStateWithLifecycle()
    val isRecommend = kind == NovelFeedTabKind.RECOMMEND
    // 排行榜入口 banner 仅推荐页有（列表头部，随滚动）
    val header: (@Composable () -> Unit)? = if (isRecommend) {
        {
            RankingBanner(
                title = stringResource(R.string.novel_ranking_banner),
                desc = stringResource(R.string.novel_ranking_banner_desc),
                onClick = onOpenNovelRanking,
            )
        }
    } else {
        null
    }

    NovelPagedList(
        items = items,
        isLoading = isLoading,
        isLoadingMore = isLoadingMore,
        hasMore = hasMore,
        error = error,
        emptyText = stringResource(if (isRecommend) R.string.novel_feed_empty else R.string.novel_follow_empty),
        isRefreshing = isRefreshing,
        onRefresh = if (isRecommend) viewModel::pullRefresh else viewModel::pullRefreshFollow,
        onRetry = if (isRecommend) viewModel::refresh else viewModel::refreshFollow,
        onLoadMore = if (isRecommend) viewModel::loadMore else viewModel::loadMoreFollow,
        onOpenNovel = onOpenNovel,
        onOpenUser = onOpenUser,
        onSearchTag = onSearchTag,
        onOpenSeries = onOpenSeries,
        onToggleFavorite = onToggleFavorite,
        scrollToTopKey = scrollToTopKey,
        header = header,
    )
}

/** 追更页：已追更的小说系列列表（复用 core:ui [SeriesCard]，下拉刷新 + 触底自动加载）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovelWatchlistTab(
    scrollToTopKey: Int = 0,
    onOpenSeries: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    viewModel: NovelFeedViewModel,
) {
    val items by viewModel.watchlist.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.watchlist.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.watchlist.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.watchlist.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.watchlist.error.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isWatchlistRefreshing.collectAsStateWithLifecycle()
    val infos by viewModel.watchlistInfos.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    // 回顶信号：首组合只记录 key 不触发；key 变化（当前 tab 被再次点击）时滚回首项
    var lastScrollKey by remember { mutableIntStateOf(scrollToTopKey) }
    LaunchedEffect(scrollToTopKey) {
        if (scrollToTopKey != lastScrollKey) {
            lastScrollKey = scrollToTopKey
            listState.animateScrollToItem(0)
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::pullRefreshWatchlist,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            // 首载 / 下拉刷新（reset 后 items 清空）→ 骨架占位
            (isLoading || isRefreshing) && items.isEmpty() -> NovelFeedSkeleton(showBannerHeader = false)
            error != null && items.isEmpty() -> ErrorBox(
                message = error,
                onRetry = viewModel::refreshWatchlist,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
            items.isEmpty() -> EmptyBox(
                stringResource(R.string.novel_watchlist_empty),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // 沉浸式底部：尾部额外避开系统导航栏（手机端 inset 已被壳层消费，补 0）
                contentPadding = PaddingValues(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    top = Spacing.lg,
                    bottom = Spacing.lg + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.smPlus),
            ) {
                items(items, key = { it.id }) { series ->
                    val info = infos[series.id]
                    SeriesCard(
                        data = SeriesCardData(
                            title = if (series.isMasked) {
                                stringResource(R.string.novel_watchlist_masked)
                            } else {
                                series.title
                            },
                            // 封面/简介/连载状态/字数来自 getNovelSeries 详情（SeriesDetailCache，与用户页同缓存）
                            coverUrl = info?.coverUrl,
                            caption = info?.caption,
                            isConcluded = info?.isConcluded,
                            partsCount = series.published_content_count,
                            totalChars = info?.totalChars ?: 0,
                            // 最近更新时间（ISO 取前 10 位 yyyy-MM-dd，与 NovelCard 一致），作者行右侧 icon 展示
                            updatedAt = series.last_published_content_datetime?.take(10),
                            authorName = series.user?.name,
                            authorAvatarUrl = series.user?.profile_image_urls?.best(),
                        ),
                        onClick = {
                            Log.d(
                                "NovelWatchlist",
                                "点击系列 id=${series.id} isMasked=${series.isMasked} title=${series.title}",
                            )
                            onOpenSeries(series.id)
                        },
                        onOpenAuthor = { series.user?.id?.let(onOpenUser) },
                    )
                }
                loadMoreFooter(hasMore = hasMore, isLoadingMore = isLoadingMore, onLoadMore = viewModel::loadMoreWatchlist)
            }
        }
    }
}
