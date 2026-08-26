package com.pixiv.reader.feature.illust

import android.annotation.SuppressLint
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.common.format.formatCount
import com.pixiv.reader.core.common.ui.WindowSizeClass
import com.pixiv.reader.core.network.model.IllustPageInfo
import com.pixiv.reader.core.network.ugoira.UgoiraFrame
import com.pixiv.reader.core.ui.component.card.UserAvatar
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.component.image.UgoiraPlayer
import com.pixiv.reader.core.ui.component.input.VerticalActionButton
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.layout.currentWindowSizeClass
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.FavoriteRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IllustDetailRoute(
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenViewer: (Long, Int) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenComments: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    viewModel: IllustViewModel = hiltViewModel(),
) {
    val illust by viewModel.illust.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val ugoiraFrames by viewModel.ugoiraFrames.collectAsStateWithLifecycle()
    val ugoiraProgress by viewModel.ugoiraProgress.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val isBookmarking by viewModel.isBookmarking.collectAsStateWithLifecycle()
    val isAuthorFollowed by viewModel.isAuthorFollowed.collectAsStateWithLifecycle()
    val isAuthorFollowing by viewModel.isAuthorFollowing.collectAsStateWithLifecycle()

    var currentPage by remember { mutableIntStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    val notificationHostState = rememberNotificationHostState()
    UiMessageEffect(viewModel.message, notificationHostState)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.illust_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.illust_cd_back)
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.illust_cd_more)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.illust_menu_download_original)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Download,
                                        contentDescription = null
                                    )
                                },
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                VerticalActionButton(
                    icon = Icons.Filled.Fullscreen,
                    label = stringResource(R.string.illust_action_fullscreen),
                    active = false,
                    enabled = true,
                    onClick = { onOpenViewer(illust?.id ?: 0L, pageToOpen) },
                    modifier = Modifier.weight(1f),
                )
                VerticalActionButton(
                    icon = if (isBookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    label = stringResource(if (isBookmarked) R.string.illust_bookmarked else R.string.illust_menu_bookmark),
                    active = isBookmarked,
                    enabled = !isBookmarking,
                    onClick = viewModel::toggleBookmark,
                    modifier = Modifier.weight(1f),
                    // 收藏激活用红心（保留 App 收藏色习惯）
                    activeIconTint = FavoriteRed,
                )
                VerticalActionButton(
                    icon = Icons.Filled.Download,
                    label = stringResource(R.string.illust_cd_download),
                    active = false,
                    enabled = true,
                    onClick = viewModel::download,
                    modifier = Modifier.weight(1f),
                )
                VerticalActionButton(
                    icon = Icons.Filled.ModeComment,
                    label = stringResource(R.string.illust_cd_comments),
                    active = false,
                    enabled = true,
                    onClick = { onOpenComments(illust?.id ?: 0L) },
                    modifier = Modifier.weight(1f),
                )
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
                    onPageChange = { currentPage = it },
                    onOpenViewer = { onOpenViewer(illust?.id ?: 0L, it) },
                    onOpenUser = onOpenUser,
                    onOpenIllust = onOpenIllust,
                    onSearchTag = onSearchTag,
                    isAuthorFollowed = isAuthorFollowed,
                    isAuthorFollowing = isAuthorFollowing,
                    onToggleFollowAuthor = viewModel::toggleFollowAuthor,
                    viewModel = viewModel,
                )
            }
        } else {
            // 手机单列
            AdaptiveContentBox(modifier = Modifier.padding(padding)) {
                when {
                    isLoading && illust == null -> LoadingBox()
                    error != null && illust == null -> ErrorBox(message = error?.let {
                        stringResource(
                            it.res,
                            *it.args.toTypedArray()
                        )
                    }, onRetry = viewModel::load)

                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (pages.isNotEmpty()) {
                            item(key = "pager") {
                                PagePager(
                                    pages = pages,
                                    ugoiraFrames = ugoiraFrames,
                                    ugoiraProgress = ugoiraProgress,
                                    onPageChange = { currentPage = it },
                                    onOpenViewer = { onOpenViewer(illust?.id ?: 0L, it) },
                                )
                            }
                        }
                        item(key = "info") {
                            InfoSection(
                                illust = illust,
                                onOpenUser = onOpenUser,
                                onSearchTag = onSearchTag,
                                isAuthorFollowed = isAuthorFollowed,
                                isAuthorFollowing = isAuthorFollowing,
                                onToggleFollowAuthor = viewModel::toggleFollowAuthor,
                                expandableIntro = true,
                            )
                        }
                        item(key = "related") { RelatedSection(viewModel, onOpenIllust) }
                    }
                }
            }
        }
    }
}

/** 平板布局：主内容单列（图 + 作者 + 相关），评论走通用评论页。 */
@Composable
private fun TwoPaneContent(
    modifier: Modifier,
    illust: Illust?,
    pages: List<IllustPageInfo>,
    onPageChange: (Int) -> Unit,
    onOpenViewer: (Int) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenIllust: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    isAuthorFollowed: Boolean,
    isAuthorFollowing: Boolean,
    onToggleFollowAuthor: () -> Unit,
    viewModel: IllustViewModel,
) {
    val ugoiraFrames by viewModel.ugoiraFrames.collectAsStateWithLifecycle()
    val ugoiraProgress by viewModel.ugoiraProgress.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (pages.isNotEmpty()) {
            PagePager(
                pages = pages,
                ugoiraFrames = ugoiraFrames,
                ugoiraProgress = ugoiraProgress,
                onPageChange = onPageChange,
                onOpenViewer = onOpenViewer,
            )
        }
        InfoSection(
            illust = illust,
            onOpenUser = onOpenUser,
            onSearchTag = onSearchTag,
            isAuthorFollowed = isAuthorFollowed,
            isAuthorFollowing = isAuthorFollowing,
            onToggleFollowAuthor = onToggleFollowAuthor,
            expandableIntro = false,
        )
        RelatedSection(viewModel, onOpenIllust)
    }
}

/**
 * 多 P Pager：容器高度按「图片真实宽高比」自适应（无黑边、无闪变）。
 * 用 AsyncImage 的 onSuccess 拿到 drawable 真实尺寸计算比例；
 * spinner 仅在真正加载中显示，加载完成或失败即隐藏。
 *
 * @param ugoiraFrames 动图帧（ugoira 作品由 ViewModel 加载）；非空时首页（动图作品仅 1 页）
 *   叠加 [UgoiraPlayer] 播放动画——静态封面 AsyncImage 保留（量比例 + 帧未就绪兜底显示）
 * @param ugoiraProgress 动图 zip 下载进度 0..1；非 null 且帧未就绪时首页叠加转圈 + 百分比
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun PagePager(
    pages: List<IllustPageInfo>,
    onPageChange: (Int) -> Unit,
    onOpenViewer: (Int) -> Unit,
    ugoiraFrames: List<UgoiraFrame> = emptyList(),
    ugoiraProgress: Float? = null,
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val currentPage = pagerState.currentPage
    LaunchedEffect(currentPage) { onPageChange(currentPage) }

    val current = pages.getOrNull(currentPage)
    // 图片加载完成后的真实宽高比（drawable.intrinsic）。
    // 以 URL 为 remember key：切页时组合期同步重置——必须先于同组合内 AsyncImage 的回调。
    // 不能用 LaunchedEffect 重置：Coil 内存命中走 Main.immediate 同步 fast path，
    // onSuccess 会在组合期间先触发、随后被 LaunchedEffect 覆盖回 false，且不再有第二次回调，
    // 导致「图片已显示但转圈永挂」（预加载完成的页必现）。
    var imageRatio by remember(current?.displayUrl) { mutableStateOf<Float?>(null) }
    // 当前页加载完成或失败（用于隐藏 spinner）
    var loadDone by remember(current?.displayUrl) { mutableStateOf(false) }
    // 加载失败标记（显示手动重试覆盖层）+ 重试代次（自增强制重建请求绕过失败状态）
    var loadFailed by remember(current?.displayUrl) { mutableStateOf(false) }
    var retryKey by remember(current?.displayUrl) { mutableIntStateOf(0) }
    fun retry() {
        loadFailed = false
        loadDone = false
        imageRatio = null
        retryKey++
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
        // 嵌套 lambda（pager item）无法隐式访问 BoxWithConstraints receiver：提前捕获
        val pagerMaxWidth = maxWidth

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pagerHeight),
        ) {
            HorizontalPager(
                state = pagerState,
                // 预组合相邻页：翻页前邻图已在加载，滑动即显
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { index ->
                val p = pages[index]
                if (index == currentPage) {
                    Box {
                        // key(retryKey)：手动重试时自增，强制重建 painter 重新发起请求
                        key(retryKey) {
                            AsyncImage(
                                model = p.displayUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                                onSuccess = { res ->
                                    // Coil 2.7：State.Success.result 是 SuccessResult，含 drawable
                                    val d = res.result.drawable
                                    if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                                        imageRatio =
                                            d.intrinsicWidth.toFloat() / d.intrinsicHeight.toFloat()
                                    }
                                    loadDone = true
                                    loadFailed = false
                                },
                                onError = {
                                    loadDone = true
                                    loadFailed = true
                                },
                            )
                        }
                        // 加载失败：点击手动重试（动图封面失败不拦截播放，帧就绪照常播）
                        if (loadFailed && ugoiraFrames.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .clickable { retry() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = stringResource(R.string.illust_load_retry),
                                        tint = Color.White,
                                        modifier = Modifier.size(28.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.illust_load_retry),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                }
                            }
                        }
                        // 动图：帧就绪播放 zip 帧动画；下载中显示转圈 + 百分比；均叠加静态封面之上
                        if (index == 0) {
                            when {
                                ugoiraFrames.isNotEmpty() -> {
                                    val maxDecodeSize =
                                        with(LocalDensity.current) { pagerMaxWidth.roundToPx() }
                                    UgoiraPlayer(
                                        frames = ugoiraFrames,
                                        modifier = Modifier.fillMaxSize(),
                                        maxDecodeSize = maxDecodeSize,
                                        contentScale = ContentScale.Fit,
                                    )
                                }

                                ugoiraProgress != null -> {
                                    // zip 下载中：半透明黑底 + 转圈 + 百分比（帧就绪后切换播放）
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.35f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(32.dp),
                                            )
                                            Text(
                                                text = "${(ugoiraProgress * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
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
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoSection(
    illust: Illust?,
    onOpenUser: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    isAuthorFollowed: Boolean,
    isAuthorFollowing: Boolean,
    onToggleFollowAuthor: () -> Unit,
    expandableIntro: Boolean,
) {
    if (illust == null) return
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
        Text(
            text = illust.title.orEmpty(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        // 作者行：头像 + 昵称（整行可点击进主页）› 关注/取关胶囊顶到行最右
        val authorId = illust.user?.id
        Row(
            modifier = Modifier
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = authorId != null) { authorId?.let(onOpenUser) }
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            UserAvatar(
                name = illust.user?.name,
                avatarUrl = illust.user?.profile_image_urls?.best(),
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = illust.user?.name.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // 占满剩余宽度：weight 槽占满 → 关注胶囊被推到行最右
                modifier = Modifier.weight(1f),
            )
            AuthorFollowPill(
                isFollowed = isAuthorFollowed,
                enabled = !isAuthorFollowing,
                onClick = onToggleFollowAuthor,
            )
        }
        // 统计行：浏览 / 收藏 / 页数 均分（纯文字，无卡片背景）
        Row(modifier = Modifier.padding(top = 16.dp)) {
            StatBlock(
                icon = Icons.Filled.Visibility,
                value = formatCount((illust.total_view ?: 0).toLong()),
                label = stringResource(R.string.illust_stat_view),
                modifier = Modifier.weight(1f),
            )
            StatBlock(
                icon = Icons.Filled.Favorite,
                value = formatCount((illust.total_bookmarks ?: 0).toLong()),
                label = stringResource(R.string.illust_stat_bookmark),
                modifier = Modifier.weight(1f),
            )
            StatBlock(
                icon = Icons.Filled.Collections,
                value = "${illust.page_count}P",
                label = stringResource(R.string.illust_stat_pages),
                modifier = Modifier.weight(1f),
            )
        }
        // 标签：可点击跳搜索该 tag
        val tags = illust.tags.orEmpty().take(8)
        if (tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tags.forEach { tag ->
                    val tagName = tag.displayName ?: tag.name.orEmpty()
                    if (tagName.isNotBlank()) {
                        Text(
                            text = "#$tagName",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .clickable { onSearchTag(tagName) }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
        // 简介：首行缩进两格；手机 6 行截断 + 展开/收起（短简介不显示按钮），平板完整显示
        val caption = illust.caption
        if (!caption.isNullOrBlank()) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            var truncated by remember { mutableStateOf(false) }
            val clamped = expandableIntro && !expanded
            Text(
                text = caption,
                // 首行缩进两格放 style（Text 无 textIndent 参数）
                style = MaterialTheme.typography.bodyMedium.copy(textIndent = TextIndent(firstLine = 2.em)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (clamped) 6 else Int.MAX_VALUE,
                overflow = if (clamped) TextOverflow.Ellipsis else TextOverflow.Clip,
                // 展开/收起时高度平滑过渡
                modifier = Modifier
                    .padding(top = 14.dp)
                    .animateContentSize(),
                onTextLayout = { layout: TextLayoutResult ->
                    // 仅在 clamped 时检测是否真的溢出（展开后 maxLines 无限，溢出恒 false）
                    if (clamped) truncated = layout.hasVisualOverflow
                },
            )
            if (expandableIntro && (truncated || expanded)) {
                // 展开 / 收起：居中按钮（未截断时不出现）
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(if (expanded) R.string.illust_collapse else R.string.illust_expand),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** 统计块：图标 + 数值 + 标签（weight(1f) 均分整行，块内水平居中）。 */
@Composable
private fun StatBlock(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 5.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** 作者关注 / 取关胶囊（作者行顶右）：未关注 = 实心主色胶囊，已关注 = 浅底 + 主色边框。 */
@Composable
private fun AuthorFollowPill(
    isFollowed: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val container = if (isFollowed) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }
    val content = if (isFollowed) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimary
    }
    Text(
        text = stringResource(if (isFollowed) R.string.illust_followed else R.string.illust_follow),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = content,
        maxLines = 1,
        modifier = Modifier
            .clip(AppShapes.pill)
            .border(
                width = 1.dp,
                color = if (isFollowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                shape = AppShapes.pill,
            )
            .background(container)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

// ── 相关作品 ──

@Composable
private fun RelatedSection(viewModel: IllustViewModel, onOpenIllust: (Long) -> Unit) {
    val items by viewModel.relatedPaged.items.collectAsStateWithLifecycle()
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            stringResource(R.string.illust_section_related),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.id }) { related ->
                Column(
                    modifier = Modifier
                        .width(150.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { onOpenIllust(related.id) },
                ) {
                    PixivImage(
                        url = related.image_urls?.medium ?: related.image_urls?.square_medium,
                        contentDescription = related.title,
                        // 固定 4:3 比例（非正方形、非真实宽高）
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f),
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

