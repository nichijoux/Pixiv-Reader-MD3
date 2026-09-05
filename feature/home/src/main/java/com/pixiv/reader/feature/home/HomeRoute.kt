package com.pixiv.reader.feature.home

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.TrendingTag
import com.pixiv.reader.core.network.comment.CommentListViewModel
import com.pixiv.reader.core.network.illust.IllustViewModel
import com.pixiv.reader.core.ui.component.detail.IllustDetailPane
import com.pixiv.reader.core.ui.component.detail.IllustDetailStrings
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.grid.IllustWaterfallGrid
import com.pixiv.reader.core.ui.component.grid.IllustWaterfallSkeleton
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.layout.ListDetailOverlay
import com.pixiv.reader.core.ui.component.layout.isDetailPaneEnabled
import com.pixiv.reader.core.ui.theme.Spacing

/**
 * 首页：推荐瀑布流 + 热门标签 + 关注流。
 *
 * @param onOpenSearch 点击搜索框跳转发现页
 * @param onSearchTag 点击热门标签跳发现页搜索该标签（对齐小说页约定：传显示名）
 * @param modifier 搜索框共享元素修饰（MainShell 在 NavHost 过渡内构造，hero 过渡用；默认空）
 * @param onOpenIllust 点击作品卡片打开详情
 * @param onOpenUser 点击作者行打开用户主页
 * @param onOpenNotifications 打开通知中心（首页右上角铃铛）
 * @param onOpenViewer 打开全屏查看器（平板详情 pane 图片点击）
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
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val trendingTags by viewModel.trendingTags.collectAsStateWithLifecycle()
    // Master-Detail：选中作品 id（平板详情 pane；手机端不启用恒为 null 不生效）
    var selectedIllustId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // 标题 + 搜索框同行（搜索框占中间剩余宽度，与右侧铃铛对齐）。
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (tab == HomeTab.RECOMMEND) {
                                stringResource(R.string.home_recommend)
                            } else {
                                stringResource(R.string.home_follow)
                            },
                            style = MaterialTheme.typography.titleLarge,
                        )
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
        // 沉浸式底部：不再由 Scaffold 垫高内容（平板上系统导航栏区域留给列表/详情直通）；
        // 手机端底部导航栏由壳层（AdaptiveNavScaffold）的 bottomBar 承担，行为不变
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        // 平板 Master-Detail：主列表左移 + 右侧详情 pane（手机不启用，退化为主列表原样）
        ListDetailOverlay(
            selected = selectedIllustId,
            onClose = { selectedIllustId = null },
            // 消费已应用的 padding，pane 内 navigationBarsPadding/imePadding 按剩余可见 inset 自适应
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            listContent = { listMax ->
                // 主列表限宽跟随 pane 状态动态变化（未选中 760 / 选中让位）
                AdaptiveContentBox(maxWidth = listMax) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        // 分区 + 热门标签
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            contentPadding = PaddingValues(horizontal = Spacing.mdPlus, vertical = Spacing.xs),
                        ) {
                            item {
                                FilterChip(
                                    selected = tab == HomeTab.RECOMMEND,
                                    onClick = { viewModel.selectTab(HomeTab.RECOMMEND) },
                                    label = { Text(stringResource(R.string.home_for_you)) },
                                )
                            }
                            item {
                                FilterChip(
                                    selected = tab == HomeTab.FOLLOW,
                                    onClick = { viewModel.selectTab(HomeTab.FOLLOW) },
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

                        when (tab) {
                            HomeTab.RECOMMEND -> RecommendContent(
                                viewModel, onOpenIllust, onOpenUser,
                                onSelectIllust = { selectedIllustId = it },
                            )
                            HomeTab.FOLLOW -> FollowContent(
                                viewModel, onOpenIllust, onOpenUser,
                                onSelectIllust = { selectedIllustId = it },
                            )
                        }
                    }
                }
            },
            detailPane = {
                // 右侧详情 pane：内嵌 IllustViewModel + CommentListViewModel（同一 backstack entry 作用域）
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
                    onClose = { selectedIllustId = null },
                    onOpenUser = onOpenUser,
                    onOpenViewer = onOpenViewer,
                    commentVm = commentVm,
                    viewModel = detailVm,
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecommendContent(
    viewModel: HomeViewModel,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onSelectIllust: (Long) -> Unit,
) {
    // pane 启用判定（点击分流用；回调 lambda 非 composable 上下文，需在此捕获）
    val detailPaneEnabled = isDetailPaneEnabled()
    val items by viewModel.recommendPaged.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.recommendPaged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.recommendPaged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.recommendPaged.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.recommendPaged.error.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::pullRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            // 首载 / 下拉刷新（reset 后 items 清空）→ 骨架占位，替代全屏转圈
            (isLoading || isRefreshing) && items.isEmpty() -> IllustWaterfallSkeleton()
            error != null && items.isEmpty() -> ErrorBox(
                message = error.orEmpty(),
                onRetry = viewModel::retry,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )

            else -> IllustWaterfallGrid(
                illusts = items,
                // 平板（pane 启用）→ 选中进右栏详情；手机 → 全屏路由跳转
                onItemClick = { id -> if (detailPaneEnabled) onSelectIllust(id) else onOpenIllust(id) },
                onLoadMore = viewModel::loadMore,
                hasMore = hasMore,
                isLoadingMore = isLoadingMore,
                onToggleFavorite = { id, fav -> viewModel.toggleIllustFavorite(id, fav) },
                onOpenUser = onOpenUser,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FollowContent(
    viewModel: HomeViewModel,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onSelectIllust: (Long) -> Unit,
) {
    // pane 启用判定（点击分流用；回调 lambda 非 composable 上下文，需在此捕获）
    val detailPaneEnabled = isDetailPaneEnabled()
    val items by viewModel.followingPaged.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.followingPaged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.followingPaged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.followingPaged.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.followingPaged.error.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::pullRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            // 首载 / 下拉刷新（reset 后 items 清空）→ 骨架占位，替代全屏转圈
            (isLoading || isRefreshing) && items.isEmpty() -> IllustWaterfallSkeleton()
            error != null && items.isEmpty() -> ErrorBox(
                message = error.orEmpty(),
                onRetry = viewModel::retry,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )

            else -> IllustWaterfallGrid(
                illusts = items,
                // 平板（pane 启用）→ 选中进右栏详情；手机 → 全屏路由跳转
                onItemClick = { id -> if (detailPaneEnabled) onSelectIllust(id) else onOpenIllust(id) },
                onLoadMore = viewModel::loadMore,
                hasMore = hasMore,
                isLoadingMore = isLoadingMore,
                onToggleFavorite = { id, fav -> viewModel.toggleIllustFavorite(id, fav) },
                onOpenUser = onOpenUser,
            )
        }
    }
}

private fun TrendingTag.displayName(): String = translated_name ?: tag ?: ""
