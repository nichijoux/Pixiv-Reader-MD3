package com.pixiv.reader.feature.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.grid.IllustWaterfallGrid
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes

/**
 * 漫画系列详情（路由 `illust_series/{seriesId}`）：
 * 头部（首话封面 + 标题/作者/话数 + 追更按钮 + 简介）+ 系列内作品瀑布流（触底分页）。
 * 追更按钮走 v1/watchlist/manga add/remove；作品点击跳插画/漫画详情。
 *
 * @param onBack 返回
 * @param onOpenIllust 点击系列内作品打开插画/漫画详情
 * @param onOpenUser 点击作者打开用户主页
 * @param onOpenViewer 点击图片打开全屏查看器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaSeriesRoute(
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenViewer: (Long, Int) -> Unit = { _, _ -> },
    viewModel: MangaSeriesViewModel = hiltViewModel(),
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val isWatchlisted by viewModel.isWatchlisted.collectAsStateWithLifecycle()
    val isWatchlisting by viewModel.isWatchlisting.collectAsStateWithLifecycle()
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
            TopAppBar(
                title = { Text(stringResource(R.string.manga_series_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.manga_cd_back),
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
        when {
            isLoading && detail == null -> LoadingBox(modifier = Modifier.padding(padding))
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
                modifier = Modifier.padding(padding),
                onOpenUser = onOpenUser,
                // 系列头部：封面 + 标题/作者/话数/追更 + 简介（随网格滚动）
                header = {
                    SeriesHeader(
                        firstIllust = items.firstOrNull(),
                        title = detail?.title.orEmpty(),
                        caption = detail?.caption,
                        authorName = detail?.user?.name.orEmpty(),
                        authorAvatarUrl = detail?.user?.profile_image_urls?.best(),
                        chapters = detail?.series_work_count ?: 0,
                        isWatchlisted = isWatchlisted,
                        isWatchlisting = isWatchlisting,
                        onToggleWatchlist = viewModel::toggleWatchlist,
                        onOpenUser = onOpenUser,
                    )
                },
            )
        }
    }
}

/**
 * 系列头部：封面（首话封面，无则书本图标兜底）+ 标题/作者/话数/追更按钮 + 简介。
 *
 * @param firstIllust 系列首话作品（封面来源）
 * @param title 系列标题
 * @param caption 系列简介（空则不渲染）
 * @param authorName 作者名
 * @param authorAvatarUrl 作者头像
 * @param chapters 总话数
 * @param isWatchlisted 是否已追更
 * @param isWatchlisting 追更请求进行中
 * @param onToggleWatchlist 追更/取消追更回调
 * @param onOpenUser 作者行点击（打开用户主页）
 * @return 无返回值
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
            // 封面（96x128，与 SeriesCard 同规格）：首话封面 / 图标兜底
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
            // 右侧信息列：标题 + 作者行 + 话数 + 追更按钮
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
                // 作者行（头像 + 名称，可点进主页）
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
                Spacer(Modifier.width(Spacing.xs))
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
        // 简介（可空）
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
