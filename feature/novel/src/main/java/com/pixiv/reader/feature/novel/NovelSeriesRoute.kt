package com.pixiv.reader.feature.novel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.common.formatCountForNovel
import com.pixiv.reader.core.ui.component.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.EmptyBox
import com.pixiv.reader.core.ui.component.ErrorBox
import com.pixiv.reader.core.ui.component.LoadingBox
import com.pixiv.reader.core.ui.component.NovelCard
import com.pixiv.reader.core.ui.component.NovelCardData
import com.pixiv.reader.core.ui.component.PixivImage
import com.pixiv.reader.core.ui.component.SeriesBookCover
import com.pixiv.reader.core.ui.component.UserAvatar
import com.pixiv.reader.core.ui.theme.AppShapes

/**
 * 小说系列详情页：系列信息头（标题/简介/篇数/连载态/作者）+ 分册 NovelCard 列表。
 *
 * @param onBack 返回
 * @param onOpenNovel 打开分册详情
 * @param onOpenCover 打开封面全屏大图
 * @param onOpenUser 打开作者主页
 * @param onSearchTag 标签搜索
 * @param onOpenSeries 打开系列详情（分册点系列标题回当前系列）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelSeriesRoute(
    onBack: () -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
    onOpenUser: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenSeries: (Long) -> Unit,
    viewModel: NovelSeriesViewModel = hiltViewModel(),
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val firstNovelCover by viewModel.firstNovelCover.collectAsStateWithLifecycle()
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
                        text = detail?.title ?: stringResource(R.string.novel_series_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.novel_cd_back),
                        )
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
                isLoading && items.isEmpty() && detail == null -> LoadingBox()
                error != null && items.isEmpty() && detail == null ->
                    ErrorBox(message = error.orEmpty(), onRetry = viewModel::load)
                items.isEmpty() && detail == null -> EmptyBox(stringResource(R.string.novel_not_found))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (detail != null) {
                        item(key = "header") {
                            SeriesHeader(
                                detail = detail!!,
                                onOpenAuthor = onOpenUser,
                                coverUrl = firstNovelCover,
                                onOpenCover = { firstNovelCover?.let(onOpenCover) },
                            )
                        }
                    }
                    item(key = "volumes_section") {
                        Text(
                            text = stringResource(R.string.novel_series_volumes_section, items.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
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
                            onToggleFavorite = { fav -> viewModel.toggleNovelFavorite(novel.id, fav) },
                            onTagClick = onSearchTag,
                            // 系列页内分册：点系列标题回到当前系列（同路由，popUpTo 语义由导航处理）
                            onSeriesClick = { novel.series?.id?.let(onOpenSeries) },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    if (hasMore) {
                        item(key = "load_more") {
                            LaunchedEffect(Unit) { viewModel.loadMore() }
                            Box(
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isLoadingMore) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 系列信息头：大封面（真实图/图标兜底）+ 标题/简介 + 篇数/连载态徽章 + 可点击作者行 + 总字数。 */
@Composable
private fun SeriesHeader(
    detail: com.pixiv.api.model.NovelSeriesDetail,
    onOpenAuthor: (Long) -> Unit,
    coverUrl: String?,
    onOpenCover: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // 大封面：3:4（110×148dp）；有真实封面图走 PixivImage（自动 Referer），无则 MD3 图标容器兜底
            if (!coverUrl.isNullOrBlank()) {
                PixivImage(
                    url = coverUrl,
                    contentDescription = detail.title,
                    modifier = Modifier
                        .width(110.dp)
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenCover),
                )
            } else {
                SeriesBookCover(
                    modifier = Modifier.size(width = 110.dp, height = 148.dp),
                    iconSize = 44.dp,
                )
            }
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    text = detail.title.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SeriesMetaChip(
                        text = stringResource(R.string.novel_series_parts, detail.content_count),
                        container = scheme.secondaryContainer,
                        content = scheme.onSecondaryContainer,
                    )
                    SeriesMetaChip(
                        text = stringResource(
                            if (detail.is_concluded == true) R.string.novel_series_concluded else R.string.novel_series_ongoing,
                        ),
                        container = if (detail.is_concluded == true) scheme.errorContainer else scheme.secondaryContainer,
                        content = if (detail.is_concluded == true) scheme.onErrorContainer else scheme.onSecondaryContainer,
                    )
                }
                if (detail.total_character_count > 0) {
                    Text(
                        text = stringResource(
                            R.string.novel_series_total_chars,
                            formatCountForNovel(detail.total_character_count),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
        val caption = detail.caption?.takeIf { it.isNotBlank() }
            ?: detail.display_text?.takeIf { it.isNotBlank() }
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
        // 作者行：头像 + 名称 + 查看作者（整行可点击）
        detail.user?.let { author ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenAuthor(author.id) }
                    .padding(top = 14.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(
                    name = author.name,
                    avatarUrl = author.profile_image_urls?.best(),
                    modifier = Modifier.size(44.dp),
                )
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        text = stringResource(R.string.novel_series_author, author.name.orEmpty()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.novel_series_view_author),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.primary,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** MD3 药丸徽章（AssistChip 视觉，扁平无交互）。 */
@Composable
private fun SeriesMetaChip(
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
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}
