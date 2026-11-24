package com.pixiv.reader.feature.user.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.Gson
import com.pixiv.reader.core.database.entity.ReadLaterEntity
import com.pixiv.reader.core.ui.component.card.NovelCard
import com.pixiv.reader.core.ui.component.grid.IllustWaterfallGrid
import com.pixiv.reader.core.ui.component.input.ConfirmDialog
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.layout.SegmentedPager
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.user.R
import com.pixiv.reader.feature.user.data.restoreIllust
import com.pixiv.reader.feature.user.data.restoreNovelCardData
import com.pixiv.reader.feature.user.state.ReadLaterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadLaterRoute(
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    viewModel: ReadLaterViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showClearConfirm by remember { mutableStateOf(false) }
    val gson = remember { Gson() }
    // 取值必须与 VM 筛选字符串一致（"illust" / "novel"）
    val readLaterTabs = listOf("illust", "novel")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.read_later_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        FilledTonalIconButton(onClick = { showClearConfirm = true }) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = stringResource(R.string.history_clear),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
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
                // 选中态跟 Pager 落页，点击反向滚页，落页回写 VM 筛选（页 0=作品 / 1=小说）
                SegmentedPager(
                    tabs = readLaterTabs,
                    onSelect = viewModel::setFilter,
                    tabLabel = { if (it == "illust") R.string.history_filter_illust else R.string.history_filter_novel },
                    pageContent = { page, _ ->
                        when (page) {
                            0 -> ReadLaterIllustList(
                                entries = items,
                                gson = gson,
                                onOpenIllust = onOpenIllust,
                                onOpenUser = onOpenUser,
                            )
                            else -> ReadLaterNovelList(
                                entries = items,
                                gson = gson,
                                context = context,
                                onOpenNovel = onOpenNovel,
                                onOpenUser = onOpenUser,
                            )
                        }
                    },
                )
            }
        }
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.read_later_clear_title),
            message = stringResource(R.string.read_later_clear_message),
            confirmText = stringResource(R.string.history_clear),
            onConfirm = {
                viewModel.clearAll()
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false },
        )
    }
}

@Composable
private fun ReadLaterIllustList(
    entries: List<ReadLaterEntity>,
    gson: Gson,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    // 无 remember 时父级任何重组都会触发全量 Gson 反序列化
    val illusts = remember(entries) { entries.map { restoreIllust(it.payloadJson, gson, it.targetId, it.title, it.coverUrl) } }
    if (illusts.isEmpty()) {
        ReadLaterEmpty(
            icon = Icons.Filled.Image,
            title = stringResource(R.string.read_later_empty_illust_title),
            hint = stringResource(R.string.read_later_empty_hint),
        )
        return
    }
    IllustWaterfallGrid(
        illusts = illusts,
        onItemClick = onOpenIllust,
        onLoadMore = {},
        hasMore = false,
        isLoadingMore = false,
        onOpenUser = onOpenUser,
    )
}

@Composable
private fun ReadLaterNovelList(
    entries: List<ReadLaterEntity>,
    gson: Gson,
    context: Context,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    // 条目与还原结果成对保存，列表 key 仍用条目自身 id
    val cards = remember(entries, context, gson) {
        entries.map { entry ->
            entry to restoreNovelCardData(
                entry.payloadJson,
                gson,
                entry.targetId,
                entry.title ?: context.getString(R.string.untitled),
                entry.coverUrl,
                requireValidId = true,
            )
        }
    }
    if (entries.isEmpty()) {
        ReadLaterEmpty(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            title = stringResource(R.string.read_later_empty_novel_title),
            hint = stringResource(R.string.read_later_empty_hint),
        )
        return
    }
    // 动效方案在组合期取好：item lambda 不是组合上下文，无法在其中读取 MaterialTheme
    val motionScheme = MaterialTheme.motionScheme
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.smPlus),
    ) {
        items(cards, key = { it.first.id }) { (entry, card) ->
            NovelCard(
                novel = card,
                onClick = { onOpenNovel(entry.targetId) },
                onOpenAuthor = { card.authorId.takeIf { it != 0L }?.let(onOpenUser) },
                onToggleFavorite = {},
                onTagClick = {},
                modifier = Modifier.animateItem(
                    fadeInSpec = motionScheme.fastEffectsSpec<Float>(),
                    placementSpec = motionScheme.fastSpatialSpec<IntOffset>(),
                ),
            )
        }
    }
}

// ── 空态（快照还原转换见 SnapshotRestore.kt） ────────────────────────────────

@Composable
private fun ReadLaterEmpty(
    icon: ImageVector,
    title: String,
    hint: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(AppShapes.circle)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}
