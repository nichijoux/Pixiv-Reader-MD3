package com.pixiv.reader.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pixivapi.model.TrendingTag
import com.pixiv.reader.core.ui.component.ErrorBox
import com.pixiv.reader.core.ui.component.IllustWaterfallGrid
import com.pixiv.reader.core.ui.component.LoadingBox

/**
 * 首页：推荐瀑布流 + 热门标签 + 关注流。
 *
 * @param onOpenSearch 点击搜索图标跳转发现页
 * @param onOpenIllust 点击作品卡片打开详情
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeRoute(
    onOpenSearch: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val trendingTags by viewModel.trendingTags.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (tab == HomeTab.RECOMMEND) "推荐" else "关注",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* 通知（P7） */ }) {
                        Icon(Icons.Filled.Notifications, contentDescription = "通知")
                    }
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 分区 + 热门标签
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            ) {
                item {
                    FilterChip(
                        selected = tab == HomeTab.RECOMMEND,
                        onClick = { viewModel.selectTab(HomeTab.RECOMMEND) },
                        label = { Text("为你推荐") },
                    )
                }
                item {
                    FilterChip(
                        selected = tab == HomeTab.FOLLOW,
                        onClick = { viewModel.selectTab(HomeTab.FOLLOW) },
                        label = { Text("关注") },
                    )
                }
                items(trendingTags, key = { it.tag.orEmpty() + it.translated_name.orEmpty() }) { tag ->
                    AssistChip(
                        onClick = { /* 点击标签跳搜索（P3） */ },
                        label = { Text(tag.displayName()) },
                    )
                }
            }

            when (tab) {
                HomeTab.RECOMMEND -> RecommendContent(viewModel, onOpenIllust)
                HomeTab.FOLLOW -> FollowContent(viewModel, onOpenIllust)
            }
        }
    }
}

@Composable
private fun RecommendContent(viewModel: HomeViewModel, onOpenIllust: (Long) -> Unit) {
    val items by viewModel.recommendPaged.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.recommendPaged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.recommendPaged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.recommendPaged.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.recommendPaged.error.collectAsStateWithLifecycle()

    when {
        isLoading && items.isEmpty() -> LoadingBox()
        error != null && items.isEmpty() -> ErrorBox(message = error.orEmpty(), onRetry = viewModel::retry)
        else -> IllustWaterfallGrid(
            illusts = items,
            onItemClick = onOpenIllust,
            onLoadMore = viewModel::loadMore,
            hasMore = hasMore,
            isLoadingMore = isLoadingMore,
            onToggleFavorite = { id, fav -> viewModel.toggleIllustFavorite(id, fav) },
        )
    }
}

@Composable
private fun FollowContent(viewModel: HomeViewModel, onOpenIllust: (Long) -> Unit) {
    val items by viewModel.followingPaged.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.followingPaged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.followingPaged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.followingPaged.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.followingPaged.error.collectAsStateWithLifecycle()

    when {
        isLoading && items.isEmpty() -> LoadingBox()
        error != null && items.isEmpty() -> ErrorBox(message = error.orEmpty(), onRetry = viewModel::retry)
        else -> IllustWaterfallGrid(
            illusts = items,
            onItemClick = onOpenIllust,
            onLoadMore = viewModel::loadMore,
            hasMore = hasMore,
            isLoadingMore = isLoadingMore,
            onToggleFavorite = { id, fav -> viewModel.toggleIllustFavorite(id, fav) },
        )
    }
}

private fun TrendingTag.displayName(): String = translated_name ?: tag ?: ""
