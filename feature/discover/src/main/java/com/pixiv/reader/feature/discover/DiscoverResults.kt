package com.pixiv.reader.feature.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.pixiv.reader.core.ui.component.ErrorBox
import com.pixiv.reader.core.ui.component.IllustWaterfallGrid
import com.pixiv.reader.core.ui.component.LoadingBox
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
        )
    }
}

@Composable
internal fun NovelSearchResults(viewModel: DiscoverViewModel) {
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
        ) {
            items(items, key = { it.id }) { novel ->
                NovelRow(novel)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
internal fun UserSearchResults(viewModel: DiscoverViewModel) {
    val items by viewModel.userPaged.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.userPaged.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.userPaged.error.collectAsStateWithLifecycle()

    when {
        isLoading && items.isEmpty() -> LoadingBox()
        error != null && items.isEmpty() -> ErrorBox(message = error.orEmpty(), onRetry = viewModel::retry)
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            items(items, key = { it.user?.id ?: 0L }) { preview ->
                UserRow(preview)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun NovelRow(novel: Novel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PixivImage(
            url = novel.image_urls?.square_medium ?: novel.image_urls?.medium,
            contentDescription = novel.title,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = novel.title.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    text = novel.user?.name.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = formatCount((novel.total_bookmarks ?: 0).toLong()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UserRow(preview: UserPreview) {
    val user = preview.user
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PixivImage(
            url = user?.profile_image_urls?.px_170x170 ?: user?.profile_image_urls?.px_50x50,
            contentDescription = user?.name,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user?.name.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                preview.illusts.take(3).forEach { illust ->
                    PixivImage(
                        url = illust.image_urls?.square_medium,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)),
                    )
                }
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
