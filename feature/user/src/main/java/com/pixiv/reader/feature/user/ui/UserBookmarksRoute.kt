package com.pixiv.reader.feature.user.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pixiv.reader.feature.user.state.UserBookmarksViewModel
import com.pixiv.reader.feature.user.R
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.list.PagedFeed
import com.pixiv.reader.core.ui.component.grid.IllustWaterfallGrid
import com.pixiv.reader.core.ui.theme.Spacing

/**
 * 用户公开收藏页：拉取指定用户公开收藏的插画瀑布流。
 *
 * @param onBack 返回
 * @param onOpenIllust 打开作品详情
 * @param onOpenUser 点击作者行打开用户主页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserBookmarksRoute(
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    viewModel: UserBookmarksViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.user_bookmarks_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
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
            PagedFeed(
                paged = viewModel.paged,
                emptyText = stringResource(R.string.user_bookmarks_empty),
                onRetry = viewModel::load,
            ) { state ->
                IllustWaterfallGrid(
                    illusts = state.items,
                    onItemClick = onOpenIllust,
                    onLoadMore = viewModel::loadMore,
                    hasMore = state.hasMore,
                    isLoadingMore = state.isLoadingMore,
                    contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.xs, bottom = Spacing.xl),
                    onOpenUser = onOpenUser,
                )
            }
        }
    }
}
