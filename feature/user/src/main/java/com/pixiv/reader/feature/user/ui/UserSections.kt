package com.pixiv.reader.feature.user.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.Illust
import com.pixiv.api.model.Novel
import com.pixiv.api.model.NovelSeriesItem
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.SeriesDetailInfo
import com.pixiv.reader.core.ui.component.EmptyBox
import com.pixiv.reader.core.ui.component.ErrorBox
import com.pixiv.reader.core.ui.component.IllustWaterfallGrid
import com.pixiv.reader.core.ui.component.LoadingBox
import com.pixiv.reader.core.ui.component.NovelCard
import com.pixiv.reader.core.ui.component.NovelCardData
import com.pixiv.reader.core.ui.component.SeriesCard
import com.pixiv.reader.core.ui.component.SeriesCardData
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
    infos: Map<Long, SeriesDetailInfo>,
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
                val info = infos[series.id]
                SeriesCard(
                    data = SeriesCardData(
                        title = series.title.orEmpty(),
                        caption = series.caption,
                        coverUrl = info?.coverUrl,
                        partsCount = series.content_count,
                        totalChars = series.total_character_count,
                        isConcluded = series.is_concluded,
                        // 与追更页一致：作者行显示最近更新时间（最新册发布日期），不显示已追更徽章
                        updatedAt = info?.updatedAt?.take(10),
                        authorName = series.user?.name,
                        authorAvatarUrl = series.user?.profile_image_urls?.best(),
                    ),
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

/** 系列卡片已上移至 core:ui（SeriesCard + SeriesCardData），用户页 / 追更列表共用。 */

