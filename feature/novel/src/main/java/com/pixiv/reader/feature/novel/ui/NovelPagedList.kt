package com.pixiv.reader.feature.novel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.ui.component.card.NovelCard
import com.pixiv.reader.core.ui.component.card.toCardData
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.list.LoadMoreItem
import com.pixiv.reader.core.ui.theme.Spacing

/**
 * 小说通用列表（推荐/关注页共用）：三态 + 下拉刷新 + 触底自动加载（LoadMoreItem 进入可视区自动续载下一页）。
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
    onOpenUser: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    val listState = rememberLazyListState()

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
                contentPadding = PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.smPlus),
            ) {
                if (header != null) {
                    item(key = "list_header") { header() }
                }
                items(items, key = { it.id }) { novel ->
                    NovelCard(
                        novel = novel.toCardData(),
                        onClick = { onOpenNovel(novel.id) },
                        onOpenAuthor = { novel.user?.id?.let(onOpenUser) },
                        onToggleFavorite = { fav -> onToggleFavorite(novel.id, fav) },
                        onTagClick = onSearchTag,
                        onSeriesClick = { novel.series?.id?.let(onOpenSeries) },
                    )
                }
                if (hasMore) {
                    item(key = "load_more") {
                        LoadMoreItem(
                            isLoadingMore = isLoadingMore,
                            onLoadMore = onLoadMore,
                        )
                    }
                }
            }
        }
    }
}
