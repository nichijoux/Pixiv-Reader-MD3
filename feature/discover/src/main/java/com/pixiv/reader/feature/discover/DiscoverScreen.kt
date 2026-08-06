package com.pixiv.reader.feature.discover

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.AutocompleteTag
import com.pixiv.api.model.Illust
import com.pixiv.api.model.Novel
import com.pixiv.api.model.TrendingTag
import com.pixiv.reader.core.common.PixivLinkType
import com.pixiv.reader.core.common.PixivUrlParser
import com.pixiv.reader.core.database.entity.SearchHistoryEntity
import com.pixiv.reader.core.ui.component.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.ConfirmDialog
import com.pixiv.reader.core.ui.component.EmptyBox
import com.pixiv.reader.core.ui.component.IllustWaterfallGrid
import com.pixiv.reader.core.ui.component.NovelCard
import com.pixiv.reader.core.ui.component.NovelCardData
import com.pixiv.reader.feature.discover.R
import kotlinx.coroutines.launch

/**
 * 发现页搜索：热门 + 历史 → 联想 → 结果（TabRow + HorizontalPager 滑动切换插画/小说/用户）。
 * 筛选入口在搜索框右侧，面板按当前类型动态渲染。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverRoute(
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    initialQuery: String? = null,
    viewModel: DiscoverViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val type by viewModel.type.collectAsStateWithLifecycle()
    val hasSearched by viewModel.hasSearched.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val hotTags by viewModel.hotTags.collectAsStateWithLifecycle()
    val history by viewModel.searchHistory.collectAsStateWithLifecycle()
    val toolOptions by viewModel.toolOptions.collectAsStateWithLifecycle()
    val genreOptions by viewModel.genreOptions.collectAsStateWithLifecycle()

    var showFilter by remember { mutableStateOf(false) }

    /**
     * 统一搜索提交入口：若输入是 pixiv 链接（如 novel/show.php?id=…）则直接跳对应详情页，
     * 否则走普通搜索。URL 跳转不写搜索历史、不发搜索请求。
     */
    val submitSearch: () -> Unit = {
        val link = PixivUrlParser.parse(query)
        when (link?.type) {
            PixivLinkType.NOVEL -> onOpenNovel(link.id)
            PixivLinkType.SERIES -> onOpenSeries(link.id)
            PixivLinkType.ILLUST -> onOpenIllust(link.id)
            PixivLinkType.USER -> onOpenUser(link.id)
            null -> viewModel.search()
        }
    }

    // 跨 Tab 标签搜索：从别处带关键词进入则自动搜索；携带 pixiv 链接则直接跳详情
    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) {
            viewModel.onQueryChange(initialQuery)
            val link = PixivUrlParser.parse(initialQuery)
            when (link?.type) {
                PixivLinkType.NOVEL -> onOpenNovel(link.id)
                PixivLinkType.SERIES -> onOpenSeries(link.id)
                PixivLinkType.ILLUST -> onOpenIllust(link.id)
                PixivLinkType.USER -> onOpenUser(link.id)
                null -> viewModel.search()
            }
        }
    }

    // 平板限宽居中：搜索栏 + TabRow + 结果列表不超过 MAX_CONTENT_WIDTH_DP
    AdaptiveContentBox {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
        ) {
            // 搜索栏（聚焦高亮 + 清除/搜索按钮）
            SearchField(
                query = query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = submitSearch,
                onClear = viewModel::clearSearch,
                onOpenFilter = { showFilter = true },
            )

            when {
                hasSearched && query.isNotBlank() -> SearchResultPager(
                    type = type,
                    viewModel = viewModel,
                    onOpenIllust = onOpenIllust,
                    onOpenNovel = onOpenNovel,
                    onOpenCover = onOpenCover,
                    onOpenUser = onOpenUser,
                    onOpenSeries = onOpenSeries,
                )
                query.isNotBlank() -> SuggestionList(
                    suggestions = suggestions,
                    onPick = { viewModel.onQueryChange(it); viewModel.search() },
                )
                else -> IdlePanel(
                    hotTags = hotTags,
                    history = history,
                    viewModel = viewModel,
                )
            }
        }
    }

    if (showFilter) {
        FilterBottomSheet(
            filters = filters,
            type = type,
            toolOptions = toolOptions,
            genreOptions = genreOptions,
            detailed = hasSearched,
            onDismiss = { showFilter = false },
            onApply = {
                viewModel.applyFilters(it)
                viewModel.search()
                showFilter = false
            },
        )
    }
}

// ── 搜索栏 ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onOpenFilter: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotBlank()) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear))
                        }
                        TextButton(onClick = onSearch) { Text(stringResource(R.string.search_action)) }
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        )
        IconButton(
            onClick = onOpenFilter,
            modifier = Modifier
                .clip(RoundedCornerShape(21.dp))
                .size(42.dp),
        ) {
            Icon(
                Icons.Filled.FilterList,
                contentDescription = stringResource(R.string.search_filter),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

// ── 初始态：热门 + 搜索历史 ──────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdlePanel(
    hotTags: List<TrendingTag>,
    history: List<SearchHistoryEntity>,
    viewModel: DiscoverViewModel,
) {
    var confirmClear by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SearchHistoryEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            if (history.isNotEmpty()) {
                item(key = "history_title") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Text(
                            text = stringResource(R.string.search_history_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 6.dp).weight(1f),
                        )
                        TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.search_history_clear), color = MaterialTheme.colorScheme.error) }
                    }
                }
                // 历史胶囊：点击搜索、长按删除单条
                item(key = "history_chips") {
                    FlowRow(
                        modifier = Modifier.padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 8.dp)) }
            }
            item(key = "hot_title") {
                Text(
                    text = stringResource(R.string.search_hot_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
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
private fun HistoryChip(
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
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

// ── 聚焦态：联想 ─────────────────────────────────────────────────────────────

@Composable
private fun SuggestionList(
    suggestions: List<AutocompleteTag>,
    onPick: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (suggestions.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
        items(suggestions, key = { it.name ?: it.hashCode() }) { tag ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { tag.name?.let(onPick) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text(
                    text = tag.name.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── 结果态：TabRow + HorizontalPager ─────────────────────────────────────────

@Composable
private fun SearchResultPager(
    type: SearchType,
    viewModel: DiscoverViewModel,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { SearchType.entries.size })
    val scope = rememberCoroutineScope()
    val initialIndex = SearchType.entries.indexOf(type).coerceAtLeast(0)
    val filters by viewModel.filters.collectAsStateWithLifecycle()

    // 滑动切页 → 同步类型
    LaunchedEffect(pagerState.currentPage) {
        val page = pagerState.currentPage
        if (page in SearchType.entries.indices) {
            viewModel.setType(SearchType.entries[page])
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = initialIndex.coerceAtMost(SearchType.entries.size - 1),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            SearchType.entries.forEachIndexed { index, t ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(stringResource(t.labelRes)) },
                )
            }
        }
        HorizontalPager(state = pagerState) { page ->
            when (SearchType.entries.getOrNull(page)) {
                SearchType.ILLUST -> if (filters.mode == SearchMode.HOT) {
                    HotIllustGrid(viewModel, onOpenIllust, onOpenUser)
                } else {
                    IllustSearchResults(viewModel, onOpenIllust, onOpenUser)
                }
                SearchType.NOVEL -> if (filters.mode == SearchMode.HOT) {
                    HotNovelList(viewModel, onOpenNovel, onOpenCover, onOpenUser, onOpenSeries)
                } else {
                    NovelSearchResults(viewModel, onOpenNovel, onOpenCover, onOpenUser, onOpenSeries)
                }
                SearchType.USER -> UserSearchResults(viewModel, onOpenUser)
                null -> {}
            }
        }
    }
}

// ── 热门模式：一次性完整列表 ────────────────────────────────────────────────

@Composable
private fun HotIllustGrid(
    viewModel: DiscoverViewModel,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    val popular by viewModel.popularIllusts.collectAsStateWithLifecycle()
    if (popular.isEmpty()) {
        EmptyBox(stringResource(R.string.search_no_hot))
        return
    }
    IllustWaterfallGrid(
        illusts = popular,
        onItemClick = onOpenIllust,
        onLoadMore = {},
        hasMore = false,
        isLoadingMore = false,
        onOpenUser = onOpenUser,
    )
}

@Composable
private fun HotNovelList(
    viewModel: DiscoverViewModel,
    onOpenNovel: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
) {
    val popular by viewModel.popularNovels.collectAsStateWithLifecycle()
    if (popular.isEmpty()) {
        EmptyBox(stringResource(R.string.search_no_hot))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(popular, key = { it.id }) { novel ->
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
                onTagClick = { tag ->
                    viewModel.onQueryChange(tag)
                    viewModel.search()
                },
                onSeriesClick = { novel.series?.id?.let(onOpenSeries) },
            )
        }
    }
}
