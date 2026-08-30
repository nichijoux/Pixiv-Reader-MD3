package com.pixiv.reader.feature.user.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.feature.user.state.UserFollowingViewModel
import com.pixiv.reader.feature.user.R
import com.pixiv.api.model.UserPreview
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.card.CreatorProfileCard
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.list.LoadMoreItem
import com.pixiv.reader.core.ui.component.card.toCreatorProfile
import com.pixiv.reader.core.ui.theme.Spacing

/**
 * 用户关注列表页：CreatorProfileCard 用户卡片（头像 + 代表作 + 关注按钮）。
 *
 * @param onBack 返回
 * @param onOpenUser 打开用户主页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserFollowingRoute(
    onBack: () -> Unit,
    onOpenUser: (Long) -> Unit,
    viewModel: UserFollowingViewModel = hiltViewModel(),
) {
    val items by viewModel.paged.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.paged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.paged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.paged.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.paged.error.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.user_following_title),
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
            when {
                isLoading && items.isEmpty() -> LoadingBox()
                error != null && items.isEmpty() -> ErrorBox(message = error.orEmpty(), onRetry = viewModel::load)
                items.isEmpty() -> EmptyBox(stringResource(R.string.user_following_empty))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.xs, bottom = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.smPlus),
                ) {
                    items(items, key = { it.user?.id ?: 0L }) { preview ->
                        FollowingUserCard(
                            preview = preview,
                            onClick = { preview.user?.id?.let(onOpenUser) },
                            onToggleFollow = { followed ->
                                preview.user?.id?.let { viewModel.toggleFollowUser(it, followed) }
                            },
                        )
                    }
                    if (hasMore) {
                        item(key = "load_more") {
                            LoadMoreItem(isLoadingMore = isLoadingMore, onLoadMore = viewModel::loadMore)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowingUserCard(
    preview: UserPreview,
    onClick: () -> Unit,
    onToggleFollow: (Boolean) -> Unit,
) {
    CreatorProfileCard(
        profile = preview.toCreatorProfile(),
        onToggleFollow = onToggleFollow,
        onClick = onClick,
    )
}
