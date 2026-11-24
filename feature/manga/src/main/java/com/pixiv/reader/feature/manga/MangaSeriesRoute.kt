package com.pixiv.reader.feature.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.ui.component.card.SeriesBookCover
import com.pixiv.reader.core.ui.component.card.UserAvatar
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.SkeletonBlock
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.feedback.skeletonPulseColor
import com.pixiv.reader.core.ui.component.grid.IllustWaterfallGrid
import com.pixiv.reader.core.ui.component.grid.IllustWaterfallSkeleton
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.component.layout.BackTopAppBar
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes

/**
 * 漫画系列详情（路由 `illust_series/{seriesId}`）：头部 + 系列内作品瀑布流（触底分页）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaSeriesRoute(
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    viewModel: MangaSeriesViewModel = hiltViewModel(),
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val watchlistState by viewModel.watchlistState.collectAsStateWithLifecycle()
    val items by viewModel.paged.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.paged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.paged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.paged.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.paged.error.collectAsStateWithLifecycle()

    val notificationHostState = rememberNotificationHostState()
    UiMessageEffect(viewModel.message, notificationHostState)

    Scaffold(
        snackbarHost = { NotificationHost(notificationHostState) },
        topBar = {
            BackTopAppBar(title = stringResource(R.string.manga_series_title), onBack = onBack)
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        when {
            isLoading && detail == null -> IllustWaterfallSkeleton(
                modifier = Modifier.padding(padding),
                // 头部骨架要与真实 SeriesHeader 结构对齐，否则加载态会跳一下
                header = { MangaSeriesHeaderSkeleton() },
            )
            error != null && detail == null -> ErrorBox(
                message = error.orEmpty(),
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding),
            )

            else -> IllustWaterfallGrid(
                illusts = items,
                onItemClick = onOpenIllust,
                onLoadMore = viewModel::loadMore,
                hasMore = hasMore,
                isLoadingMore = isLoadingMore,
                error = error,
                modifier = Modifier.padding(padding),
                onOpenUser = onOpenUser,
                header = {
                    SeriesHeader(
                        firstIllust = items.firstOrNull(),
                        title = detail?.title.orEmpty(),
                        caption = detail?.caption,
                        authorName = detail?.user?.name.orEmpty(),
                        authorAvatarUrl = detail?.user?.profile_image_urls?.best(),
                        chapters = detail?.series_work_count ?: 0,
                        isWatchlisted = watchlistState.isOn,
                        isWatchlisting = watchlistState.inFlight,
                        onToggleWatchlist = viewModel::toggleWatchlist,
                        onOpenUser = onOpenUser,
                    )
                },
            )
        }
    }
}

/**
 * 系列头部：首话封面（无则书本图标）+ 标题/作者/话数/追更 + 简介。
 */
@Composable
private fun SeriesHeader(
    firstIllust: Illust?,
    title: String,
    caption: String?,
    authorName: String,
    authorAvatarUrl: String?,
    chapters: Int,
    isWatchlisted: Boolean,
    isWatchlisting: Boolean,
    onToggleWatchlist: () -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 128.dp)
                    .clip(AppShapes.card),
            ) {
                val coverUrl = firstIllust?.image_urls?.medium
                    ?: firstIllust?.image_urls?.square_medium
                if (!coverUrl.isNullOrBlank()) {
                    PixivImage(
                        url = coverUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    SeriesBookCover(modifier = Modifier.fillMaxSize(), iconSize = 48.dp)
                }
            }
            Column(
                modifier = Modifier
                    .padding(start = Spacing.md)
                    .weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier
                        .padding(top = Spacing.sm)
                        .clip(AppShapes.small)
                        .clickable { firstIllust?.user?.id?.let(onOpenUser) }
                        .padding(end = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
                ) {
                    UserAvatar(
                        name = authorName,
                        avatarUrl = authorAvatarUrl,
                        modifier = Modifier.size(Sizes.s20),
                    )
                    Text(
                        text = authorName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stringResource(R.string.manga_series_chapters, chapters),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                FilledTonalButton(
                    onClick = onToggleWatchlist,
                    enabled = !isWatchlisting,
                    modifier = Modifier.padding(top = Spacing.sm),
                ) {
                    Text(
                        text = stringResource(
                            if (isWatchlisted) R.string.manga_series_watchlisted
                            else R.string.manga_series_watchlist
                        ),
                    )
                }
            }
        }
        if (!caption.isNullOrBlank()) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }
    }
}

/**
 * 系列头骨架：与 [SeriesHeader] 同规格（96×128 封面块 + 右侧信息列 + 简介行），
 * 供 [IllustWaterfallSkeleton] 整行 header 槽位使用，避免加载态结构错位。
 */
@Composable
private fun MangaSeriesHeaderSkeleton() {
    val color = skeletonPulseColor(label = "mangaSeriesHeaderSkeleton")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            SkeletonBlock(
                modifier = Modifier
                    .size(width = 96.dp, height = 128.dp)
                    .clip(AppShapes.card),
                color = color,
            )
            Column(
                modifier = Modifier
                    .padding(start = Spacing.md)
                    .weight(1f),
            ) {
                SkeletonBlock(
                    modifier = Modifier.fillMaxWidth(0.85f).height(18.dp).clip(AppShapes.tiny),
                    color = color,
                )
                SkeletonBlock(
                    modifier = Modifier
                        .padding(top = Spacing.sm)
                        .fillMaxWidth(0.5f)
                        .height(18.dp)
                        .clip(AppShapes.tiny),
                    color = color,
                )
                Row(
                    modifier = Modifier.padding(top = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonBlock(
                        modifier = Modifier.size(Sizes.s20).clip(AppShapes.circle),
                        color = color,
                    )
                    SkeletonBlock(
                        modifier = Modifier
                            .padding(start = Spacing.xsPlus)
                            .width(88.dp)
                            .height(10.dp)
                            .clip(AppShapes.tiny),
                        color = color,
                    )
                }
                SkeletonBlock(
                    modifier = Modifier
                        .padding(top = Spacing.xs)
                        .width(64.dp)
                        .height(10.dp)
                        .clip(AppShapes.tiny),
                    color = color,
                )
                SkeletonBlock(
                    modifier = Modifier
                        .padding(top = Spacing.sm)
                        .width(104.dp)
                        .height(36.dp)
                        .clip(AppShapes.pill),
                    color = color,
                )
            }
        }
        SkeletonBlock(
            modifier = Modifier
                .padding(top = Spacing.md)
                .fillMaxWidth()
                .height(10.dp)
                .clip(AppShapes.tiny),
            color = color,
        )
    }
}
