package com.pixiv.reader.feature.fanbox.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.pixiv.api.model.FanboxPost
import com.pixiv.reader.core.ui.component.card.UserAvatar
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.list.LoadMoreItem
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.feature.fanbox.R
import com.pixiv.reader.feature.fanbox.state.FanboxCreatorListViewModel
import com.pixiv.reader.feature.fanbox.state.FanboxCreatorViewModel
import com.pixiv.reader.feature.fanbox.state.FanboxHomeViewModel
import com.pixiv.reader.feature.fanbox.state.FanboxPostViewModel

/**
 * FANBOX 首页（路由 `fanbox`）：关注创作者的新帖流。
 * 未登录（无 FANBOX cookie）时渲染内嵌登录门，登录成功自动切换为列表。
 *
 * @param onBack 返回
 * @param onOpenPost 打开帖子详情
 * @param onOpenCreators 打开关注创作者列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FanboxHomeRoute(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenCreators: () -> Unit,
    viewModel: FanboxHomeViewModel = hiltViewModel(),
) {
    val needsLogin by viewModel.needsLogin.collectAsStateWithLifecycle()
    FanboxLoginGate(
        needsLogin = needsLogin,
        onLoginSuccess = viewModel::recheckAndLoad,
        onBack = onBack,
    ) {
        FanboxPostListScreen(
            title = stringResource(R.string.fanbox_home_title),
            paged = viewModel.paged,
            onBack = onBack,
            onOpenPost = onOpenPost,
            onOpenCreator = onOpenCreators,
            headerActions = {
                // 顶栏：关注创作者列表入口
                IconButton(onClick = onOpenCreators) {
                    Icon(Icons.Filled.Groups, contentDescription = stringResource(R.string.fanbox_creator_list_title))
                }
            },
            onRetry = viewModel::load,
            onLoadMore = viewModel::loadMore,
        )
    }
}

/**
 * FANBOX 关注创作者列表（路由 `fanbox_creators`）。
 *
 * @param onBack 返回
 * @param onOpenCreator 打开创作者帖子流（参数为 creatorId）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FanboxCreatorListRoute(
    onBack: () -> Unit,
    onOpenCreator: (String) -> Unit,
    viewModel: FanboxCreatorListViewModel = hiltViewModel(),
) {
    val needsLogin by viewModel.needsLogin.collectAsStateWithLifecycle()
    FanboxLoginGate(
        needsLogin = needsLogin,
        onLoginSuccess = viewModel::recheckAndLoad,
        onBack = onBack,
    ) {
        val items by viewModel.paged.items.collectAsStateWithLifecycle()
        val isLoading by viewModel.paged.isLoading.collectAsStateWithLifecycle()
        val error by viewModel.paged.error.collectAsStateWithLifecycle()
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.fanbox_creator_list_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.fanbox_cd_back))
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
                when {
                    isLoading && items.isEmpty() -> LoadingBox()
                    error != null && items.isEmpty() -> ErrorBox(message = error.orEmpty(), onRetry = viewModel::load)
                    items.isEmpty() -> EmptyBox(stringResource(R.string.fanbox_creator_empty))
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        items(items, key = { it.creator?.userId ?: it.hashCode().toString() }) { item ->
                            val creator = item.creator
                            if (creator == null) return@items
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(AppShapes.card)
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .clickable { creator.userId?.let(onOpenCreator) }
                                    .padding(Spacing.mdPlus),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                UserAvatar(
                                    name = creator.name,
                                    avatarUrl = creator.profileImageUrl,
                                    modifier = Modifier.size(Sizes.s44),
                                )
                                Text(
                                    text = creator.name.orEmpty(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = Spacing.md),
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
 * FANBOX 创作者帖子流（路由 `fanbox_creator/{creatorId}`）。
 *
 * @param onBack 返回
 * @param onOpenPost 打开帖子详情
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FanboxCreatorRoute(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    viewModel: FanboxCreatorViewModel = hiltViewModel(),
) {
    FanboxPostListScreen(
        title = stringResource(R.string.fanbox_creator_title),
        paged = viewModel.paged,
        onBack = onBack,
        onOpenPost = onOpenPost,
        onOpenCreator = null,
        headerActions = {},
        onRetry = viewModel::load,
        onLoadMore = viewModel::loadMore,
    )
}

/**
 * FANBOX 帖子详情（路由 `fanbox_post/{postId}`）：
 * 付费墙提示（feeRequired>0 且不可访问）/ 纯文本 / 图片流 / 文章 blocks 三类正文渲染。
 *
 * @param onBack 返回
 * @param onOpenCreator 点击创作者行打开其帖子流（参数为 creatorId）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FanboxPostRoute(
    onBack: () -> Unit,
    onOpenCreator: (String) -> Unit,
    viewModel: FanboxPostViewModel = hiltViewModel(),
) {
    val post by viewModel.post.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fanbox_post_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.fanbox_cd_back))
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
            when {
                isLoading && post == null -> LoadingBox()
                error != null && post == null -> ErrorBox(message = error.orEmpty(), onRetry = viewModel::load)
                post == null -> EmptyBox(stringResource(R.string.fanbox_empty))
                else -> {
                    val detail = checkNotNull(post)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(Spacing.lg),
                    ) {
                        // 标题 + 创作者行（点击进创作者帖子流）
                        Text(
                            text = detail.title.orEmpty(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Spacing.smPlus)
                                .clickable { detail.creatorId?.let(onOpenCreator) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UserAvatar(
                                name = detail.user?.name,
                                avatarUrl = detail.user?.profileImageUrl,
                                modifier = Modifier.size(Sizes.s28),
                            )
                            Text(
                                text = detail.user?.name.orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = Spacing.sm),
                            )
                            detail.publishedDateTime?.take(10)?.let { date ->
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = date,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        // 正文（付费墙判定优先）
                        if (detail.feeRequired > 0 && !detail.isAccessible) {
                            FanboxPaywallCard(feeRequired = detail.feeRequired)
                        } else {
                            detail.body?.let { body -> FanboxBodyContent(body = body) }
                                ?: detail.bodyText?.takeIf { it.isNotBlank() }?.let { text ->
                                    FanboxTextBlock(text = text)
                                }
                        }
                    }
                }
            }
        }
    }
}

/** 登录门：未登录渲染 [FanboxLoginRoute]，登录成功（needsLogin 翻转）切内容区。 */
@Composable
private fun FanboxLoginGate(
    needsLogin: Boolean,
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (needsLogin) {
        FanboxLoginRoute(
            onLoginSuccess = onLoginSuccess,
            onBack = onBack,
        )
    } else {
        content()
    }
}

/** 帖子流列表（首页 / 创作者页共用）：帖子卡 + 触底分页。 */
@Composable
private fun FanboxPostListScreen(
    title: String,
    paged: com.pixiv.reader.core.network.paging.PagedState<FanboxPost>,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenCreator: (() -> Unit)?,
    headerActions: @Composable () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val items by paged.items.collectAsStateWithLifecycle()
    val isLoading by paged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by paged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by paged.hasMore.collectAsStateWithLifecycle()
    val error by paged.error.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.fanbox_cd_back))
                    }
                },
                actions = { headerActions() },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        AdaptiveContentBox(modifier = Modifier.padding(padding)) {
            when {
                isLoading && items.isEmpty() -> LoadingBox()
                error != null && items.isEmpty() -> ErrorBox(message = error.orEmpty(), onRetry = onRetry)
                items.isEmpty() -> EmptyBox(stringResource(R.string.fanbox_empty))
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    items(items, key = { it.id ?: it.hashCode().toString() }) { post ->
                        FanboxPostCard(
                            post = post,
                            onClick = { post.id?.let(onOpenPost) },
                            onOpenCreator = onOpenCreator,
                        )
                    }
                    if (hasMore) {
                        item(key = "load_more") {
                            LoadMoreItem(isLoadingMore = isLoadingMore, onLoadMore = onLoadMore)
                        }
                    }
                }
            }
        }
    }
}

/** 帖子卡：封面（无封面用标题占位）+ 标题 + 创作者行 + 日期 + 付费徽章。 */
@Composable
private fun FanboxPostCard(
    post: FanboxPost,
    onClick: () -> Unit,
    onOpenCreator: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.cardLarge)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick),
    ) {
        post.coverImageUrl?.takeIf { it.isNotBlank() }?.let { cover ->
            PixivImage(
                url = cover,
                contentDescription = post.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f),
            )
        }
        Column(modifier = Modifier.padding(Spacing.mdPlus)) {
            Text(
                text = post.title.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.smPlus),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(
                    name = post.user?.name,
                    avatarUrl = post.user?.profileImageUrl,
                    modifier = Modifier.size(Sizes.s24),
                )
                Text(
                    text = post.user?.name.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = Spacing.sm)
                        .weight(1f)
                        .then(
                            if (onOpenCreator != null) {
                                Modifier.clickable { onOpenCreator() }
                            } else {
                                Modifier
                            },
                        ),
                )
                if (post.feeRequired > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = AppShapes.small,
                    ) {
                        Text(
                            text = "¥${post.feeRequired}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp),
                        )
                    }
                }
                post.publishedDateTime?.take(10)?.let { date ->
                    Text(
                        text = date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
            }
        }
    }
}

/** 付费墙提示卡（需赞助方案解锁全文）。 */
@Composable
private fun FanboxPaywallCard(feeRequired: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.lg),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = AppShapes.card,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Sizes.s28),
            )
            Text(
                text = stringResource(R.string.fanbox_fee_required, feeRequired),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

/** 正文文本段。 */
@Composable
private fun FanboxTextBlock(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = Spacing.md),
    )
}

/** 正文渲染：文章 blocks 优先（p/header/image），否则按 imageOrder 渲染图片流。 */
@Composable
private fun FanboxBodyContent(body: com.pixiv.api.model.FanboxPostBody) {
    val blocks = body.blocks
    if (!blocks.isNullOrEmpty()) {
        blocks.forEach { block ->
            when (block.type) {
                "header" -> Text(
                    text = block.text.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xs),
                )
                "image" -> body.imageMap?.get(block.imageId)?.let { image ->
                    FanboxImageBlock(image = image)
                }
                else -> FanboxTextBlock(text = block.text.orEmpty())
            }
        }
        return
    }
    // 图片帖：imageOrder 顺序渲染
    val order = body.imageOrder.orEmpty()
    if (order.isNotEmpty()) {
        order.forEach { key ->
            body.imageMap?.get(key)?.let { image -> FanboxImageBlock(image = image) }
        }
    }
    body.text?.takeIf { it.isNotBlank() }?.let { FanboxTextBlock(text = it) }
}

/** 正文图片（originalUrl 优先，付费未解锁时为 null 则跳过）。 */
@Composable
private fun FanboxImageBlock(image: com.pixiv.api.model.FanboxImage) {
    val url = image.originalUrl ?: image.thumbnailUrl ?: return
    PixivImage(
        url = url,
        contentDescription = null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.md)
            .clip(AppShapes.card),
    )
}
