package com.pixiv.reader.feature.discover.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pixiv.reader.core.ui.component.card.CreatorProfileCard
import com.pixiv.reader.core.ui.component.card.toCreatorProfile
import com.pixiv.reader.core.ui.component.list.PagedFeed
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.layout.BackTopAppBar
import com.pixiv.reader.core.ui.component.list.loadMoreFooter
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.discover.R
import com.pixiv.reader.feature.discover.state.UserRankingViewModel

/**
 * 画师榜（路由 `user_ranking`）：官方推荐创作者列表（CreatorProfileCard：三封面 + 头像/名/关注）。
 *
 * @param onBack 返回
 * @param onOpenUser 点击卡片 / 作者行打开用户主页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserRankingRoute(
    onBack: () -> Unit,
    onOpenUser: (Long) -> Unit,
    viewModel: UserRankingViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            BackTopAppBar(title = stringResource(R.string.ranking_user_title), onBack = onBack)
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        AdaptiveContentBox(modifier = Modifier.padding(padding)) {
            PagedFeed(
                paged = viewModel.paged,
                emptyText = stringResource(R.string.ranking_user_empty),
                onRetry = viewModel::load,
            ) { state ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = Spacing.sm,
                        bottom = Spacing.lg + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.smPlus),
                ) {
                    // key 需全列表唯一：user id 优先，缺失回退条目哈希（防推荐接口重复 id 触发 duplicate key 崩溃）
                    items(state.items, key = { "user_${it.user?.id ?: it.hashCode()}" }) { preview ->
                        val profile = preview.toCreatorProfile()
                        CreatorProfileCard(
                            profile = profile,
                            onToggleFollow = { nowFollowed ->
                                profile.id.takeIf { it != 0L }?.let { viewModel.toggleFollow(it, nowFollowed) }
                            },
                            onClick = { profile.id.takeIf { it != 0L }?.let(onOpenUser) },
                        )
                    }
                    loadMoreFooter(hasMore = state.hasMore, isLoadingMore = state.isLoadingMore, onLoadMore = viewModel::loadMore)
                }
            }
        }
    }
}
