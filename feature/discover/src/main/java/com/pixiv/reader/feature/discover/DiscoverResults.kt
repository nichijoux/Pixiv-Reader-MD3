package com.pixiv.reader.feature.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pixivapi.model.Illust
import com.example.pixivapi.model.Novel
import com.example.pixivapi.model.UserPreview
import com.pixiv.reader.core.common.formatCount
import com.pixiv.reader.core.common.formatCountForNovel
import com.pixiv.reader.core.ui.component.CreatorProfile
import com.pixiv.reader.core.ui.component.CreatorProfileCard
import com.pixiv.reader.core.ui.component.ErrorBox
import com.pixiv.reader.core.ui.component.IllustWaterfallGrid
import com.pixiv.reader.core.ui.component.LoadingBox
import com.pixiv.reader.core.ui.component.NovelCard
import com.pixiv.reader.core.ui.component.NovelCardData
import com.pixiv.reader.core.ui.component.PixivImage

@Composable
internal fun IllustSearchResults(viewModel: DiscoverViewModel, onOpenIllust: (Long) -> Unit) {
    val items by viewModel.illustPaged.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.illustPaged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.illustPaged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.illustPaged.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.illustPaged.error.collectAsStateWithLifecycle()

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
internal fun NovelSearchResults(
    viewModel: DiscoverViewModel,
    onOpenNovel: (Long) -> Unit,
    onOpenReader: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    val items by viewModel.novelPaged.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.novelPaged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.novelPaged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.novelPaged.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.novelPaged.error.collectAsStateWithLifecycle()

    when {
        isLoading && items.isEmpty() -> LoadingBox()
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
                    onOpenReader = { onOpenReader(novel.id) },
                    onOpenAuthor = { novel.user?.id?.let(onOpenUser) },
                    onToggleFavorite = { nowFavorite -> viewModel.toggleNovelFavorite(novel.id, nowFavorite) },
                    onTagClick = { tag ->
                        viewModel.onQueryChange(tag)
                        viewModel.search()
                    },
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
    onOpenReader: () -> Unit,
    onOpenAuthor: () -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    onTagClick: (String) -> Unit,
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
            favoriteCount = novel.total_bookmarks ?: 0,
            wordCount = novel.text_length ?: 0,
            tags = novel.tags.orEmpty()
                .take(6)
                .map { it.translated_name ?: it.name ?: "" }
                .filter { it.isNotBlank() },
            isFavorite = novel.is_bookmarked == true,
        ),
        onClick = onClick,
        onOpenReader = onOpenReader,
        onOpenAuthor = onOpenAuthor,
        onToggleFavorite = onToggleFavorite,
        onTagClick = onTagClick,
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
        isLoading && items.isEmpty() -> LoadingBox()
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
