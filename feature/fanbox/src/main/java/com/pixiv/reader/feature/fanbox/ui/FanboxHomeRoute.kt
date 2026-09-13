package com.pixiv.reader.feature.fanbox.ui

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.FanboxCreator
import com.pixiv.api.model.FanboxPost
import com.pixiv.reader.core.network.fanbox.FanboxHeaderInterceptor
import com.pixiv.reader.core.ui.component.card.UserAvatar
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.layout.ListDetailOverlay
import com.pixiv.reader.core.ui.component.layout.isDetailPaneEnabled
import com.pixiv.reader.core.ui.component.list.LoadMoreItem
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.fanbox.R
import com.pixiv.reader.feature.fanbox.state.FanboxCreatorsState
import com.pixiv.reader.feature.fanbox.state.FanboxHomeViewModel
import com.pixiv.reader.feature.fanbox.state.FanboxPostViewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch

/** 首页 tab（投稿 / 推荐创作者）。 */
private enum class FanboxHomeTab(@param:StringRes val labelRes: Int) {
    POSTS(R.string.fanbox_tab_posts),
    CREATORS(R.string.fanbox_tab_creators),
}

/**
 * FANBOX 首页：SegmentedButton + Pager 双 tab（沿项目 Downloads/History 惯式）——
 * 「投稿」流（触底翻页）与「推荐创作者」（单页，点击进创作者网页）。
 * 会话过期（401）时以「重新登录」引导替代普通错误态；从登录页返回本页时自动重试
 * （cookie 换新后无需手动点重试，仍 401 则继续展示过期引导）。
 * 平板（内容区 ≥704dp）启用 Master-Detail：帖子卡点击右栏滑入详情 pane；手机退化为全屏路由。
 *
 * @param onBack 返回上一页
 * @param onOpenPost 打开帖子详情全屏路由（手机端 / pane 未启用时）
 * @param onOpenWeb 打开 App 内可见 WebView（url, title）
 * @param onOpenImage 打开全屏图片预览（url, title）
 * @param viewModel 页面状态
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FanboxHomeRoute(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenWeb: (String, String) -> Unit,
    onOpenImage: (String, String) -> Unit,
    viewModel: FanboxHomeViewModel = hiltViewModel(),
) {
    val posts by viewModel.postsPaged.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.postsPaged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.postsPaged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.postsPaged.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.postsPaged.error.collectAsStateWithLifecycle()
    val creators by viewModel.creators.collectAsStateWithLifecycle()
    val sessionExpired by viewModel.sessionExpired.collectAsStateWithLifecycle()

    // Master-Detail：选中的帖子 id（平板详情 pane；手机端不启用恒为 null 不生效）
    var selectedPostId by rememberSaveable { mutableStateOf<String?>(null) }
    // pane 启用判定（点击分流用；回调 lambda 非 composable 上下文，需在此捕获）
    val detailPaneEnabled = isDetailPaneEnabled()

    val pagerState = rememberPagerState(pageCount = { FanboxHomeTab.entries.size })
    val scope = rememberCoroutineScope()
    val notificationHost = rememberNotificationHostState()
    UiMessageEffect(viewModel.message, notificationHost)

    // 从登录页（或其他网页）返回时自动重试：过期态在 401 时置位、登录换新 cookie 后
    // 服务端已有效，但无返回通知路径——借本页 ON_RESUME 触发刷新；首次进入时
    // 过期态为 false，与 init 的加载不重复
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && viewModel.sessionExpired.value) {
                viewModel.retry()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fanbox_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.fanbox_cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        snackbarHost = { NotificationHost(notificationHost) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        // 平板 Master-Detail：主列表左移 + 右侧详情 pane（手机不启用，退化为主列表原样）
        ListDetailOverlay(
            selected = selectedPostId,
            onClose = { selectedPostId = null },
            // 消费已应用的 padding，pane 内系统栏 inset 自适应
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            listContent = { listMax ->
                AdaptiveContentBox(maxWidth = listMax) {
                    Column(modifier = Modifier.fillMaxSize()) {
                // tab 分段：点击反向滚页（选中态跟 Pager 落页）
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                ) {
                    FanboxHomeTab.entries.forEachIndexed { index, tab ->
                        SegmentedButton(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = FanboxHomeTab.entries.size),
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
                HorizontalPager(state = pagerState) { page ->
                    when (FanboxHomeTab.entries.getOrNull(page)) {
                        FanboxHomeTab.POSTS -> PostsPage(
                            posts = posts,
                            isLoading = isLoading,
                            isLoadingMore = isLoadingMore,
                            hasMore = hasMore,
                            error = error,
                            sessionExpired = sessionExpired,
                            onRetry = viewModel::retry,
                            onLoadMore = viewModel::loadMorePosts,
                            // 平板（pane 启用）→ 选中进右栏详情；手机 → 全屏路由跳转
                            onOpenPost = { id ->
                                if (detailPaneEnabled) selectedPostId = id else onOpenPost(id)
                            },
                            onLoginAgain = { onOpenWeb(FanboxHeaderInterceptor.FANBOX_LOGIN_URL, "") },
                        )
                        FanboxHomeTab.CREATORS -> CreatorsPage(
                            state = creators,
                            onRetry = viewModel::loadCreators,
                            onOpenCreator = { creatorId, name ->
                                onOpenWeb("${FanboxHeaderInterceptor.FANBOX_URL}@$creatorId", name)
                            },
                        )
                        null -> {}
                    }
                }
            }
                }
            },
            detailPane = {
                // 右侧详情 pane：内嵌 FanboxPostViewModel（同一 backstack entry 作用域）
                val postVm: FanboxPostViewModel = hiltViewModel()
                FanboxPostPane(
                    selectedPostId = selectedPostId,
                    onOpenWeb = onOpenWeb,
                    onOpenImage = onOpenImage,
                    viewModel = postVm,
                )
            },
        )
    }
}

/**
 * 平板详情 pane：selectedId 为空显示占位，非空加载并渲染帖子详情。
 * 内容复用 [FanboxPostContent]（全屏路由同款内容块）；WebView / 图片全屏经回调上抛宿主路由。
 *
 * @param selectedPostId 当前选中的帖子 id（null 显示占位）
 * @param onOpenWeb 打开 App 内可见 WebView（url, title）
 * @param onOpenImage 打开全屏图片预览（url, title）
 * @param viewModel 帖子详情 VM（宿主提供的 backstack entry 作用域实例）
 */
@Composable
private fun FanboxPostPane(
    selectedPostId: String?,
    onOpenWeb: (String, String) -> Unit,
    onOpenImage: (String, String) -> Unit,
    viewModel: FanboxPostViewModel,
) {
    val host = rememberNotificationHostState()
    UiMessageEffect(viewModel.message, host)
    // 选中变化 → 驱动 VM 加载对应帖子（全屏路由经 SavedStateHandle 取参，pane 手动驱动）
    LaunchedEffect(selectedPostId) {
        val id = selectedPostId
        if (id != null && id != viewModel.postId) viewModel.loadPost(id)
    }
    if (selectedPostId == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.fanbox_pane_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val postState by viewModel.post.collectAsStateWithLifecycle()
    val plansState by viewModel.plans.collectAsStateWithLifecycle()
    val commentsState by viewModel.comments.collectAsStateWithLifecycle()
    val context = LocalContext.current

    /** 链接统一路由：fanbox.cc → 可见 WebView；站外 → 系统浏览器。 */
    fun openLink(url: String) {
        val target = runCatching { Uri.parse(url).host }.getOrNull()
        if (target == "fanbox.cc" || target?.endsWith(".fanbox.cc") == true) {
            onOpenWeb(url, postState.post?.title.orEmpty())
        } else {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
    }

    FanboxPostContent(
        postState = postState,
        plansState = plansState,
        commentsState = commentsState,
        onRetry = viewModel::retry,
        onOpenWeb = onOpenWeb,
        onOpenImage = onOpenImage,
        openLink = ::openLink,
    )
}

/**
 * 投稿流页：会话过期 → 过期引导；首载中 / 错误 / 空三态；列表 + 触底加载。
 */
@Composable
private fun PostsPage(
    posts: List<FanboxPost>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    error: String?,
    sessionExpired: Boolean,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenPost: (String) -> Unit,
    onLoginAgain: () -> Unit,
) {
    when {
        sessionExpired && posts.isEmpty() -> SessionExpiredBox(onLoginAgain = onLoginAgain, onRetry = onRetry)
        isLoading && posts.isEmpty() -> LoadingBox()
        posts.isEmpty() && error != null -> ErrorBox(message = error, onRetry = onRetry)
        posts.isEmpty() -> EmptyBox(text = stringResource(R.string.fanbox_empty_posts))
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            items(posts, key = { it.id }) { post -> FanboxPostCard(post = post, onClick = { onOpenPost(post.id) }) }
            if (hasMore) {
                item(key = "load_more") {
                    LoadMoreItem(isLoadingMore = isLoadingMore, onLoadMore = onLoadMore)
                }
            }
        }
    }
}

/**
 * 会话过期引导（无投稿数据时整页展示）：去网页重登 + 返回后重试。
 */
@Composable
private fun SessionExpiredBox(onLoginAgain: () -> Unit, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.fanbox_session_expired),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.fanbox_session_expired_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Button(onClick = onLoginAgain, modifier = Modifier.padding(top = Spacing.lg)) {
            Text(stringResource(R.string.fanbox_login_again))
        }
        // 网页重登返回后由此恢复（同时复位过期态）
        TextButton(onClick = onRetry, modifier = Modifier.padding(top = Spacing.xs)) {
            Text(stringResource(R.string.fanbox_retry))
        }
    }
}

/**
 * 投稿卡（M3 Expressive）：封面 + 标题 + 创作者行 + 摘要/标签 + 徽标与计数元信息行。
 * 摘要、标签、预览等可选内容为空时收起，不留空位。
 */
@Composable
private fun FanboxPostCard(post: FanboxPost, onClick: () -> Unit) {
    val fallbackTitle = stringResource(R.string.fanbox_post_title)
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            if (post.coverUrl.isNotEmpty()) {
                PixivImage(
                    url = post.coverUrl,
                    contentDescription = post.title,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
            }
            Column(modifier = Modifier.padding(Spacing.md)) {
                // 标题
                Text(
                    text = post.title.orEmpty().ifEmpty { fallbackTitle },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // 创作者行：头像 + 名字 + 时间
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = Spacing.xs),
                ) {
                    UserAvatar(
                        name = post.user?.name,
                        avatarUrl = post.user?.iconUrl,
                        modifier = Modifier.size(Sizes.s24),
                    )
                    Text(
                        text = post.user?.name.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = Spacing.xs).weight(1f),
                    )
                    Text(
                        text = formatFanboxDate(post.publishedDatetime),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 摘要（列表接口常为空串，空则不占位）
                val excerpt = post.excerpt.orEmpty()
                if (excerpt.isNotBlank()) {
                    Text(
                        text = excerpt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                // 标签：文本式 #tag（列表密度优先，不上 chip）
                val tags = post.tags.orEmpty().filter { it.isNotEmpty() }
                if (tags.isNotEmpty()) {
                    Text(
                        text = tags.joinToString("  ") { "#$it" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                // 元信息行：费用/受限/R-18 徽标 + 点赞/评论计数
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = Spacing.sm),
                ) {
                    when {
                        post.feeRequired > 0 -> FanboxBadge(
                            text = stringResource(R.string.fanbox_fee_required, post.feeRequired),
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        post.isRestricted -> FanboxBadge(
                            text = stringResource(R.string.fanbox_restricted_badge),
                            container = MaterialTheme.colorScheme.surfaceVariant,
                            content = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> FanboxBadge(
                            text = stringResource(R.string.fanbox_fee_free),
                            container = MaterialTheme.colorScheme.surfaceVariant,
                            content = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (post.hasAdultContent) {
                        FanboxBadge(
                            text = stringResource(R.string.fanbox_r18),
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(start = Spacing.xs),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    MetaCount(icon = Icons.Filled.FavoriteBorder, count = post.likeCount)
                    MetaCount(
                        icon = Icons.Filled.ModeComment,
                        count = post.commentCount,
                        modifier = Modifier.padding(start = Spacing.md),
                    )
                }
            }
        }
    }
}

/** 小型 tonal 徽标（费用 / 受限 / R-18 / 支持关系），胶囊形 + labelSmall。 */
@Composable
private fun FanboxBadge(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = container,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = Spacing.xsPlus, vertical = Spacing.xxs),
        )
    }
}

/** 计数元信息（点赞 / 评论）：小图标 + 数值，次级色。 */
@Composable
private fun MetaCount(icon: ImageVector, count: Int, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Sizes.s16),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xxs),
        )
    }
}

/**
 * 推荐创作者页：三态 + 创作者行（点击进创作者网页）。
 */
@Composable
private fun CreatorsPage(
    state: FanboxCreatorsState,
    onRetry: () -> Unit,
    onOpenCreator: (String, String) -> Unit,
) {
    when {
        state.isLoading -> LoadingBox()
        state.error != null -> ErrorBox(message = state.error, onRetry = onRetry)
        state.items.isEmpty() -> EmptyBox(text = stringResource(R.string.fanbox_empty_creators))
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            items(
                state.items,
                key = { it.creatorId ?: it.user?.userId ?: it.hashCode().toString() },
            ) { creator ->
                FanboxCreatorCard(
                    creator = creator,
                    onClick = {
                        creator.creatorId?.let { id -> onOpenCreator(id, creator.user?.name.orEmpty()) }
                    },
                )
            }
        }
    }
}

/**
 * 创作者卡（M3 Expressive）：头像 + 名字 + 支持/关注关系徽标 + 分类 + 简介 +
 * 主页展示图预览排（常不足 4 张，缺的格子收起）。
 */
@Composable
private fun FanboxCreatorCard(creator: FanboxCreator, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(
                    name = creator.user?.name,
                    avatarUrl = creator.user?.iconUrl,
                    modifier = Modifier.size(Sizes.s40),
                )
                Column(modifier = Modifier.weight(1f).padding(start = Spacing.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = creator.user?.name.orEmpty(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (creator.isSupported) {
                            FanboxBadge(
                                text = stringResource(R.string.fanbox_supported),
                                container = MaterialTheme.colorScheme.primaryContainer,
                                content = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(start = Spacing.xs),
                            )
                        } else if (creator.isFollowed) {
                            FanboxBadge(
                                text = stringResource(R.string.fanbox_followed),
                                container = MaterialTheme.colorScheme.surfaceVariant,
                                content = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = Spacing.xs),
                            )
                        }
                    }
                    // 分类（服务端常给 null，可空展示）
                    val category = creator.category.orEmpty()
                    if (category.isNotBlank()) {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xxs),
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // description 跨模块属性，先落局部变量再判空（isNullOrBlank 后无法智能转换）
            val description = creator.description
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            // 主页展示图预览排（缩略图优先；最多 4 张）
            val previews = creator.profileItems.orEmpty().take(4)
            if (previews.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.padding(top = Spacing.sm),
                ) {
                    previews.forEach { item ->
                        PixivImage(
                            url = item.thumbnailUrl ?: item.imageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(4f / 3f)
                                .clip(MaterialTheme.shapes.small),
                        )
                    }
                }
            }
        }
    }
}

/**
 * FANBOX ISO 时间截断展示（`2026-08-01T12:34:56+09:00` → `2026-08-01 12:34`）。
 *
 * @param iso 服务端原始时间串
 * @return 截断时间；入参异常原样返回
 */
private fun formatFanboxDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        val t = iso.indexOf('T')
        if (t <= 0) iso else "${iso.substring(0, t)} ${iso.substring(t + 1, minOf(t + 6, iso.length))}"
    }.getOrDefault(iso)
}
