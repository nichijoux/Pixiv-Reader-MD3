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
import com.pixiv.reader.core.ui.component.layout.LocalNavBarBottomPadding
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.PixivHaptics
import com.pixiv.reader.core.ui.component.feedback.rememberHapticCue
import com.pixiv.reader.core.ui.component.list.LazyListPrefetchEffect
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
 * 小说 Tab：推荐 / 关注 / 追更 三段页签 + 推荐页排行榜入口 banner。
 * 三流各自独立 PagedState（数据驻留 VM），关注/追更首次进入才加载；初始页由「我的-浏览设置-小说默认页」决定。
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
    // Master-Detail：选中小说 / 系列 id（互斥；手机端不启用 pane，恒为 null）
    var selectedNovelId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedSeriesId by rememberSaveable { mutableStateOf<Long?>(null) }
    // pane 间互跳返回栈（"N:小说"/"S:系列"）：每次互跳压栈当前选中，返回键逐级还原，
    // 栈空才关闭 pane；否则互跳链会被返回键一步清空
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
    // 返回键：栈空则关闭 pane
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
    // 回调 lambda 非 composable 上下文，pane 判定需在此捕获
    val detailPaneEnabled = isDetailPaneEnabled()

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

    // 首次进入才加载（数据已驻留则不重复请求）
    LaunchedEffect(pagerState.currentPage) {
        when (pagerState.currentPage) {
            1 -> viewModel.ensureFollowLoaded()
            2 -> viewModel.ensureWatchlistLoaded()
        }
    }

    Scaffold(
        snackbarHost = {
            // 沉浸式底部（contentWindowInsets=0）后 Scaffold 不再自动避开导航栏，通知条需自行避让；
            // 手机端导航栏 inset 已被壳层消费，此处补 0，无双重避让
            NotificationHost(
                notificationHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
        topBar = {
            TopAppBar(
                title = {
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
        // 沉浸式底部：不再由 Scaffold 垫高内容（平板上系统导航栏区域留给列表直通）；
        // 手机端底部导航栏由壳层 AdaptiveNavScaffold 的 bottomBar 承担
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        // 平板 Master-Detail：主列表左移 + 右侧 pane（手机不启用，退化为主列表原样）
        ListDetailOverlay(
            selected = selectedNovelId ?: selectedSeriesId,
            onClose = closePane,
            // 返回键沿 pane 互跳链逐级回退，栈空才关闭
            onBack = popPaneOrClose,
            // 已应用 padding 需 consume，内部 navigationBarsPadding/imePadding 才能按剩余可见 inset 自适应
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            listContent = { listMax ->
                // 限宽跟随 pane 状态动态变化（未选中 760 / 选中让位）
                AdaptiveContentBox(maxWidth = listMax) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 等宽铺满；选中态跟 Pager 落页，点击反向滚页
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
                // 同一 backstack entry 作用域
                val detailVm: NovelViewModel = hiltViewModel()
                val commentVm: CommentListViewModel = hiltViewModel()
                when {
                    selectedNovelId != null -> NovelDetailPane(
                        selectedId = selectedNovelId,
                        placeholder = stringResource(R.string.novel_ranking_preview_placeholder),
                        onOpenReader = onOpenReader,
                        onOpenUser = onOpenUser,
                        onSearchTag = onSearchTag,
                        // 「查看完整系列」：pane 启用则压栈当前选中并切到系列 pane
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
                        // 与本模块 NovelSeriesPane 直连（无槽位），分册点击切回小说 pane
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
                            // 分册卡系列标题：压栈后原地切换系列（返回键可回退）
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

private enum class NovelFeedTabKind { RECOMMEND, FOLLOW }

/**
 * 推荐 / 关注共用 tab，按 [kind] 收敛数据源、刷新/重试/加载更多回调、空态文案差异
 * （推荐页列表头额外有排行榜入口 banner）。
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
    // 仅在 isRefreshing false→true 时响一次
    val haptic = rememberHapticCue()
    var wasRefreshing by remember { mutableStateOf(isRefreshing) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing && !wasRefreshing) {
            haptic(PixivHaptics.HapticCue.GESTURE_START)
        }
        wasRefreshing = isRefreshing
    }
    // 首组合只记录 key 不触发，key 变化（当前 tab 被再次点击）才滚回首项
    var lastScrollKey by remember { mutableIntStateOf(scrollToTopKey) }
    LaunchedEffect(scrollToTopKey) {
        if (scrollToTopKey != lastScrollKey) {
            lastScrollKey = scrollToTopKey
            listState.animateScrollToItem(0)
        }
    }
    LazyListPrefetchEffect(
        state = listState,
        itemCount = items.size,
        hasMore = hasMore,
        isLoadingMore = isLoadingMore,
        error = error,
        onLoadMore = viewModel::loadMoreWatchlist,
    )

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::pullRefreshWatchlist,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            // 首载与下拉刷新（reset 会清空 items）共用骨架
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
                    bottom = Spacing.lg + LocalNavBarBottomPadding.current + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
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
                            // 详情字段来自 getNovelSeries（与用户页同 SeriesDetailCache）
                            coverUrl = info?.coverUrl,
                            caption = info?.caption,
                            isConcluded = info?.isConcluded,
                            partsCount = series.published_content_count,
                            totalChars = info?.totalChars ?: 0,
                            // ISO 取前 10 位 yyyy-MM-dd（与 NovelCard 一致）
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
                loadMoreFooter(
                    hasMore = hasMore,
                    isLoadingMore = isLoadingMore,
                    onLoadMore = viewModel::loadMoreWatchlist,
                    error = error,
                )
            }
        }
    }
}
