package com.pixiv.reader.feature.user.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.Illust
import com.pixiv.api.model.Novel
import com.pixiv.api.model.NovelSeriesItem
import com.pixiv.reader.core.common.formatCountForNovel
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.ui.component.EmptyBox
import com.pixiv.reader.core.ui.component.ErrorBox
import com.pixiv.reader.core.ui.component.IllustWaterfallGrid
import com.pixiv.reader.core.ui.component.LoadingBox
import com.pixiv.reader.core.ui.component.NovelCard
import com.pixiv.reader.core.ui.component.NovelCardData
import com.pixiv.reader.core.ui.component.PixivImage
import com.pixiv.reader.core.ui.component.SeriesBookCover
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.feature.user.R

/** 插画 / 漫画分区内容：三态 + 瀑布流（漫画复用同一组件）。 */
@Composable
internal fun SectionIllust(
    paged: PagedState<Illust>,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val items by paged.items.collectAsStateWithLifecycle()
    val isLoading by paged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by paged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by paged.hasMore.collectAsStateWithLifecycle()
    val error by paged.error.collectAsStateWithLifecycle()

    when {
        isLoading && items.isEmpty() -> LoadingBox()
        error != null && items.isEmpty() -> ErrorBox(message = error.orEmpty(), onRetry = onRetry)
        items.isEmpty() -> EmptyBox(stringResource(R.string.user_empty_illust))
        else -> IllustWaterfallGrid(
            illusts = items,
            onItemClick = onOpenIllust,
            onLoadMore = onLoadMore,
            hasMore = hasMore,
            isLoadingMore = isLoadingMore,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
            onToggleFavorite = onToggleFavorite,
            onOpenUser = onOpenUser,
        )
    }
}

/** 小说分区内容：三态 + NovelCard 列表（触底加载）。 */
@Composable
internal fun SectionNovel(
    paged: PagedState<Novel>,
    onOpenNovel: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onTagClick: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val items by paged.items.collectAsStateWithLifecycle()
    val isLoading by paged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by paged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by paged.hasMore.collectAsStateWithLifecycle()
    val error by paged.error.collectAsStateWithLifecycle()

    when {
        isLoading && items.isEmpty() -> LoadingBox()
        error != null && items.isEmpty() -> ErrorBox(message = error.orEmpty(), onRetry = onRetry)
        items.isEmpty() -> EmptyBox(stringResource(R.string.user_empty_novel))
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id }) { novel ->
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
                    onClick = { onOpenNovel(novel.id) },
                    onOpenCover = { (novel.image_urls?.square_medium ?: novel.image_urls?.medium)?.let(onOpenCover) },
                    onOpenAuthor = { novel.user?.id?.let(onOpenUser) },
                    onToggleFavorite = { fav -> onToggleFavorite(novel.id, fav) },
                    onTagClick = onTagClick,
                    onSeriesClick = { novel.series?.id?.let(onOpenSeries) },
                )
            }
            if (hasMore) {
                item(key = "load_more") {
                    LaunchedEffect(Unit) { onLoadMore() }
                    Box(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isLoadingMore) {
                            CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

/** 系列分区内容：三态 + SeriesCard 列表（触底加载）。 */
@Composable
internal fun SectionSeries(
    paged: PagedState<NovelSeriesItem>,
    covers: Map<Long, String>,
    onOpenSeries: (Long) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val items by paged.items.collectAsStateWithLifecycle()
    val isLoading by paged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by paged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by paged.hasMore.collectAsStateWithLifecycle()
    val error by paged.error.collectAsStateWithLifecycle()

    when {
        isLoading && items.isEmpty() -> LoadingBox()
        error != null && items.isEmpty() -> ErrorBox(message = error.orEmpty(), onRetry = onRetry)
        items.isEmpty() -> EmptyBox(stringResource(R.string.user_empty_series))
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id }) { series ->
                SeriesCard(
                    series = series,
                    coverUrl = covers[series.id],
                    onClick = { onOpenSeries(series.id) },
                )
            }
            if (hasMore) {
                item(key = "load_more") {
                    LaunchedEffect(Unit) { onLoadMore() }
                    Box(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isLoadingMore) {
                            CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

/** 系列卡片：封面（真实图/图标兜底）+ 标题/简介 + N 篇/总字数 + 连载状态/已追更徽章。 */
@Composable
internal fun SeriesCard(
    series: NovelSeriesItem,
    coverUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 封面：有 URL 显示真实封面（3:4，自动 Referer），无则 MD3 图标容器兜底
        if (!coverUrl.isNullOrBlank()) {
            PixivImage(
                url = coverUrl,
                contentDescription = series.title,
                modifier = Modifier
                    .width(72.dp)
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(12.dp)),
            )
        } else {
            SeriesBookCover(
                modifier = Modifier.size(width = 72.dp, height = 96.dp),
                iconSize = 36.dp,
            )
        }
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Text(
                text = series.title.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val caption = series.caption
            if (!caption.isNullOrBlank()) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 篇数 + 总字数（text 弱化）
                Text(
                    text = stringResource(R.string.user_series_parts, series.content_count),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (series.total_character_count > 0) {
                    Text(
                        text = stringResource(
                            R.string.user_series_chars,
                            formatCountForNovel(series.total_character_count),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 连载状态徽章（药丸）
                SeriesStatusBadge(
                    text = stringResource(
                        if (series.is_concluded) R.string.user_series_concluded else R.string.user_series_ongoing,
                    ),
                    container = if (series.is_concluded) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    content = if (series.is_concluded) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
                // 已追更标记（次要）
                if (series.watchlist_added) {
                    SeriesStatusBadge(
                        text = stringResource(R.string.user_series_watchlisted),
                        container = MaterialTheme.colorScheme.surfaceContainerHigh,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** MD3 药丸徽章（AssistChip 视觉，扁平无交互）。 */
@Composable
private fun SeriesStatusBadge(
    text: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = Modifier
            .clip(AppShapes.pill)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}
