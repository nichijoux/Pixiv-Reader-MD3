package com.pixiv.reader.feature.illust

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pixiv.api.model.Comment
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.common.WindowSizeClass
import com.pixiv.reader.core.common.formatCount
import com.pixiv.reader.core.model.IllustPageInfo
import com.pixiv.reader.core.ui.component.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.CommentInput
import com.pixiv.reader.core.ui.component.ErrorBox
import com.pixiv.reader.core.ui.component.LoadingBox
import com.pixiv.reader.core.ui.component.NotificationHost
import com.pixiv.reader.core.ui.component.PixivImage
import com.pixiv.reader.core.ui.component.UserAvatar
import com.pixiv.reader.core.ui.component.currentWindowSizeClass
import com.pixiv.reader.core.ui.component.rememberNotificationHostState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IllustDetailRoute(
    onBack: () -> Unit,
    onOpenViewer: (Long, Int) -> Unit,
    onOpenUser: (Long) -> Unit,
    viewModel: IllustViewModel = hiltViewModel(),
) {
    val illust by viewModel.illust.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val isBookmarking by viewModel.isBookmarking.collectAsStateWithLifecycle()
    val commentDraft by viewModel.commentDraft.collectAsStateWithLifecycle()

    var currentPage by remember { mutableStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    val notificationHostState = rememberNotificationHostState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.message.collect { msg -> notificationHostState.show(context.getString(msg.res, *msg.args.toTypedArray())) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.illust_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.illust_cd_back)) }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.illust_cd_more))
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.illust_menu_download_original)) },
                                leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                                onClick = { menuExpanded = false; viewModel.download() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(if (isBookmarked) R.string.illust_menu_unbookmark else R.string.illust_menu_bookmark)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isBookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        contentDescription = null,
                                    )
                                },
                                onClick = { menuExpanded = false; viewModel.toggleBookmark() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.illust_menu_report)) },
                                leadingIcon = { Icon(Icons.Filled.Report, contentDescription = null) },
                                onClick = { menuExpanded = false; viewModel.report() },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            val pageToOpen = if (illust?.isGif() == true) 0 else currentPage
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(onClick = viewModel::download) { Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.illust_cd_download)) }
                IconButton(onClick = viewModel::toggleBookmark, enabled = !isBookmarking) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(if (isBookmarked) R.string.illust_menu_unbookmark else R.string.illust_menu_bookmark),
                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = { onOpenViewer(illust?.id ?: 0L, pageToOpen) },
                    modifier = Modifier.weight(1f).height(44.dp),
                ) {
                    Text(stringResource(R.string.illust_btn_fullscreen))
                }
            }
        },
        snackbarHost = { NotificationHost(notificationHostState) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        val size = currentWindowSizeClass()
        val twoPane = size != WindowSizeClass.Compact

        if (twoPane) {
            // 平板双栏：加载中/失败也要有明确状态，避免显示空骨架
            when {
                isLoading && illust == null -> LoadingBox(modifier = Modifier.padding(padding))
                error != null && illust == null -> ErrorBox(
                    message = error?.let { stringResource(it.res, *it.args.toTypedArray()) },
                    onRetry = viewModel::load,
                    modifier = Modifier.padding(padding),
                )
                else -> TwoPaneContent(
                    modifier = Modifier.padding(padding),
                    illust = illust,
                    pages = pages,
                    currentPage = currentPage,
                    onPageChange = { currentPage = it },
                    onOpenViewer = { onOpenViewer(illust?.id ?: 0L, it) },
                    onOpenUser = onOpenUser,
                    viewModel = viewModel,
                    commentDraft = commentDraft,
                    onCommentDraftChange = viewModel::onCommentDraftChange,
                    onPostComment = viewModel::postComment,
                )
            }
        } else {
            // 手机单列
            AdaptiveContentBox(modifier = Modifier.padding(padding)) {
                when {
                    isLoading && illust == null -> LoadingBox()
                    error != null && illust == null -> ErrorBox(message = error?.let { stringResource(it.res, *it.args.toTypedArray()) }, onRetry = viewModel::load)
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (pages.isNotEmpty()) {
                            item(key = "pager") {
                                PagePager(
                                    pages = pages,
                                    onPageChange = { currentPage = it },
                                    onOpenViewer = { onOpenViewer(illust?.id ?: 0L, it) },
                                )
                            }
                        }
                        item(key = "info") { InfoSection(illust, onOpenUser) }
                        item(key = "related") { RelatedSection(viewModel) }
                        item(key = "comments") {
                            CommentSection(
                                viewModel = viewModel,
                                draft = commentDraft,
                                onDraftChange = viewModel::onCommentDraftChange,
                                onPost = viewModel::postComment,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 平板双栏布局：左侧主内容（图+作者+相关），右侧评论区（输入框固定底部） */
@Composable
private fun TwoPaneContent(
    modifier: Modifier,
    illust: Illust?,
    pages: List<IllustPageInfo>,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    onOpenViewer: (Int) -> Unit,
    onOpenUser: (Long) -> Unit,
    viewModel: IllustViewModel,
    commentDraft: String,
    onCommentDraftChange: (String) -> Unit,
    onPostComment: () -> Unit,
) {
    val comments by viewModel.commentsPaged.items.collectAsStateWithLifecycle()

    Row(modifier = modifier.fillMaxSize()) {
        // 左栏：可滚动（左:右 ≈ 3:1，右栏限宽 420dp 防止过宽）
        Column(
            modifier = Modifier
                .weight(3f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
        ) {
            if (pages.isNotEmpty()) {
                PagePager(
                    pages = pages,
                    onPageChange = onPageChange,
                    onOpenViewer = onOpenViewer,
                )
            }
            InfoSection(illust, onOpenUser)
            RelatedSection(viewModel)
        }

        VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // 右栏：评论区（输入框固定底部）
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .widthIn(max = 420.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.illust_section_comments), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                CommentList(comments)
            }
            CommentInput(
                draft = commentDraft,
                onDraftChange = onCommentDraftChange,
                onPost = onPostComment,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * 多 P Pager：容器高度按「图片真实宽高比」自适应（无黑边、无闪变）。
 * 用 AsyncImage 的 onSuccess 拿到 drawable 真实尺寸计算比例；
 * spinner 仅在真正加载中显示，加载完成或失败即隐藏。
 */
@Composable
private fun PagePager(
    pages: List<IllustPageInfo>,
    onPageChange: (Int) -> Unit,
    onOpenViewer: (Int) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val currentPage = pagerState.currentPage
    LaunchedEffect(currentPage) { onPageChange(currentPage) }

    val current = pages.getOrNull(currentPage)
    // 图片加载完成后的真实宽高比（drawable.intrinsic）
    var imageRatio by remember { mutableStateOf<Float?>(null) }
    // 当前页加载完成或失败（用于隐藏 spinner）
    var loadDone by remember { mutableStateOf(false) }

    LaunchedEffect(current?.displayUrl) {
        imageRatio = null
        loadDone = false
    }

    // 优先图片真实比例，其次网页宽高，最后兜底
    val webRatio = current
        ?.takeIf { it.width > 0 && it.height > 0 }
        ?.let { it.width.toFloat() / it.height.toFloat() }
    val effectiveRatio = imageRatio ?: webRatio ?: 1.5f
    val ready = imageRatio != null || webRatio != null

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black),
    ) {
        val targetHeight = if (effectiveRatio > 0f) maxWidth / effectiveRatio else 400.dp
        val pagerHeight by animateDpAsState(
            targetValue = targetHeight,
            animationSpec = tween(durationMillis = 250),
            label = "pagerHeight",
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pagerHeight),
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
                val p = pages[index]
                if (index == currentPage) {
                    AsyncImage(
                        model = p.displayUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        onSuccess = { res ->
                            // Coil 2.7：State.Success.result 是 SuccessResult，含 drawable
                            val d = res.result.drawable
                            if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                                imageRatio = d.intrinsicWidth.toFloat() / d.intrinsicHeight.toFloat()
                            }
                            loadDone = true
                        },
                        onError = { loadDone = true },
                    )
                } else {
                    PixivImage(
                        url = p.displayUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            if (!ready && !loadDone) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                }
            }
            // 页码 + 全屏入口（统一 32dp 高胶囊，视觉一致）
            Row(
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${pages.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { onOpenViewer(pagerState.currentPage) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Fullscreen,
                        contentDescription = stringResource(R.string.illust_cd_fullscreen),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoSection(illust: Illust?, onOpenUser: (Long) -> Unit) {
    if (illust == null) return
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = illust.title.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier
                .padding(top = 12.dp)
                .clickable { illust.user?.id?.let(onOpenUser) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            UserAvatar(
                name = illust.user?.name,
                avatarUrl = illust.user?.profile_image_urls?.best(),
                modifier = Modifier.size(36.dp),
            )
            Column {
                Text(illust.user?.name.orEmpty(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    stringResource(R.string.illust_page_count, illust.page_count ?: 0),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            StatText(stringResource(R.string.illust_stat_view), (illust.total_view ?: 0).toLong())
            StatText(stringResource(R.string.illust_stat_bookmark), (illust.total_bookmarks ?: 0).toLong())
        }
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            illust.tags.orEmpty().take(8).forEach { tag ->
                Text(
                    text = "#${tag.displayName ?: tag.name.orEmpty()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        val caption = illust.caption
        if (!caption.isNullOrBlank()) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun StatText(label: String, value: Long) {
    Column {
        Text(
            text = if (value > 0) formatCount(value) else "-",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── 相关作品 ──

@Composable
private fun RelatedSection(viewModel: IllustViewModel) {
    val items by viewModel.relatedPaged.items.collectAsStateWithLifecycle()
    Column(modifier = Modifier.padding(16.dp)) {
        Text(stringResource(R.string.illust_section_related), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.id }) { related ->
                Column(
                    modifier = Modifier
                        .width(120.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { /* 打开相关作品 */ },
                ) {
                    PixivImage(
                        url = related.image_urls?.medium ?: related.image_urls?.square_medium,
                        contentDescription = related.title,
                        modifier = Modifier.fillMaxWidth().height(110.dp),
                        contentScale = ContentScale.Crop,
                    )
                    Text(
                        text = related.title.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(6.dp),
                    )
                }
            }
        }
    }
}

// ── 评论 ──

@Composable
private fun CommentSection(
    viewModel: IllustViewModel,
    draft: String,
    onDraftChange: (String) -> Unit,
    onPost: () -> Unit,
) {
    val comments by viewModel.commentsPaged.items.collectAsStateWithLifecycle()
    Column(modifier = Modifier.padding(16.dp)) {
        Text(stringResource(R.string.illust_section_comments), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        CommentList(comments)
        Spacer(Modifier.height(12.dp))
        CommentInput(
            draft = draft,
            onDraftChange = onDraftChange,
            onPost = onPost,
        )
    }
}

@Composable
private fun CommentList(comments: List<Comment>) {
    if (comments.isEmpty()) {
        Text(
            text = stringResource(R.string.illust_comments_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        return
    }
    Column {
        comments.take(50).forEach { comment -> CommentRow(comment) }
    }
}

@Composable
private fun CommentRow(comment: Comment) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UserAvatar(
            name = comment.user?.name,
            avatarUrl = comment.user?.profile_image_urls?.best(),
            modifier = Modifier.size(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = comment.user?.name.orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = comment.comment.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
            // 树形对话：父评论下方直接渲染子回复（浅色块 + 缩进，区分父子层）。
            val replies = comment.replies.orEmpty()
            if (replies.isNotEmpty()) {
                val visibleReplies = replies.take(20)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    visibleReplies.forEachIndexed { index, reply ->
                        IllustReplyRow(reply)
                        if (index != visibleReplies.lastIndex) {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

/** 子评论行（树形对话第二层：缩进浅色块内、带小头像、无分隔线）。 */
@Composable
private fun IllustReplyRow(reply: Comment) {
    Row(verticalAlignment = Alignment.Top) {
        UserAvatar(
            name = reply.user?.name,
            avatarUrl = reply.user?.profile_image_urls?.best(),
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        ) {
            Text(
                text = reply.user?.name.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val parentName = reply.parent_comment?.user?.name
            val prefix = if (!parentName.isNullOrBlank()) stringResource(R.string.illust_reply_prefix, parentName) else ""
            Text(
                text = prefix + reply.comment.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
