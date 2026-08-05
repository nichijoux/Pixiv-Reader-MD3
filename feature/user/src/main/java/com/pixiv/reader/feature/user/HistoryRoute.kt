package com.pixiv.reader.feature.user

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.ImageUrls
import com.pixiv.api.model.Illust
import com.google.gson.Gson
import com.pixiv.reader.core.database.entity.BrowseHistoryEntity
import com.pixiv.reader.core.ui.component.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.CreatorProfile
import com.pixiv.reader.core.ui.component.CreatorProfileCard
import com.pixiv.reader.core.ui.component.EmptyBox
import com.pixiv.reader.core.ui.component.IllustWaterfallGrid
import com.pixiv.reader.core.ui.component.NovelCard
import com.pixiv.reader.core.ui.component.NovelCardData
import kotlinx.coroutines.launch

/**
 * 阅读历史：TabRow（插画/小说/用户）+ HorizontalPager 滑动切换。
 * 三类内容各自使用通用组件：插画 `IllustCard`（瀑布流，含收藏）/ 小说 `NovelCard`（payloadJson 完整信息）/ 用户 `CreatorProfileCard`。
 *
 * @param onBack 返回
 * @param onOpenIllust 打开作品详情
 * @param onOpenNovel 打开小说详情
 * @param onOpenUser 打开用户主页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryRoute(
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val filter by viewModel.filterFlow.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { HistoryFilter.entries.size })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 滑动切页 → 同步筛选
    LaunchedEffect(pagerState.currentPage) {
        val page = pagerState.currentPage
        if (page in HistoryFilter.entries.indices) {
            viewModel.selectFilter(HistoryFilter.entries[page])
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearAll) {
                            Text(
                                text = stringResource(R.string.history_clear),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error,
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
                // TabRow：插画 / 小说 / 用户
                TabRow(
                    selectedTabIndex = filter.ordinal.coerceAtMost(HistoryFilter.entries.size - 1),
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    HistoryFilter.entries.forEachIndexed { index, f ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(stringResource(f.labelRes)) },
                        )
                    }
                }
                // 滑动内容
                HorizontalPager(state = pagerState) { page ->
                    when (HistoryFilter.entries.getOrNull(page)) {
                        HistoryFilter.ILLUST -> IllustHistoryList(
                            entries = history,
                            viewModel = viewModel,
                            onOpenIllust = onOpenIllust,
                        )
                        HistoryFilter.NOVEL -> NovelHistoryList(
                            entries = history,
                            viewModel = viewModel,
                            onOpenNovel = onOpenNovel,
                            onOpenUser = onOpenUser,
                            context = context,
                        )
                        HistoryFilter.USER -> UserHistoryList(
                            entries = history,
                            viewModel = viewModel,
                            onOpenUser = onOpenUser,
                            context = context,
                        )
                        null -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun IllustHistoryList(
    entries: List<BrowseHistoryEntity>,
    viewModel: HistoryViewModel,
    onOpenIllust: (Long) -> Unit,
) {
    val illusts = entries.map { it.toIllust() }
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
    )
}

// ── 小说：NovelCard（payloadJson 完整信息） ──────────────────────────────────

@Composable
private fun NovelHistoryList(
    entries: List<BrowseHistoryEntity>,
    viewModel: HistoryViewModel,
    onOpenNovel: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    context: Context,
) {
    if (entries.isEmpty()) {
        EmptyBox(stringResource(R.string.history_empty_novel))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(entries, key = { it.id }) { entry ->
            val card = entry.toNovelCardData(context)
            NovelCard(
                novel = card,
                onClick = { onOpenNovel(entry.targetId) },
                onOpenReader = { onOpenNovel(entry.targetId) },
                onOpenAuthor = { card.authorId.takeIf { it != 0L }?.let(onOpenUser) },
                onToggleFavorite = { fav -> viewModel.toggleNovelFavorite(entry.targetId, fav) },
                onTagClick = {},
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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

// ── 数据转换（历史快照 → 通用组件数据） ─────────────────────────────────────

private fun BrowseHistoryEntity.toIllust(): Illust {
    // 优先解析完整 payloadJson（含宽高，避免固定高度裁剪中间）；旧记录回退最小数据
    val parsed = payloadJson?.let {
        runCatching { org.json.JSONObject(it) }.getOrNull()
    }
    if (parsed != null) {
        return Illust(
            id = parsed.optLong("id", targetId),
            title = parsed.optString("title").ifEmpty { title.orEmpty() },
            image_urls = ImageUrls(medium = parsed.optString("coverUrl").ifEmpty { coverUrl.orEmpty() }),
            width = parsed.optInt("width") ?: 0,
            height = parsed.optInt("height") ?: 0,
            total_bookmarks = parsed.optInt("bookmarks").takeIf { it != 0 },
            page_count = parsed.optInt("pageCount") ?: 0,
            is_bookmarked = if (parsed.has("isBookmarked")) parsed.optBoolean("isBookmarked") else null,
        )
    }
    return Illust(id = targetId, title = title, image_urls = ImageUrls(medium = coverUrl))
}

private fun BrowseHistoryEntity.toNovelCardData(context: Context): NovelCardData {
    // 优先解析完整 payloadJson（新记录）；旧记录/失败回退最小数据
    val parsed = payloadJson?.let {
        runCatching { Gson().fromJson(it, NovelCardData::class.java) }.getOrNull()
    }
    if (parsed != null) return parsed
    return NovelCardData(
        id = targetId,
        title = title ?: context.getString(R.string.untitled),
        coverUrl = coverUrl,
        authorId = 0,
        authorName = "",
        authorAvatarUrl = null,
        publishDate = null,
        seriesTitle = null,
        favoriteCount = 0,
        wordCount = 0,
    )
}

private fun BrowseHistoryEntity.toCreatorProfile(context: Context): CreatorProfile = CreatorProfile(
    id = targetId,
    name = title ?: context.getString(R.string.unknown_user),
    avatarUrl = coverUrl,
)
