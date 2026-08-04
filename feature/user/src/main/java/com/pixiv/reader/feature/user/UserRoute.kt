package com.pixiv.reader.feature.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ui.component.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.EmptyBox
import com.pixiv.reader.core.ui.component.ErrorBox
import com.pixiv.reader.core.ui.component.IllustWaterfallGrid
import com.pixiv.reader.core.ui.component.LoadingBox
import com.pixiv.reader.core.ui.component.NovelCard
import com.pixiv.reader.core.ui.component.NovelCardData
import com.pixiv.reader.core.ui.component.UserAvatar

/**
 * 用户主页（P5）：详情统计 + 关注/取关 + 分区作品（插画/漫画/小说）。
 * 小说分区 item 与搜索结果一致（NovelCard）。
 *
 * @param onBack 返回
 * @param onOpenIllust 打开作品详情
 * @param onOpenNovel 打开小说详情
 * @param onOpenReader 打开小说阅读器
 * @param onOpenUser 打开用户主页
 * @param onSearchTag 标签搜索（跳发现页）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserRoute(
    userId: Long,
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenReader: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    viewModel: UserViewModel = hiltViewModel(),
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isFollowed by viewModel.isFollowed.collectAsStateWithLifecycle()
    val isFollowing by viewModel.isFollowing.collectAsStateWithLifecycle()
    val isBlocked by viewModel.isBlocked.collectAsStateWithLifecycle()
    val isBlocking by viewModel.isBlocking.collectAsStateWithLifecycle()
    val section by viewModel.section.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.message.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(user?.name ?: "用户主页", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        AdaptiveContentBox(modifier = Modifier.padding(padding)) {
            when {
                isLoading && user == null -> LoadingBox()
                error != null && user == null -> ErrorBox(message = error.orEmpty(), onRetry = viewModel::load)
                user == null -> EmptyBox("没有找到该用户")
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    val detail = checkNotNull(user)
                    UserHeader(
                        user = detail,
                        profile = profile,
                        isFollowed = isFollowed,
                        isFollowing = isFollowing,
                        isBlocked = isBlocked,
                        isBlocking = isBlocking,
                        onToggleFollow = viewModel::toggleFollow,
                        onToggleBlock = viewModel::toggleBlock,
                    )
                    // 分区 Tab
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        UserSection.entries.forEach { sec ->
                            FilterChip(
                                selected = section == sec,
                                onClick = { viewModel.selectSection(sec) },
                                label = { Text(sec.label()) },
                            )
                        }
                    }
                    // 分区内容
                    Box(modifier = Modifier.weight(1f)) {
                        when (section) {
                            UserSection.ILLUST -> SectionIllust(
                                paged = viewModel.illustPaged,
                                onOpenIllust = onOpenIllust,
                                onRetry = viewModel::load,
                                onLoadMore = viewModel::loadMore,
                            )
                            UserSection.MANGA -> SectionIllust(
                                paged = viewModel.mangaPaged,
                                onOpenIllust = onOpenIllust,
                                onRetry = viewModel::load,
                                onLoadMore = viewModel::loadMore,
                            )
                            UserSection.NOVEL -> SectionNovel(
                                paged = viewModel.novelPaged,
                                onOpenNovel = onOpenNovel,
                                onOpenReader = onOpenReader,
                                onOpenUser = onOpenUser,
                                onToggleFavorite = { id, fav -> viewModel.toggleNovelFavorite(id, fav) },
                                onTagClick = onSearchTag,
                                onRetry = viewModel::load,
                                onLoadMore = viewModel::loadMore,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserHeader(
    user: com.example.pixivapi.model.User,
    profile: com.example.pixivapi.model.Profile?,
    isFollowed: Boolean,
    isFollowing: Boolean,
    isBlocked: Boolean,
    isBlocking: Boolean,
    onToggleFollow: () -> Unit,
    onToggleBlock: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(
                name = user.name,
                avatarUrl = user.profile_image_urls?.best(),
                modifier = Modifier.size(64.dp),
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    text = user.name.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!user.account.isNullOrBlank()) {
                    Text(
                        text = "@${user.account}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedButton(
                onClick = onToggleFollow,
                enabled = !isFollowing,
            ) {
                Text(if (isFollowed) "已关注" else "关注")
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isBlocked) "取消拉黑" else "拉黑") },
                        onClick = {
                            menuExpanded = false
                            onToggleBlock()
                        },
                        enabled = !isBlocking,
                    )
                }
            }
        }
        val comment = user.comment
        if (!comment.isNullOrBlank()) {
            Text(
                text = comment,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        // 统计格：插画 / 小说 / 收藏 / 关注
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatItem("插画", profile?.total_illusts)
            StatItem("小说", profile?.total_novels)
            StatItem("收藏", profile?.total_bookmarks_public)
            StatItem("关注", profile?.total_follow_users)
        }
    }
}

@Composable
private fun StatItem(label: String, value: Int?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value?.toString() ?: "-",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionIllust(
    paged: com.pixiv.reader.core.network.paging.PagedState<com.example.pixivapi.model.Illust>,
    onOpenIllust: (Long) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val items by paged.items.collectAsStateWithLifecycle()
    val isLoading by paged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by paged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by paged.hasMore.collectAsStateWithLifecycle()
    val error by paged.error.collectAsStateWithLifecycle()

    when {
        isLoading && items.isEmpty() -> LoadingBox()
        error != null && items.isEmpty() -> ErrorBox(message = error.orEmpty(), onRetry = onRetry)
        items.isEmpty() -> EmptyBox("暂无作品")
        else -> IllustWaterfallGrid(
            illusts = items,
            onItemClick = onOpenIllust,
            onLoadMore = onLoadMore,
            hasMore = hasMore,
            isLoadingMore = isLoadingMore,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
        )
    }
}

@Composable
private fun SectionNovel(
    paged: com.pixiv.reader.core.network.paging.PagedState<com.example.pixivapi.model.Novel>,
    onOpenNovel: (Long) -> Unit,
    onOpenReader: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onTagClick: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val items by paged.items.collectAsStateWithLifecycle()
    val isLoading by paged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by paged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by paged.hasMore.collectAsStateWithLifecycle()
    val error by paged.error.collectAsStateWithLifecycle()

    when {
        isLoading && items.isEmpty() -> LoadingBox()
        error != null && items.isEmpty() -> ErrorBox(message = error.orEmpty(), onRetry = onRetry)
        items.isEmpty() -> EmptyBox("暂无小说")
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
                        favoriteCount = novel.total_bookmarks ?: 0,
                        wordCount = novel.text_length ?: 0,
                        tags = novel.tags.orEmpty()
                            .take(6)
                            .map { it.translated_name ?: it.name ?: "" }
                            .filter { it.isNotBlank() },
                        isFavorite = novel.is_bookmarked == true,
                    ),
                    onClick = { onOpenNovel(novel.id) },
                    onOpenReader = { onOpenReader(novel.id) },
                    onOpenAuthor = { novel.user?.id?.let(onOpenUser) },
                    onToggleFavorite = { fav -> onToggleFavorite(novel.id, fav) },
                    onTagClick = onTagClick,
                )
            }
            if (hasMore) {
                item(key = "load_more") {
                    LaunchedEffect(Unit) { onLoadMore() }
                    Box(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isLoadingMore) {
                            CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

private fun UserSection.label(): String = when (this) {
    UserSection.ILLUST -> "插画"
    UserSection.MANGA -> "漫画"
    UserSection.NOVEL -> "小说"
}
