package com.pixiv.reader.feature.discover.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.Novel
import com.pixiv.api.model.UserPreview
import com.pixiv.reader.core.ui.component.card.CreatorProfileCard
import com.pixiv.reader.core.ui.component.card.NovelCard
import com.pixiv.reader.core.ui.component.card.toCardData
import com.pixiv.reader.core.ui.component.card.toCreatorProfile
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.grid.IllustWaterfallGrid
import com.pixiv.reader.core.ui.component.grid.IllustWaterfallSkeleton
import com.pixiv.reader.core.ui.component.list.loadMoreFooter
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.discover.state.DiscoverViewModel

/** 插画搜索结果（普通模式，分页瀑布流）。 */
@Composable
internal fun IllustSearchResults(
    viewModel: DiscoverViewModel,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    val items by viewModel.illustPaged.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.illustPaged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.illustPaged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.illustPaged.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.illustPaged.error.collectAsStateWithLifecycle()

    when {
        isLoading && items.isEmpty() -> IllustWaterfallSkeleton()
        error != null && items.isEmpty() -> ErrorBox(
            message = error.orEmpty(),
            onRetry = viewModel::retry
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

/** 小说搜索结果（普通模式，分页列表）。 */
@Composable
internal fun NovelSearchResults(
    viewModel: DiscoverViewModel,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
) {
    val items by viewModel.novelPaged.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.novelPaged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.novelPaged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.novelPaged.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.novelPaged.error.collectAsStateWithLifecycle()
    // 动效方案在组合期取好：item lambda 不是组合上下文，无法在其中读取 MaterialTheme
    val motionScheme = MaterialTheme.motionScheme

    when {
        isLoading && items.isEmpty() -> NovelSearchSkeleton()
        error != null && items.isEmpty() -> ErrorBox(
            message = error.orEmpty(),
            onRetry = viewModel::retry
        )

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.xs, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.smPlus),
        ) {
            items(items, key = { it.id }) { novel ->
                NovelRow(
                    novel = novel,
                    onClick = { onOpenNovel(novel.id) },
                    onOpenAuthor = { novel.user?.id?.let(onOpenUser) },
                    onToggleFavorite = { nowFavorite ->
                        viewModel.toggleNovelFavorite(
                            novel.id,
                            nowFavorite
                        )
                    },
                    onTagClick = { tag ->
                        viewModel.onQueryChange(tag)
                        viewModel.search()
                    },
                    onSeriesClick = { novel.series?.id?.let(onOpenSeries) },
                    // 进场淡入 + 新搜索/翻页追加时位置平滑过渡
                    modifier = Modifier.animateItem(
                        fadeInSpec = motionScheme.fastEffectsSpec<Float>(),
                        placementSpec = motionScheme.fastSpatialSpec<IntOffset>(),
                    ),
                )
            }
            loadMoreFooter(hasMore = hasMore, isLoadingMore = isLoadingMore, onLoadMore = viewModel::loadMore)
        }
    }
}

/**
 * 小说搜索结果单行：转发到通用 [NovelCard]。
 *
 * @param novel 小说数据
 * @param onClick 卡片点击
 * @param onOpenAuthor 作者行点击
 * @param onToggleFavorite 收藏切换回调（参数为切换后是否已收藏）
 * @param onTagClick 标签点击（跳搜索）
 * @param onSeriesClick 系列标题点击
 * @param modifier 外部传入的 Modifier（调用方用于挂 `animateItem` 列表动效）
 * @return 无返回值
 */
@Composable
private fun NovelRow(
    novel: Novel,
    onClick: () -> Unit,
    onOpenAuthor: () -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    onTagClick: (String) -> Unit,
    onSeriesClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    NovelCard(
        novel = novel.toCardData(),
        onClick = onClick,
        onOpenAuthor = onOpenAuthor,
        onToggleFavorite = onToggleFavorite,
        onTagClick = onTagClick,
        onSeriesClick = onSeriesClick,
        modifier = modifier,
    )
}

/** 用户搜索结果（普通模式）。 */
@Composable
internal fun UserSearchResults(
    viewModel: DiscoverViewModel,
    onOpenUser: (Long) -> Unit,
) {
    val items by viewModel.userPaged.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.userPaged.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.userPaged.error.collectAsStateWithLifecycle()
    // 动效方案在组合期取好：item lambda 不是组合上下文，无法在其中读取 MaterialTheme
    val motionScheme = MaterialTheme.motionScheme

    when {
        isLoading && items.isEmpty() -> UserSearchSkeleton()
        error != null && items.isEmpty() -> ErrorBox(
            message = error.orEmpty(),
            onRetry = viewModel::retry
        )

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.smPlus),
        ) {
            items(items, key = { it.user?.id ?: 0L }) { preview ->
                UserRow(
                    preview = preview,
                    onClick = { preview.user?.id?.let(onOpenUser) },
                    onToggleFollow = { followed ->
                        preview.user?.id?.let { viewModel.toggleFollowUser(it, followed) }
                    },
                    // 进场淡入 + 新搜索时位置平滑过渡
                    modifier = Modifier.animateItem(
                        fadeInSpec = motionScheme.fastEffectsSpec<Float>(),
                        placementSpec = motionScheme.fastSpatialSpec<IntOffset>(),
                    ),
                )
            }
        }
    }
}

/**
 * 用户搜索结果单行：转发到通用 [CreatorProfileCard]。
 *
 * @param preview 用户预览数据
 * @param onClick 卡片点击（打开用户主页）
 * @param onToggleFollow 关注切换回调（参数为切换后是否已关注）
 * @param modifier 外部传入的 Modifier（调用方用于挂 `animateItem` 列表动效）
 * @return 无返回值
 */
@Composable
private fun UserRow(
    preview: UserPreview,
    onClick: () -> Unit,
    onToggleFollow: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    CreatorProfileCard(
        profile = preview.toCreatorProfile(),
        onToggleFollow = onToggleFollow,
        onClick = onClick,
        modifier = modifier,
    )
}
