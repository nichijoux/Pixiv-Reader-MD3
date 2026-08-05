package com.pixiv.reader.feature.discover

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.Illust
import com.pixiv.api.model.Novel
import com.pixiv.api.model.UserPreview
import com.pixiv.reader.core.common.formatCount
import com.pixiv.reader.core.common.formatCountForNovel
import com.pixiv.reader.core.ui.component.CreatorProfile
import com.pixiv.reader.core.ui.component.CreatorProfileCard
import com.pixiv.reader.core.ui.component.ErrorBox
import com.pixiv.reader.core.ui.component.IllustWaterfallGrid
import com.pixiv.reader.core.ui.component.NovelCard
import com.pixiv.reader.core.ui.component.NovelCardData
import com.pixiv.reader.core.ui.component.PixivImage

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
        isLoading && items.isEmpty() -> IllustSearchSkeleton()
        error != null && items.isEmpty() -> ErrorBox(message = error.orEmpty(), onRetry = viewModel::retry)
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

@Composable
internal fun NovelSearchResults(
    viewModel: DiscoverViewModel,
    onOpenNovel: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
) {
    val items by viewModel.novelPaged.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.novelPaged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.novelPaged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.novelPaged.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.novelPaged.error.collectAsStateWithLifecycle()

    when {
        isLoading && items.isEmpty() -> NovelSearchSkeleton()
        error != null && items.isEmpty() -> ErrorBox(message = error.orEmpty(), onRetry = viewModel::retry)
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id }) { novel ->
                NovelRow(
                    novel = novel,
                    onClick = { onOpenNovel(novel.id) },
                    onOpenCover = { (novel.image_urls?.square_medium ?: novel.image_urls?.medium)?.let(onOpenCover) },
                    onOpenAuthor = { novel.user?.id?.let(onOpenUser) },
                    onToggleFavorite = { nowFavorite -> viewModel.toggleNovelFavorite(novel.id, nowFavorite) },
                    onTagClick = { tag ->
                        viewModel.onQueryChange(tag)
                        viewModel.search()
                    },
                    onSeriesClick = { novel.series?.id?.let(onOpenSeries) },
                )
            }
            if (hasMore) {
                item(key = "load_more") {
                    LaunchedLoadMore(isLoadingMore, viewModel::loadMore)
                }
            }
        }
    }
}

@Composable
private fun NovelRow(
    novel: Novel,
    onClick: () -> Unit,
    onOpenCover: () -> Unit,
    onOpenAuthor: () -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    onTagClick: (String) -> Unit,
    onSeriesClick: (Long) -> Unit = {},
) {
    NovelCard(
        novel = NovelCardData(
            id = novel.id,
            title = novel.title.orEmpty(),
            coverUrl = novel.image_urls?.square_medium ?: novel.image_urls?.medium,
            authorId = novel.user?.id ?: 0L,
            authorName = novel.user?.name.orEmpty(),
            authorAvatarUrl = novel.user?.profile_image_urls?.best(),
            publishDate = novel.create_date,
            seriesTitle = novel.series?.title,
            seriesId = novel.series?.id,
            favoriteCount = novel.total_bookmarks ?: 0,
            wordCount = novel.text_length ?: 0,
            tags = novel.tags.orEmpty()
                .take(6)
                .map { it.translated_name ?: it.name ?: "" }
                .filter { it.isNotBlank() },
            isFavorite = novel.is_bookmarked == true,
        ),
        onClick = onClick,
        onOpenCover = onOpenCover,
        onOpenAuthor = onOpenAuthor,
        onToggleFavorite = onToggleFavorite,
        onTagClick = onTagClick,
        onSeriesClick = onSeriesClick,
    )
}

@Composable
internal fun UserSearchResults(
    viewModel: DiscoverViewModel,
    onOpenUser: (Long) -> Unit,
) {
    val items by viewModel.userPaged.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.userPaged.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.userPaged.error.collectAsStateWithLifecycle()

    when {
        isLoading && items.isEmpty() -> UserSearchSkeleton()
        error != null && items.isEmpty() -> ErrorBox(message = error.orEmpty(), onRetry = viewModel::retry)
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.user?.id ?: 0L }) { preview ->
                UserRow(
                    preview = preview,
                    onClick = { preview.user?.id?.let(onOpenUser) },
                    onToggleFollow = { followed ->
                        preview.user?.id?.let { viewModel.toggleFollowUser(it, followed) }
                    },
                )
            }
        }
    }
}

@Composable
private fun UserRow(
    preview: UserPreview,
    onClick: () -> Unit,
    onToggleFollow: (Boolean) -> Unit,
) {
    val user = preview.user
    CreatorProfileCard(
        profile = CreatorProfile(
            id = user?.id ?: 0L,
            name = user?.name.orEmpty(),
            avatarUrl = user?.profile_image_urls?.best(),
            covers = preview.illusts.take(3).mapNotNull {
                it.image_urls?.square_medium ?: it.image_urls?.medium
            },
            isFollowed = user?.is_followed == true,
        ),
        onToggleFollow = onToggleFollow,
        onClick = onClick,
    )
}

@Composable
internal fun LaunchedLoadMore(isLoadingMore: Boolean, onLoadMore: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { onLoadMore() }
    if (isLoadingMore) {
        Box(modifier = Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(strokeWidth = 2.dp)
        }
    }
}

/** 骨架呼吸脉冲色：`surfaceVariant` + alpha 0.35↔0.75，替代全屏转圈的加载占位。 */
@Composable
private fun skeletonPulseColor(): Color {
    val transition = rememberInfiniteTransition(label = "searchSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "searchSkeletonAlpha",
    )
    return MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
}

/** 圆角占位块（[Modifier.clip] 由调用方指定形状）。 */
@Composable
private fun SkeletonBlock(modifier: Modifier, color: Color) {
    Box(modifier = modifier.background(color))
}

/**
 * 插画搜索结果骨架：仿 [IllustWaterfallGrid]（自适应 2 列瀑布流）渲染 8 张占位卡
 * ——圆角 14dp 卡片 + 封面块（交替高度模拟瀑布流）+ 标题 2 行 + 作者行（20dp 圆头像 + 名称条）。
 */
@Composable
private fun IllustSearchSkeleton() {
    val color = skeletonPulseColor()
    val coverHeights = listOf(150.dp, 120.dp, 180.dp, 140.dp, 130.dp, 160.dp)
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(140.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
    ) {
        items(count = 8) { index ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(coverHeights[index % coverHeights.size])
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                    color = color,
                )
                Column(modifier = Modifier.padding(10.dp)) {
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = color,
                    )
                    SkeletonBlock(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth(0.5f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = color,
                    )
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SkeletonBlock(
                            modifier = Modifier.size(20.dp).clip(CircleShape),
                            color = color,
                        )
                        SkeletonBlock(
                            modifier = Modifier.padding(start = 6.dp).width(80.dp).height(10.dp).clip(RoundedCornerShape(6.dp)),
                            color = color,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 小说搜索结果骨架：仿 [NovelCard]（横排卡片列表）渲染 6 张占位卡
 * ——圆角 16dp Card + 左侧 104dp 3/4 封面块 + 右侧标题/作者条。
 */
@Composable
private fun NovelSearchSkeleton() {
    val color = skeletonPulseColor()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(count = 6) {
            androidx.compose.material3.Card(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Row(modifier = Modifier.padding(14.dp)) {
                    SkeletonBlock(
                        modifier = Modifier
                            .width(104.dp)
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(12.dp)),
                        color = color,
                    )
                    Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                        SkeletonBlock(
                            modifier = Modifier.fillMaxWidth(0.75f).height(16.dp).clip(RoundedCornerShape(6.dp)),
                            color = color,
                        )
                        SkeletonBlock(
                            modifier = Modifier.padding(top = 10.dp).fillMaxWidth(0.4f).height(12.dp).clip(RoundedCornerShape(6.dp)),
                            color = color,
                        )
                        Row(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SkeletonBlock(
                                modifier = Modifier.size(28.dp).clip(CircleShape),
                                color = color,
                            )
                            SkeletonBlock(
                                modifier = Modifier.padding(start = 8.dp).width(90.dp).height(10.dp).clip(RoundedCornerShape(6.dp)),
                                color = color,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 用户搜索结果骨架：仿 [CreatorProfileCard] 渲染 5 张占位卡
 * ——圆角 16dp 卡片 + 顶部 120dp 三封面横排 + 底部 64dp 圆头像（重叠封面）+ 名称条 + 关注按钮块。
 */
@Composable
private fun UserSearchSkeleton() {
    val color = skeletonPulseColor()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(count = 5) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                    repeat(3) {
                        SkeletonBlock(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            color = color,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.offset(y = (-24).dp)) {
                        SkeletonBlock(
                            modifier = Modifier.size(64.dp).clip(CircleShape),
                            color = color,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    SkeletonBlock(
                        modifier = Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(6.dp)),
                        color = color,
                    )
                    Spacer(Modifier.width(10.dp))
                    SkeletonBlock(
                        modifier = Modifier.width(72.dp).height(40.dp).clip(RoundedCornerShape(20.dp)),
                        color = color,
                    )
                }
            }
        }
    }
}
