package com.pixiv.reader.feature.novel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.card.NovelCard
import com.pixiv.reader.core.ui.component.card.NovelCardData
import com.pixiv.reader.core.ui.component.card.toCardData
import com.pixiv.reader.feature.novel.R

/**
 * 小说通用列表（推荐/关注页共用）：三态 + 下拉刷新 + 触底自动加载 + 触底手动加载。
 * 整页保持独立 LazyColumn，滚动位置各自独立；[header] 为列表首 item（随滚动，如排行榜 banner）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NovelPagedList(
    items: List<Novel>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    error: String?,
    emptyText: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
    onOpenUser: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    val listState = rememberLazyListState()

    // 滚动接近底部时自动加载下一页
    LaunchedEffect(listState, items.size, hasMore) {
        if (!hasMore || items.isEmpty()) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                if (lastVisible >= items.size - 3) onLoadMore()
            }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            // 首载 / 下拉刷新（reset 后 items 清空）→ 骨架占位，替代全屏转圈
            (isLoading || isRefreshing) && items.isEmpty() -> NovelFeedSkeleton(showBannerHeader = header != null)
            error != null && items.isEmpty() -> ErrorBox(
                message = error,
                onRetry = onRetry,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
            items.isEmpty() -> EmptyBox(emptyText, modifier = Modifier.verticalScroll(rememberScrollState()))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (header != null) {
                    item(key = "list_header") { header() }
                }
                items(items, key = { it.id }) { novel ->
                    NovelCard(
                        novel = novel.toCardData(),
                        onClick = { onOpenNovel(novel.id) },
                        onOpenCover = { (novel.image_urls?.square_medium ?: novel.image_urls?.medium)?.let(onOpenCover) },
                        onOpenAuthor = { novel.user?.id?.let(onOpenUser) },
                        onToggleFavorite = { fav -> onToggleFavorite(novel.id, fav) },
                        onTagClick = onSearchTag,
                        onSeriesClick = { novel.series?.id?.let(onOpenSeries) },
                    )
                }
                if (hasMore) {
                    item(key = "load_more") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isLoadingMore) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Text(
                                    text = stringResource(R.string.novel_load_more),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable(onClick = onLoadMore)
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
