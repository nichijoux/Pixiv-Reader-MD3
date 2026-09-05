package com.pixiv.reader.feature.manga

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.GifBox
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Leaderboard
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.common.ui.MAX_CONTENT_WIDTH_DP
import com.pixiv.reader.core.network.comment.CommentListViewModel
import com.pixiv.reader.core.network.illust.IllustViewModel
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.ugoira.UgoiraLoader
import com.pixiv.reader.core.ui.component.detail.IllustDetailPane
import com.pixiv.reader.core.ui.component.detail.IllustDetailStrings
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.RankingBannerSkeleton
import com.pixiv.reader.core.ui.component.grid.IllustWaterfallGrid
import com.pixiv.reader.core.ui.component.grid.IllustWaterfallSkeleton
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.layout.ListDetailOverlay
import com.pixiv.reader.core.ui.component.layout.isDetailPaneEnabled
import com.pixiv.reader.core.ui.component.list.RankingBanner
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes

/**
 * 漫画 Tab：左上角切换漫画 / 插画 / 动图三种内容流，各自独立分页瀑布流 + 下拉刷新；
 * 排行榜入口 banner（网格头部）按类型显示对应榜单（漫画榜 / 插画榜）。
 *
 * @param onOpenIllust 点击作品卡打开详情
 * @param onOpenMangaRanking 点击漫画排行榜 banner / 顶栏奖杯打开漫画排行榜全屏页
 * @param onOpenIllustRanking 点击插画排行榜 banner / 顶栏奖杯打开插画排行榜全屏页
 * @param onOpenUser 点击作者行打开用户主页
 * @param onOpenViewer 打开全屏查看器（平板详情 pane 图片点击）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaRoute(
    onOpenIllust: (Long) -> Unit,
    onOpenMangaRanking: () -> Unit,
    onOpenIllustRanking: () -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenViewer: (Long, Int) -> Unit,
    viewModel: MangaViewModel = hiltViewModel(),
) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    // Master-Detail：选中作品 id（平板详情 pane；手机端不启用恒为 null 不生效）
    var selectedIllustId by rememberSaveable { mutableStateOf<Long?>(null) }
    // pane 启用判定（点击分流用；回调 lambda 非 composable 上下文，需在此捕获）
    val detailPaneEnabled = isDetailPaneEnabled()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // 平板限宽居中（与下方 AdaptiveContentBox 内容对齐）：左上角内容类型切换
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Box(Modifier.widthIn(max = MAX_CONTENT_WIDTH_DP.dp)) {
                            var menuExpanded by remember { mutableStateOf(false) }
                            Box {
                                TextButton(
                                    onClick = { menuExpanded = true },
                                    // 切换按钮文字/图标用 onSurface（不随按钮主题色变蓝）
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                                ) {
                                    Icon(
                                        imageVector = tab.icon(),
                                        contentDescription = null,
                                        modifier = Modifier.size(Sizes.s20),
                                    )
                                    Text(
                                        text = stringResource(tab.labelRes()),
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = Spacing.xs),
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDropDown,
                                        contentDescription = stringResource(R.string.manga_cd_switch_type),
                                        modifier = Modifier.size(Sizes.s18),
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                ) {
                                    MangaContentType.entries.forEach { type ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(type.labelRes())) },
                                            leadingIcon = { Icon(type.icon(), contentDescription = null) },
                                            onClick = {
                                                viewModel.selectTab(type)
                                                menuExpanded = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                actions = {
                    // 排行榜入口按内容类型显示对应榜单（漫画榜 / 插画榜；动图无榜单页）
                    when (tab) {
                        MangaContentType.MANGA -> IconButton(onClick = onOpenMangaRanking) {
                            Icon(
                                Icons.Filled.Leaderboard,
                                contentDescription = stringResource(R.string.manga_cd_ranking),
                            )
                        }
                        MangaContentType.ILLUST -> IconButton(onClick = onOpenIllustRanking) {
                            Icon(
                                Icons.Filled.Leaderboard,
                                contentDescription = stringResource(R.string.illust_cd_ranking),
                            )
                        }
                        MangaContentType.UGOIRA -> Unit
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
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = viewModel::pullRefresh,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        when (tab) {
                            MangaContentType.MANGA -> MangaContentList(
                                paged = viewModel.recommendPaged,
                                isRefreshing = isRefreshing,
                                emptyText = stringResource(R.string.manga_empty),
                                rankingBanner = {
                                    RankingBanner(
                                        title = stringResource(R.string.manga_ranking_banner),
                                        desc = stringResource(R.string.manga_ranking_banner_desc),
                                        onClick = onOpenMangaRanking,
                                    )
                                },
                                onLoadMore = viewModel::loadMore,
                                onRetry = viewModel::retry,
                                // 平板（pane 启用）→ 选中进右栏详情；手机 → 全屏路由跳转
                                onOpenIllust = { id ->
                                    if (detailPaneEnabled) selectedIllustId = id else onOpenIllust(id)
                                },
                                onOpenUser = onOpenUser,
                                onToggleFavorite = { id, fav -> viewModel.toggleIllustFavorite(id, fav) },
                            )
                            MangaContentType.ILLUST -> MangaContentList(
                                paged = viewModel.illustPaged,
                                isRefreshing = isRefreshing,
                                emptyText = stringResource(R.string.manga_illust_empty),
                                rankingBanner = {
                                    RankingBanner(
                                        title = stringResource(R.string.illust_ranking_banner),
                                        desc = stringResource(R.string.illust_ranking_banner_desc),
                                        onClick = onOpenIllustRanking,
                                    )
                                },
                                onLoadMore = viewModel::loadMore,
                                onRetry = viewModel::retry,
                                onOpenIllust = { id ->
                                    if (detailPaneEnabled) selectedIllustId = id else onOpenIllust(id)
                                },
                                onOpenUser = onOpenUser,
                                onToggleFavorite = { id, fav -> viewModel.toggleIllustFavorite(id, fav) },
                            )
                            MangaContentType.UGOIRA -> MangaContentList(
                                paged = viewModel.ugoiraPaged,
                                isRefreshing = isRefreshing,
                                emptyText = stringResource(R.string.manga_ugoira_empty),
                                rankingBanner = null,
                                ugoiraLoader = viewModel.ugoiraLoader,
                                onLoadMore = viewModel::loadMore,
                                onRetry = viewModel::retry,
                                onOpenIllust = { id ->
                                    if (detailPaneEnabled) selectedIllustId = id else onOpenIllust(id)
                                },
                                onOpenUser = onOpenUser,
                                onToggleFavorite = { id, fav -> viewModel.toggleIllustFavorite(id, fav) },
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
                    ),
                    placeholder = stringResource(R.string.manga_ranking_preview_placeholder),
                    onOpenUser = onOpenUser,
                    onOpenViewer = onOpenViewer,
                    commentVm = commentVm,
                    viewModel = detailVm,
                )
            },
        )
    }
}

/** 内容类型切换按钮文案（@StringRes）。 */
@StringRes
private fun MangaContentType.labelRes(): Int = when (this) {
    MangaContentType.MANGA -> R.string.manga_content_manga
    MangaContentType.ILLUST -> R.string.manga_content_illust
    MangaContentType.UGOIRA -> R.string.manga_content_ugoira
}

/** 内容类型切换按钮/菜单图标。 */
private fun MangaContentType.icon(): ImageVector = when (this) {
    MangaContentType.MANGA -> Icons.Filled.Collections
    MangaContentType.ILLUST -> Icons.Filled.Image
    MangaContentType.UGOIRA -> Icons.Filled.GifBox
}

/**
 * 内容类型瀑布流：按 `PagedState` 三态渲染（骨架 / 错误 / 空 / 网格）。
 *
 * @param rankingBanner 排行榜入口（漫画/插画类型传入，作网格头部 + 骨架占位；动图不传）
 * @param ugoiraLoader 动图加载器（仅动图类型传入，卡片播放动画）
 */
@Composable
private fun MangaContentList(
    paged: PagedState<Illust>,
    isRefreshing: Boolean,
    emptyText: String,
    rankingBanner: (@Composable () -> Unit)?,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    ugoiraLoader: UgoiraLoader? = null,
) {
    val items by paged.items.collectAsStateWithLifecycle()
    val isLoading by paged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by paged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by paged.hasMore.collectAsStateWithLifecycle()
    val error by paged.error.collectAsStateWithLifecycle()

    when {
        // 首载 / 下拉刷新（reset 后 items 清空）→ 骨架占位，替代全屏转圈
        // 顶部渲染排行榜入口 banner 骨架占位（对齐真实网格 header，不随数据消失）
        (isLoading || isRefreshing) && items.isEmpty() -> IllustWaterfallSkeleton(
            header = { if (rankingBanner != null) RankingBannerSkeleton() },
        )
        error != null && items.isEmpty() -> ErrorBox(
            message = error,
            onRetry = onRetry,
            modifier = Modifier.verticalScroll(rememberScrollState()),
        )
        items.isEmpty() -> Box(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp),
            )
        }
        else -> IllustWaterfallGrid(
            illusts = items,
            onItemClick = onOpenIllust,
            onLoadMore = onLoadMore,
            hasMore = hasMore,
            isLoadingMore = isLoadingMore,
            onToggleFavorite = onToggleFavorite,
            onOpenUser = onOpenUser,
            ugoiraLoader = ugoiraLoader,
            // 排行榜入口 banner 作为网格头部（仅漫画/插画类型，随列表滚动/下拉）
            header = rankingBanner,
        )
    }
}

