package com.pixiv.reader.feature.discover.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.TrendingTag
import com.pixiv.reader.core.database.entity.SearchHistoryEntity
import com.pixiv.reader.core.ui.component.input.ConfirmDialog
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.feature.discover.R
import com.pixiv.reader.feature.discover.state.DiscoverViewModel

/** 初始态：发现入口区 + 搜索历史 + 热门标签（历史可清空/单删）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IdlePanel(
    hotTags: List<TrendingTag>,
    history: List<SearchHistoryEntity>,
    viewModel: DiscoverViewModel,
    onOpenPixivision: () -> Unit = {},
    onOpenUserRanking: () -> Unit = {},
    onOpenAiRanking: () -> Unit = {},
    onOpenEraRanking: () -> Unit = {},
    onOpenWallpaperRanking: () -> Unit = {},
) {
    var confirmClear by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SearchHistoryEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.lg),
        ) {
            // ── 发现入口区：pixivision / 画师榜 / AI榜 / 年代榜 / 壁纸榜 ──
            item(key = "discover_entries") {
                DiscoverEntries(
                    onOpenPixivision = onOpenPixivision,
                    onOpenUserRanking = onOpenUserRanking,
                    onOpenAiRanking = onOpenAiRanking,
                    onOpenEraRanking = onOpenEraRanking,
                    onOpenWallpaperRanking = onOpenWallpaperRanking,
                )
            }
            if (history.isNotEmpty()) {
                item(key = "history_title") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(Sizes.s18)
                        )
                        Text(
                            text = stringResource(R.string.search_history_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .padding(start = Spacing.xsPlus)
                                .weight(1f),
                        )
                        TextButton(onClick = {
                            confirmClear = true
                        }) {
                            Text(
                                stringResource(R.string.search_history_clear),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                // 历史胶囊：点击搜索、长按删除单条
                item(key = "history_chips") {
                    FlowRow(
                        modifier = Modifier.padding(bottom = Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        history.forEach { item ->
                            HistoryChip(
                                text = item.keyword,
                                onClick = { viewModel.onQueryChange(item.keyword); viewModel.search() },
                                onLongClick = { pendingDelete = item },
                            )
                        }
                    }
                }
                item {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = Spacing.sm)
                    )
                }
            }
            item(key = "hot_title") {
                Text(
                    text = stringResource(R.string.search_hot_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs),
                )
            }
            items(hotTags.take(6).withIndex().toList(), key = { it.index }) { (index, tag) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { tag.tag?.let { viewModel.onQueryChange(it); viewModel.search() } }
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (index < 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(24.dp),
                    )
                    Text(
                        text = tag.translated_name ?: tag.tag.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // 清空搜索历史确认
        if (confirmClear) {
            ConfirmDialog(
                title = stringResource(R.string.search_history_clear_title),
                message = stringResource(R.string.search_history_clear_message),
                confirmText = stringResource(R.string.search_history_clear),
                onConfirm = {
                    viewModel.clearHistory()
                    confirmClear = false
                },
                onDismiss = { confirmClear = false },
            )
        }
        // 单条搜索历史删除确认（长按历史胶囊）
        pendingDelete?.let { entity ->
            ConfirmDialog(
                title = stringResource(R.string.search_history_delete_title),
                message = stringResource(R.string.search_history_delete_message, entity.keyword),
                confirmText = stringResource(com.pixiv.reader.core.ui.R.string.common_delete),
                onConfirm = {
                    viewModel.removeHistory(entity)
                    pendingDelete = null
                },
                onDismiss = { pendingDelete = null },
            )
        }
    }
}

/** 搜索历史胶囊：单击搜索、长按删除。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HistoryChip(
    text: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(AppShapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = Spacing.md, vertical = 7.dp),
    )
}

/**
 * 发现入口区：pixivision 特辑 / 画师榜 / AI 榜 / 年代榜 / 壁纸榜（两列小卡布局）。
 *
 * @param onOpenPixivision 打开 pixivision 特辑列表
 * @param onOpenUserRanking 打开画师榜（推荐创作者）
 * @param onOpenAiRanking 打开 AI 榜
 * @param onOpenEraRanking 打开年代榜（历史某天榜单）
 * @param onOpenWallpaperRanking 打开壁纸榜
 * @return 无返回值
 */
@Composable
private fun DiscoverEntries(
    onOpenPixivision: () -> Unit,
    onOpenUserRanking: () -> Unit,
    onOpenAiRanking: () -> Unit,
    onOpenEraRanking: () -> Unit,
    onOpenWallpaperRanking: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
        // 分区标题
        Row(
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Explore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.s18),
            )
            Text(
                text = stringResource(R.string.discover_entries_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = Spacing.xsPlus),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            DiscoverEntryCard(
                icon = Icons.Filled.TravelExplore,
                label = stringResource(R.string.discover_entry_pixivision),
                onClick = onOpenPixivision,
                modifier = Modifier.weight(1f),
            )
            DiscoverEntryCard(
                icon = Icons.Filled.Groups,
                label = stringResource(R.string.discover_entry_user_ranking),
                onClick = onOpenUserRanking,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            DiscoverEntryCard(
                icon = Icons.Filled.SmartToy,
                label = stringResource(R.string.discover_entry_ai_ranking),
                onClick = onOpenAiRanking,
                modifier = Modifier.weight(1f),
            )
            DiscoverEntryCard(
                icon = Icons.Filled.History,
                label = stringResource(R.string.discover_entry_era_ranking),
                onClick = onOpenEraRanking,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            DiscoverEntryCard(
                icon = Icons.Filled.Wallpaper,
                label = stringResource(R.string.discover_entry_wallpaper_ranking),
                onClick = onOpenWallpaperRanking,
                modifier = Modifier.weight(1f),
            )
            // 补位：保持与上行等宽的两列节奏
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * 发现入口小卡：圆形图标底 + 标题 + 箭头（surfaceContainer 卡，整卡可点）。
 *
 * @param icon 入口图标
 * @param label 入口标题
 * @param onClick 点击回调
 * @param modifier 布局参数（两列布局调用方传 `weight(1f)`）
 * @return 无返回值
 */
@Composable
private fun DiscoverEntryCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(AppShapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.smPlus),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            modifier = Modifier
                .size(Sizes.s32)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), AppShapes.circle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Sizes.s18),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
