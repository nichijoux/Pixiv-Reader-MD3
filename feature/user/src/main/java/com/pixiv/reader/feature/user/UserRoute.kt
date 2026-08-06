package com.pixiv.reader.feature.user

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.NovelSeriesItem
import com.pixiv.reader.core.common.formatCountForNovel
import com.pixiv.reader.core.ui.component.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.EmptyBox
import com.pixiv.reader.core.ui.component.ErrorBox
import com.pixiv.reader.core.ui.component.IllustWaterfallGrid
import com.pixiv.reader.core.ui.component.LoadingBox
import com.pixiv.reader.core.ui.component.NotificationHost
import com.pixiv.reader.core.ui.component.NovelCard
import com.pixiv.reader.core.ui.component.NovelCardData
import com.pixiv.reader.core.ui.component.PixivImage
import com.pixiv.reader.core.ui.component.SeriesBookCover
import com.pixiv.reader.core.ui.component.UserAvatar
import com.pixiv.reader.core.ui.component.rememberNotificationHostState
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Durations
import kotlinx.coroutines.launch

/**
 * 用户主页（P5 重设计）：详情统计 + 关注/取关/拉黑 + 4 分区（插画/漫画/小说/系列）。
 * 顶部 Tab 支持左右滑动切换（HorizontalPager），每段独立分页（PagedState 驻留 VM）。
 * 统计格可点击：插画/小说 → 滑动切段；收藏/关注 → 进入该用户的公开收藏/关注列表页。
 *
 * @param onBack 返回
 * @param onOpenIllust 打开作品详情
 * @param onOpenNovel 打开小说详情
 * @param onOpenCover 打开全屏大图（小说封面 / 头部头像共用）
 * @param onOpenUser 打开用户主页
 * @param onSearchTag 标签搜索（跳发现页）
 * @param onOpenSeries 打开小说系列详情
 * @param onOpenUserBookmarks 打开该用户公开收藏
 * @param onOpenUserFollowing 打开该用户关注列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserRoute(
    userId: Long,
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
    onOpenUser: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenUserBookmarks: () -> Unit,
    onOpenUserFollowing: () -> Unit,
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
    val seriesCovers by viewModel.seriesCovers.collectAsStateWithLifecycle()

    val sections = UserSection.entries
    val pagerState = rememberPagerState(
        initialPage = sections.indexOf(section).coerceAtLeast(0),
        pageCount = { sections.size },
    )
    val scope = rememberCoroutineScope()

    // 滑动切页 → 同步分区（未加载则加载该段）
    LaunchedEffect(pagerState.currentPage) {
        val page = pagerState.currentPage
        if (page in sections.indices) {
            viewModel.selectSection(sections[page])
        }
    }

    val notificationHostState = rememberNotificationHostState()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.message.collect { msg ->
            notificationHostState.show(context.getString(msg.res, *msg.args.toTypedArray()))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(user?.name ?: stringResource(R.string.user_title_default), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { NotificationHost(notificationHostState) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        AdaptiveContentBox(modifier = Modifier.padding(padding)) {
            when {
                isLoading && user == null -> UserProfileSkeleton()
                error != null && user == null -> error!!.let { msg ->
                    ErrorBox(message = stringResource(msg.res, *msg.args.toTypedArray()), onRetry = viewModel::load)
                }
                user == null -> EmptyBox(stringResource(R.string.user_not_found))
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
                        onScrollToSection = { sec ->
                            scope.launch { pagerState.animateScrollToPage(sections.indexOf(sec)) }
                        },
                        onOpenUserBookmarks = onOpenUserBookmarks,
                        onOpenUserFollowing = onOpenUserFollowing,
                        onOpenAvatar = onOpenCover,
                    )
                    // 分区 Tab：PrimaryTabRow 均分占满（手机/平板一致，4 个短标签均放得下）
                    PrimaryTabRow(
                        selectedTabIndex = pagerState.currentPage.coerceIn(0, (sections.size - 1).coerceAtLeast(0)),
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        for (index in sections.indices) {
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = { Text(stringResource(sections[index].labelRes)) },
                            )
                        }
                    }
                    // 分区内容（Pager 每页只 collect 自己段的状态）
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f),
                    ) { page ->
                        when (sections.getOrNull(page)) {
                            UserSection.ILLUST -> SectionIllust(
                                paged = viewModel.illustPaged,
                                onOpenIllust = onOpenIllust,
                                onOpenUser = onOpenUser,
                                onToggleFavorite = viewModel::toggleIllustFavorite,
                                onRetry = viewModel::load,
                                onLoadMore = viewModel::loadMore,
                            )
                            UserSection.MANGA -> SectionIllust(
                                paged = viewModel.mangaPaged,
                                onOpenIllust = onOpenIllust,
                                onOpenUser = onOpenUser,
                                onToggleFavorite = viewModel::toggleIllustFavorite,
                                onRetry = viewModel::load,
                                onLoadMore = viewModel::loadMore,
                            )
                            UserSection.NOVEL -> SectionNovel(
                                paged = viewModel.novelPaged,
                                onOpenNovel = onOpenNovel,
                                onOpenCover = onOpenCover,
                                onOpenUser = onOpenUser,
                                onOpenSeries = onOpenSeries,
                                onToggleFavorite = { id, fav -> viewModel.toggleNovelFavorite(id, fav) },
                                onTagClick = onSearchTag,
                                onRetry = viewModel::load,
                                onLoadMore = viewModel::loadMore,
                            )
                            UserSection.SERIES -> SectionSeries(
                                paged = viewModel.seriesPaged,
                                covers = seriesCovers,
                                onOpenSeries = onOpenSeries,
                                onRetry = viewModel::load,
                                onLoadMore = viewModel::loadMore,
                            )
                            null -> EmptyBox("")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserHeader(
    user: com.pixiv.api.model.User,
    profile: com.pixiv.api.model.Profile?,
    isFollowed: Boolean,
    isFollowing: Boolean,
    isBlocked: Boolean,
    isBlocking: Boolean,
    onToggleFollow: () -> Unit,
    onToggleBlock: () -> Unit,
    onScrollToSection: (UserSection) -> Unit,
    onOpenUserBookmarks: () -> Unit,
    onOpenUserFollowing: () -> Unit,
    onOpenAvatar: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(
                name = user.name,
                avatarUrl = user.profile_image_urls?.best(),
                modifier = Modifier.size(64.dp),
                onClick = { user.profile_image_urls?.best()?.let(onOpenAvatar) },
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    text = user.name.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!user.account.isNullOrBlank()) {
                    Text(
                        text = "@${user.account}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // 关注 / 拉黑 双按钮（移除三点下拉）
            FilledTonalButton(
                onClick = onToggleFollow,
                enabled = !isFollowing,
            ) {
                Text(if (isFollowed) stringResource(R.string.user_following) else stringResource(R.string.user_follow))
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onToggleBlock,
                enabled = !isBlocking,
                colors = if (isBlocked) {
                    ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.outlinedButtonColors()
                },
            ) {
                Text(if (isBlocked) stringResource(R.string.user_unblock) else stringResource(R.string.user_block))
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
        // 统计格：插画 / 小说 / 收藏 / 关注（可点击）
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatItem(stringResource(R.string.user_stat_illust), profile?.total_illusts) {
                onScrollToSection(UserSection.ILLUST)
            }
            StatItem(stringResource(R.string.user_stat_novel), profile?.total_novels) {
                onScrollToSection(UserSection.NOVEL)
            }
            StatItem(stringResource(R.string.user_stat_bookmark), profile?.total_bookmarks_public) {
                onOpenUserBookmarks()
            }
            StatItem(stringResource(R.string.user_stat_follow), profile?.total_follow_users) {
                onOpenUserFollowing()
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: Int?,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
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
    paged: com.pixiv.reader.core.network.paging.PagedState<com.pixiv.api.model.Illust>,
    onOpenIllust: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
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
        items.isEmpty() -> EmptyBox(stringResource(R.string.user_empty_illust))
        else -> IllustWaterfallGrid(
            illusts = items,
            onItemClick = onOpenIllust,
            onLoadMore = onLoadMore,
            hasMore = hasMore,
            isLoadingMore = isLoadingMore,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
            onToggleFavorite = onToggleFavorite,
            onOpenUser = onOpenUser,
        )
    }
}

@Composable
private fun SectionNovel(
    paged: com.pixiv.reader.core.network.paging.PagedState<com.pixiv.api.model.Novel>,
    onOpenNovel: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
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
        items.isEmpty() -> EmptyBox(stringResource(R.string.user_empty_novel))
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
                    onToggleFavorite = { fav -> onToggleFavorite(novel.id, fav) },
                    onTagClick = onTagClick,
                    onSeriesClick = { novel.series?.id?.let(onOpenSeries) },
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

@Composable
private fun SectionSeries(
    paged: com.pixiv.reader.core.network.paging.PagedState<NovelSeriesItem>,
    covers: Map<Long, String>,
    onOpenSeries: (Long) -> Unit,
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
        items.isEmpty() -> EmptyBox(stringResource(R.string.user_empty_series))
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id }) { series ->
                SeriesCard(
                    series = series,
                    coverUrl = covers[series.id],
                    onClick = { onOpenSeries(series.id) },
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

/** 系列卡片：封面（真实图/图标兜底）+ 标题/简介 + N 篇/总字数 + 连载状态/已追更徽章。 */
@Composable
private fun SeriesCard(
    series: NovelSeriesItem,
    coverUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 封面：有 URL 显示真实封面（3:4，自动 Referer），无则 MD3 图标容器兜底
        if (!coverUrl.isNullOrBlank()) {
            PixivImage(
                url = coverUrl,
                contentDescription = series.title,
                modifier = Modifier
                    .width(72.dp)
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(12.dp)),
            )
        } else {
            SeriesBookCover(
                modifier = Modifier.size(width = 72.dp, height = 96.dp),
                iconSize = 36.dp,
            )
        }
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Text(
                text = series.title.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val caption = series.caption
            if (!caption.isNullOrBlank()) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 篇数 + 总字数（text 弱化）
                Text(
                    text = stringResource(R.string.user_series_parts, series.content_count),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (series.total_character_count > 0) {
                    Text(
                        text = stringResource(
                            R.string.user_series_chars,
                            formatCountForNovel(series.total_character_count),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 连载状态徽章（药丸）
                SeriesStatusBadge(
                    text = stringResource(
                        if (series.is_concluded) R.string.user_series_concluded else R.string.user_series_ongoing,
                    ),
                    container = if (series.is_concluded) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    content = if (series.is_concluded) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
                // 已追更标记（次要）
                if (series.watchlist_added) {
                    SeriesStatusBadge(
                        text = stringResource(R.string.user_series_watchlisted),
                        container = MaterialTheme.colorScheme.surfaceContainerHigh,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** MD3 药丸徽章（AssistChip 视觉，扁平无交互）。 */
@Composable
private fun SeriesStatusBadge(
    text: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = Modifier
            .clip(AppShapes.pill)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** 加载骨架：头部（头像/名称/按钮/统计）+ Tab 条 + 瀑布流占位，呼吸脉冲。 */
@Composable
private fun UserProfileSkeleton() {
    val transition = rememberInfiniteTransition(label = "userSkeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = Durations.PAGE_SWITCH_ANIM_MS),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "userSkeletonAlpha",
    )
    val color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(color),
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(color),
                )
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(0.3f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(color),
                )
            }
            Box(
                modifier = Modifier
                    .size(72.dp, 36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(color),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .size(56.dp, 34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(4) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(color),
                )
            }
        }
    }
}
