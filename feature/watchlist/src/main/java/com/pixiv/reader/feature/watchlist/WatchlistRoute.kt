package com.pixiv.reader.feature.watchlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.WatchlistSeries
import com.pixiv.reader.core.ui.component.card.UserAvatar
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.list.LoadMoreItem
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Sizes

/**
 * 追更：小说 / 漫画系列追更列表（SegmentedButton 分段切换，各类型独立分页缓存）。
 * 小说行点击打开最新分册详情；漫画行点击打开最新一话（插画详情）；
 * 行内按钮取消追更（按当前类型分流端点）。
 *
 * @param onBack 返回
 * @param initialType 初始类型（"novel" / "manga"，路由参数）
 * @param onOpenNovel 打开小说详情（小说追更行点击）
 * @param onOpenIllust 打开插画/漫画详情（漫画追更行点击）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistRoute(
    onBack: () -> Unit,
    initialType: String = WatchlistViewModel.TYPE_NOVEL,
    onOpenNovel: (Long) -> Unit,
    onOpenIllust: (Long) -> Unit = {},
    viewModel: WatchlistViewModel = hiltViewModel(),
) {
    val type by viewModel.type.collectAsStateWithLifecycle()
    val paged = viewModel.stateFor(type)
    val items by paged.items.collectAsStateWithLifecycle()
    val isLoading by paged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by paged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by paged.hasMore.collectAsStateWithLifecycle()
    val error by paged.error.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.watchlist_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
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
            Column(modifier = Modifier.fillMaxSize()) {
                // 类型分段：小说 / 漫画（各类型独立分页缓存，切换不重复请求）
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                ) {
                    SegmentedButton(
                        selected = type == WatchlistViewModel.TYPE_NOVEL,
                        onClick = { viewModel.selectType(WatchlistViewModel.TYPE_NOVEL) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.watchlist_type_novel)) },
                    )
                    SegmentedButton(
                        selected = type == WatchlistViewModel.TYPE_MANGA,
                        onClick = { viewModel.selectType(WatchlistViewModel.TYPE_MANGA) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.watchlist_type_manga)) },
                    )
                }
                when {
                    isLoading && items.isEmpty() -> LoadingBox()
                    error != null && items.isEmpty() -> ErrorBox(
                        message = error.orEmpty(),
                        onRetry = { viewModel.retry(type) }
                    )

                    items.isEmpty() -> EmptyBox(stringResource(R.string.watchlist_empty))
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    ) {
                        items(items, key = { "${type}_${it.id}" }) { series ->
                            WatchlistRow(
                                series = series,
                                onClick = {
                                    // 小说 → 最新分册详情；漫画 → 最新一话插画详情
                                    series.latest_content_id?.let { id ->
                                        if (type == WatchlistViewModel.TYPE_MANGA) onOpenIllust(id) else onOpenNovel(id)
                                    }
                                },
                                onRemove = { viewModel.removeWatchlist(series) },
                            )
                        }
                        if (hasMore) {
                            item(key = "load_more") {
                                LoadMoreItem(
                                    isLoadingMore = isLoadingMore,
                                    onLoadMore = { viewModel.loadMore(type) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 追更列表行：作者头像 + 标题/作者/章节数 + 取消追更按钮 +「查看」入口。
 *
 * @param series 追更系列
 * @param onClick 行点击（打开最新内容）
 * @param onRemove 行内取消追更
 */
@Composable
private fun WatchlistRow(
    series: WatchlistSeries,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.card)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.smPlus),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserAvatar(
            name = series.user?.name,
            avatarUrl = series.user?.profile_image_urls?.best(),
            modifier = Modifier.size(Sizes.s44),
        )
        Column(
            modifier = Modifier
                .padding(start = Spacing.md)
                .weight(1f),
        ) {
            Text(
                text = if (series.isMasked) stringResource(R.string.watchlist_masked_series) else series.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (!series.user?.name.isNullOrBlank()) {
                    Text(
                        text = series.user?.name.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(
                        R.string.watchlist_chapters,
                        series.published_content_count
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // 行内取消追更（铃铛关闭图标，不占文案位）
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.NotificationsOff,
                contentDescription = stringResource(R.string.watchlist_unwatch),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.s20),
            )
        }
        Text(
            text = stringResource(R.string.watchlist_view),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
