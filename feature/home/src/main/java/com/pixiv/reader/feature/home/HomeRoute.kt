package com.pixiv.reader.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.TrendingTag
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.grid.IllustWaterfallGrid
import com.pixiv.reader.core.ui.component.grid.IllustWaterfallSkeleton
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox

/**
 * 首页：推荐瀑布流 + 热门标签 + 关注流。
 *
 * @param onOpenSearch 点击搜索框跳转发现页
 * @param onSearchTag 点击热门标签跳发现页搜索该标签（对齐小说页约定：传显示名）
 * @param modifier 搜索框共享元素修饰（MainShell 在 NavHost 过渡内构造，hero 过渡用；默认空）
 * @param onOpenIllust 点击作品卡片打开详情
 * @param onOpenUser 点击作者行打开用户主页
 * @param onOpenNotifications 打开通知中心（首页右上角铃铛）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeRoute(
    onOpenSearch: () -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val trendingTags by viewModel.trendingTags.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // 标题 + 搜索框同行（搜索框占中间剩余宽度，与右侧铃铛对齐）；
                    // 外层共享元素修饰（MainShell 构造）驱动搜索框 ↔ 发现页 hero 过渡
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
    ) { padding ->
        AdaptiveContentBox(modifier = Modifier.padding(padding)) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                // 分区 + 热门标签
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
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
                    HomeTab.RECOMMEND -> RecommendContent(viewModel, onOpenIllust, onOpenUser)
                    HomeTab.FOLLOW -> FollowContent(viewModel, onOpenIllust, onOpenUser)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecommendContent(
    viewModel: HomeViewModel,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
) {
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
                onItemClick = onOpenIllust,
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
) {
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
                onItemClick = onOpenIllust,
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
