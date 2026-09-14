package com.pixiv.reader.feature.user.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.feature.user.state.HistoryFilter
import com.pixiv.reader.feature.user.state.HistoryViewModel
import com.pixiv.reader.feature.user.R
import com.pixiv.reader.feature.user.data.restoreIllust
import com.pixiv.reader.feature.user.data.restoreNovelCardData
import com.google.gson.Gson
import com.pixiv.reader.core.database.entity.BrowseHistoryEntity
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.layout.BackTopAppBar
import com.pixiv.reader.core.ui.component.layout.SegmentedPager
import com.pixiv.reader.core.ui.component.input.ConfirmDialog
import com.pixiv.reader.core.ui.component.card.CreatorProfile
import com.pixiv.reader.core.ui.component.card.CreatorProfileCard
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.grid.IllustWaterfallGrid
import com.pixiv.reader.core.ui.component.card.NovelCard
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes

/**
 * 阅读历史：TabRow（作品/小说/用户）+ HorizontalPager 滑动切换。
 * 三类内容各自使用通用组件：作品 `IllustCard`（瀑布流，含收藏；插画/漫画/动图共用）/ 小说 `NovelCard`（payloadJson 完整信息）/ 用户 `CreatorProfileCard`。
 *
 * @param onBack 返回
 * @param onOpenIllust 打开作品详情
 * @param onOpenNovel 打开小说详情
 * @param onOpenUser 打开用户主页
 * @param onOpenSeries 打开小说系列详情
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryRoute(
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BackTopAppBar(title = stringResource(R.string.history_title), onBack = onBack) {
                if (history.isNotEmpty()) {
                    // 清空历史：图标 + 文字（删除色），点击弹确认框
                    TextButton(onClick = { showClearConfirm = true }) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(Sizes.s18),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = stringResource(R.string.history_clear),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = Spacing.xs),
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        AdaptiveContentBox(modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 类型分段（作品 / 小说 / 用户）+ 滑动内容：SegmentedPager 双向同步
                // （选中态跟 Pager 落页，点击反向滚页；落页回调 VM 同步筛选）
                SegmentedPager(
                    tabs = HistoryFilter.entries,
                    onSelect = viewModel::selectFilter,
                    tabLabel = { it.labelRes },
                    pageContent = { _, tab ->
                        when (tab) {
                            HistoryFilter.ILLUST -> IllustHistoryList(
                                entries = history,
                                viewModel = viewModel,
                                onOpenIllust = onOpenIllust,
                                onOpenUser = onOpenUser,
                            )
                            HistoryFilter.NOVEL -> NovelHistoryList(
                                entries = history,
                                viewModel = viewModel,
                                onOpenNovel = onOpenNovel,
                                onOpenUser = onOpenUser,
                                onOpenSeries = onOpenSeries,
                                context = context,
                            )
                            HistoryFilter.USER -> UserHistoryList(
                                entries = history,
                                viewModel = viewModel,
                                onOpenUser = onOpenUser,
                                context = context,
                            )
                        }
                    },
                )
            }
        }
    }

    // 清空浏览历史确认
    if (showClearConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.history_clear_title),
            message = stringResource(R.string.history_clear_message),
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
private fun IllustHistoryList(
    entries: List<BrowseHistoryEntity>,
    viewModel: HistoryViewModel,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    val illusts = entries.map { restoreIllust(it.payloadJson, it.targetId, it.title, it.coverUrl) }
    if (illusts.isEmpty()) {
        EmptyBox(stringResource(R.string.history_empty_illust))
        return
    }
    IllustWaterfallGrid(
        illusts = illusts,
        onItemClick = onOpenIllust,
        onLoadMore = {},
        hasMore = false,
        isLoadingMore = false,
        onToggleFavorite = { id, fav -> viewModel.toggleIllustFavorite(id, fav) },
        onOpenUser = onOpenUser,
    )
}

// ── 小说：NovelCard（payloadJson 完整信息） ──────────────────────────────────

@Composable
private fun NovelHistoryList(
    entries: List<BrowseHistoryEntity>,
    viewModel: HistoryViewModel,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    context: Context,
) {
    if (entries.isEmpty()) {
        EmptyBox(stringResource(R.string.history_empty_novel))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.smPlus),
    ) {
        items(entries, key = { it.id }) { entry ->
            val card = restoreNovelCardData(
                entry.payloadJson,
                Gson(),
                entry.targetId,
                entry.title ?: context.getString(R.string.untitled),
                entry.coverUrl,
            )
            NovelCard(
                novel = card,
                onClick = { onOpenNovel(entry.targetId) },
                onOpenAuthor = { card.authorId.takeIf { it != 0L }?.let(onOpenUser) },
                onToggleFavorite = { fav -> viewModel.toggleNovelFavorite(entry.targetId, fav) },
                onTagClick = {},
                onSeriesClick = { card.seriesId?.let(onOpenSeries) },
            )
        }
    }
}

// ── 用户：CreatorProfileCard ─────────────────────────────────────────────────

@Composable
private fun UserHistoryList(
    entries: List<BrowseHistoryEntity>,
    viewModel: HistoryViewModel,
    onOpenUser: (Long) -> Unit,
    context: Context,
) {
    val users = entries
    if (users.isEmpty()) {
        EmptyBox(stringResource(R.string.history_empty_user))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.smPlus),
    ) {
        items(users, key = { it.id }) { entry ->
            CreatorProfileCard(
                profile = entry.toCreatorProfile(context),
                onToggleFollow = {},
                onClick = { onOpenUser(entry.targetId) },
            )
        }
    }
}

// ── 数据转换（历史快照 → 通用组件数据，插画/小说见 SnapshotRestore.kt） ──────────

private fun BrowseHistoryEntity.toCreatorProfile(context: Context): CreatorProfile = CreatorProfile(
    id = targetId,
    name = title ?: context.getString(R.string.unknown_user),
    avatarUrl = coverUrl,
)
