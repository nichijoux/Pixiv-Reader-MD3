package com.pixiv.reader.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.pixiv.api.model.Illust
import com.pixiv.api.model.TrendingTag
import com.pixiv.reader.core.comment.state.CommentListViewModel
import com.pixiv.reader.core.comment.state.CommentTarget
import com.pixiv.reader.core.comment.ui.CommentPane
import com.pixiv.reader.core.network.illust.IllustViewModel
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.ui.component.detail.IllustDetailPane
import com.pixiv.reader.core.ui.component.detail.IllustDetailStrings
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.PixivHaptics
import com.pixiv.reader.core.ui.component.feedback.rememberHapticCue
import com.pixiv.reader.core.ui.component.grid.IllustWaterfallGrid
import com.pixiv.reader.core.ui.component.grid.IllustWaterfallSkeleton
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.layout.ListDetailOverlay
import com.pixiv.reader.core.ui.component.layout.isDetailPaneEnabled
import com.pixiv.reader.core.ui.theme.Spacing

/**
 * 首页：推荐瀑布流 + 热门标签 + 关注流。
 * [modifier] 是搜索框共享元素修饰（MainShell 在 NavHost 过渡内构造，用于 hero）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeRoute(
    onOpenSearch: () -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenViewer: (Long, Int) -> Unit,
    modifier: Modifier = Modifier,
    // 主壳「再次点击当前 Tab」回顶信号（计数 key，变化即滚回顶部）
    reselectKey: Int = 0,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val trendingTags by viewModel.trendingTags.collectAsStateWithLifecycle()
    // 平板详情 pane 的选中 id；手机端不启用恒为 null
    var selectedIllustId by rememberSaveable { mutableStateOf<Long?>(null) }
    val pagerState = rememberPagerState(initialPage = tab.ordinal, pageCount = { HomeTab.entries.size })
    val scope = rememberCoroutineScope()
    val haptic = rememberHapticCue()

    // 左右滑页与 VM 当前页签双向同步，保证关注流按需首载与状态一致
    LaunchedEffect(pagerState.currentPage) {
        val current = HomeTab.entries[pagerState.currentPage]
        if (current != tab) {
            viewModel.selectTab(current)
        }
    }
    LaunchedEffect(tab) {
        if (pagerState.currentPage != tab.ordinal && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(tab.ordinal)
        }
    }

    // 再次点击底部首页 Tab 仅回顶当前可见子页，避免影响另一页滚动进度
    var lastReselectKey by remember { mutableIntStateOf(reselectKey) }
    var recommendReselectKey by remember { mutableIntStateOf(0) }
    var followReselectKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(reselectKey) {
        if (reselectKey != lastReselectKey) {
            lastReselectKey = reselectKey
            if (pagerState.currentPage == HomeTab.RECOMMEND.ordinal) {
                recommendReselectKey++
            } else {
                followReselectKey++
            }
        }
    }

    val motionScheme = MaterialTheme.motionScheme
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = {
                                val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                                (slideInVertically(motionScheme.fastSpatialSpec<IntOffset>()) { it / 3 * dir } +
                                    fadeIn(motionScheme.fastEffectsSpec())) togetherWith
                                    (slideOutVertically(motionScheme.fastEffectsSpec<IntOffset>()) { -it / 3 * dir } +
                                        fadeOut(motionScheme.fastEffectsSpec()))
                            },
                            label = "homeTitleSwitch",
                        ) { currentTab ->
                            Text(
                                text = if (currentTab == HomeTab.RECOMMEND) {
                                    stringResource(R.string.home_recommend)
                                } else {
                                    stringResource(R.string.home_follow)
                                },
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        HomeSearchBar(
                            onClick = onOpenSearch,
                            modifier = modifier.weight(1f),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenNotifications) {
                        Icon(
                            Icons.Filled.Notifications,
                            contentDescription = stringResource(R.string.home_cd_notifications),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = Modifier.fillMaxSize(),
        // Scaffold 不垫高内容（平板导航栏区域留给列表/详情直通）；手机底部导航栏由壳层 bottomBar 承担
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        ListDetailOverlay(
            selected = selectedIllustId,
            onClose = { selectedIllustId = null },
            // 消费已应用的 padding，pane 内 navigationBarsPadding/imePadding 按剩余可见 inset 自适应
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            listContent = { listMax ->
                // 限宽跟随 pane 状态（未选中 760 / 选中让位）
                AdaptiveContentBox(maxWidth = listMax) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            contentPadding = PaddingValues(horizontal = Spacing.mdPlus, vertical = Spacing.xs),
                        ) {
                            item {
                                FilterChip(
                                    selected = tab == HomeTab.RECOMMEND,
                                    onClick = {
                                        if (tab != HomeTab.RECOMMEND) {
                                            haptic(PixivHaptics.HapticCue.SELECT)
                                            viewModel.selectTab(HomeTab.RECOMMEND)
                                            scope.launch {
                                                pagerState.animateScrollToPage(HomeTab.RECOMMEND.ordinal)
                                            }
                                        }
                                    },
                                    label = { Text(stringResource(R.string.home_for_you)) },
                                )
                            }
                            item {
                                FilterChip(
                                    selected = tab == HomeTab.FOLLOW,
                                    onClick = {
                                        if (tab != HomeTab.FOLLOW) {
                                            haptic(PixivHaptics.HapticCue.SELECT)
                                            viewModel.selectTab(HomeTab.FOLLOW)
                                            scope.launch {
                                                pagerState.animateScrollToPage(HomeTab.FOLLOW.ordinal)
                                            }
                                        }
                                    },
                                    label = { Text(stringResource(R.string.home_follow)) },
                                )
                            }
                            items(
                                trendingTags,
                                key = { it.tag.orEmpty() + it.translated_name.orEmpty() }) { tag ->
                                AssistChip(
                                    onClick = { onSearchTag(tag.displayName()) },
                                    label = { Text(tag.displayName()) },
                                )
                            }
                        }

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        ) { page ->
                            val pageTab = HomeTab.entries[page]
                            val paged = if (pageTab == HomeTab.RECOMMEND) {
                                viewModel.recommendPaged
                            } else {
                                viewModel.followingPaged
                            }
                            HomeFeedContent(
                                pageTab = pageTab,
                                activeTab = tab,
                                paged = paged,
                                viewModel = viewModel,
                                onOpenIllust = onOpenIllust,
                                onOpenUser = onOpenUser,
                                onSelectIllust = { selectedIllustId = it },
                                scrollToTopKey = if (pageTab == HomeTab.RECOMMEND) {
                                    recommendReselectKey
                                } else {
                                    followReselectKey
                                },
                                onSearchTag = onSearchTag,
                            )
                        }
                    }
                }
            },
            detailPane = {
                // 同一 backstack entry 作用域
                val detailVm: IllustViewModel = hiltViewModel()
                val commentVm: CommentListViewModel = hiltViewModel()
                IllustDetailPane(
                    selectedId = selectedIllustId,
                    strings = IllustDetailStrings(
                        loadRetry = stringResource(R.string.home_illust_load_retry),
                        fullscreen = stringResource(R.string.home_illust_fullscreen),
                        statView = stringResource(R.string.home_illust_stat_view),
                        statBookmark = stringResource(R.string.home_illust_stat_bookmark),
                        statPages = stringResource(R.string.home_illust_stat_pages),
                        expand = stringResource(R.string.home_illust_expand),
                        collapse = stringResource(R.string.home_illust_collapse),
                        follow = stringResource(R.string.home_illust_follow),
                        followed = stringResource(R.string.home_illust_followed),
                        related = stringResource(R.string.home_illust_related),
                        bookmark = stringResource(R.string.home_illust_bookmark),
                        bookmarked = stringResource(R.string.home_illust_bookmarked),
                        download = stringResource(R.string.home_illust_download),
                        comments = stringResource(R.string.home_illust_comments),
                    ),
                    placeholder = stringResource(R.string.home_pane_placeholder),
                    onOpenUser = onOpenUser,
                    onOpenViewer = onOpenViewer,
                    comments = { onBackToDetail ->
                        CommentPane(
                            commentVm = commentVm,
                            onOpenUser = onOpenUser,
                            onBackToDetail = onBackToDetail,
                        )
                    },
                    onOpenComments = { selectedIllustId?.let { commentVm.switchTo(CommentTarget.ILLUST, it) } },
                    viewModel = detailVm,
                )
            },
        )
    }
}

private enum class HomeFeedPhase { LOADING, ERROR, EMPTY, CONTENT }

/**
 * 推荐 / 关注两 Tab 共用（原双胞胎实现合并）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeFeedContent(
    pageTab: HomeTab,
    activeTab: HomeTab,
    paged: PagedState<Illust>,
    viewModel: HomeViewModel,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onSelectIllust: (Long) -> Unit,
    scrollToTopKey: Int = 0,
    onSearchTag: (String) -> Unit = {},
) {
    // pane 启用判定（点击分流用；回调 lambda 非 composable 上下文，需在此捕获）
    val detailPaneEnabled = isDetailPaneEnabled()
    val items by paged.items.collectAsStateWithLifecycle()
    val isLoading by paged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by paged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by paged.hasMore.collectAsStateWithLifecycle()
    val error by paged.error.collectAsStateWithLifecycle()
    val globalRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isRefreshing = globalRefreshing && activeTab == pageTab

    // 下拉刷新手势起始触觉：仅 false→true 时响（回落不响）
    val haptic = rememberHapticCue()
    var wasRefreshing by remember { mutableStateOf(isRefreshing) }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing && !wasRefreshing) {
            haptic(PixivHaptics.HapticCue.GESTURE_START)
        }
        wasRefreshing = isRefreshing
    }

    val phase = when {
        // reset 后 items 清空也走骨架，替代全屏转圈
        (isLoading || isRefreshing) && items.isEmpty() -> HomeFeedPhase.LOADING
        error != null && items.isEmpty() -> HomeFeedPhase.ERROR
        items.isEmpty() -> HomeFeedPhase.EMPTY
        else -> HomeFeedPhase.CONTENT
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.pullRefresh(pageTab) },
        modifier = Modifier.fillMaxSize(),
    ) {
        Crossfade(
            targetState = phase,
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "homeFeedPhase",
        ) { currentPhase ->
            when (currentPhase) {
                HomeFeedPhase.LOADING -> IllustWaterfallSkeleton()
                HomeFeedPhase.ERROR -> ErrorBox(
                    message = error.orEmpty(),
                    onRetry = { viewModel.retry(pageTab) },
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
                // 可滚动 → 空态也能触发下拉刷新
                HomeFeedPhase.EMPTY -> EmptyBox(
                    text = stringResource(R.string.home_empty),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )

                HomeFeedPhase.CONTENT -> IllustWaterfallGrid(
                    illusts = items,
                    // 平板（pane 启用）→ 选中进右栏详情；手机 → 全屏路由跳转
                    onItemClick = { id -> if (detailPaneEnabled) onSelectIllust(id) else onOpenIllust(id) },
                    onLoadMore = { viewModel.loadMore(pageTab) },
                    hasMore = hasMore,
                    isLoadingMore = isLoadingMore,
                    error = error,
                    onToggleFavorite = { id, fav -> viewModel.toggleIllustFavorite(id, fav) },
                    onOpenUser = onOpenUser,
                    onTagClick = onSearchTag,
                    scrollToTopKey = scrollToTopKey,
                )
            }
        }
    }
}

private fun TrendingTag.displayName(): String = translated_name ?: tag ?: ""
