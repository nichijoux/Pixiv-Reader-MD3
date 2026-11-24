package com.pixiv.reader.feature.discover.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.TrendingTag
import com.pixiv.reader.core.database.entity.SearchHistoryEntity
import com.pixiv.reader.core.ui.component.input.AccentIconBadgeCircle
import com.pixiv.reader.core.ui.component.input.ConfirmDialog
import com.pixiv.reader.core.ui.component.layout.SectionTitle
import com.pixiv.reader.core.ui.theme.SettingAccentAmber
import com.pixiv.reader.core.ui.theme.SettingAccentBlue
import com.pixiv.reader.core.ui.theme.SettingAccentGreen
import com.pixiv.reader.core.ui.theme.SettingAccentOrange
import com.pixiv.reader.core.ui.theme.SettingAccentPink
import com.pixiv.reader.core.ui.theme.SettingAccentTeal
import com.pixiv.reader.core.ui.theme.SettingAccentViolet
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.feature.discover.R
import com.pixiv.reader.feature.discover.state.DiscoverViewModel

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
                        SectionTitle(
                            text = stringResource(R.string.search_history_title),
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.History,
                            accent = SettingAccentBlue,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
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
            if (hotTags.isNotEmpty()) {
                item(key = "hot_title") {
                    SectionTitle(
                        text = stringResource(R.string.search_hot_title),
                        modifier = Modifier.padding(top = Spacing.sm),
                        icon = Icons.Filled.Whatshot,
                        accent = SettingAccentOrange,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                item(key = "hot_chips") {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        hotTags.take(6).forEachIndexed { index, tag ->
                            HotTagChip(
                                rank = index + 1,
                                label = tag.translated_name ?: tag.tag.orEmpty(),
                                onClick = {
                                    tag.tag?.let {
                                        viewModel.onQueryChange(it)
                                        viewModel.search()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

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

@Composable
private fun HotTagChip(
    rank: Int,
    label: String,
    onClick: () -> Unit,
) {
    val top = rank <= 3
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "hotTagPress",
    )
    Surface(
        onClick = onClick,
        shape = AppShapes.pill,
        color = if (top) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        interactionSource = interaction,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.xs, end = Spacing.md, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
        ) {
            Box(
                modifier = Modifier
                    .size(Sizes.s20)
                    .background(
                        if (top) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        AppShapes.circle,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$rank",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (top) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (top) FontWeight.SemiBold else FontWeight.Normal,
                color = if (top) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp),
            )
        }
    }
}

@Composable
private fun DiscoverEntries(
    onOpenPixivision: () -> Unit,
    onOpenUserRanking: () -> Unit,
    onOpenAiRanking: () -> Unit,
    onOpenEraRanking: () -> Unit,
    onOpenWallpaperRanking: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
        SectionTitle(
            text = stringResource(R.string.discover_entries_title),
            modifier = Modifier.padding(top = Spacing.sm),
            icon = Icons.Filled.Explore,
            accent = SettingAccentViolet,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            DiscoverEntryCard(
                icon = Icons.Filled.TravelExplore,
                label = stringResource(R.string.discover_entry_pixivision),
                onClick = onOpenPixivision,
                modifier = Modifier.weight(1f),
                accent = SettingAccentTeal,
            )
            DiscoverEntryCard(
                icon = Icons.Filled.Groups,
                label = stringResource(R.string.discover_entry_user_ranking),
                onClick = onOpenUserRanking,
                modifier = Modifier.weight(1f),
                accent = SettingAccentPink,
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
                accent = SettingAccentViolet,
            )
            DiscoverEntryCard(
                icon = Icons.Filled.History,
                label = stringResource(R.string.discover_entry_era_ranking),
                onClick = onOpenEraRanking,
                modifier = Modifier.weight(1f),
                accent = SettingAccentAmber,
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
                accent = SettingAccentGreen,
            )
            // 补位占位，使第 5 个入口与上行保持等宽两列
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DiscoverEntryCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Color.Unspecified,
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
        // 图标点缀色由 AccentIconBadgeCircle 经 resolveAccent 解析，彩色风格关闭时回退主题主色
        AccentIconBadgeCircle(
            icon = icon,
            accent = if (accent == Color.Unspecified) MaterialTheme.colorScheme.primary else accent,
            size = Sizes.s32,
            iconSize = Sizes.s18,
        )
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
