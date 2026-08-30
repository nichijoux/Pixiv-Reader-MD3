package com.pixiv.reader.feature.viewer

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.common.config.ViewerOrientation
import com.pixiv.reader.core.network.model.IllustPageInfo
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.image.UgoiraPlayer
import com.pixiv.reader.core.ui.component.image.ZoomableImage
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.feedback.toNotificationType
import com.pixiv.reader.core.ui.theme.FavoriteRed
import com.pixiv.reader.core.ui.theme.ViewerScrim
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes

/**
 * 全屏插画查看器：多 P 翻页 + 捏合缩放 + 页码 + 底部操作。
 * 平板/横屏：图片自适应居中，底部操作条宽度受限。
 */
@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun ViewerRoute(
    onBack: () -> Unit,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onBack)
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val isGif by viewModel.isGif.collectAsStateWithLifecycle()
    val ugoiraFrames by viewModel.ugoiraFrames.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val isOriginal by viewModel.isOriginal.collectAsStateWithLifecycle()
    val orientation by viewModel.viewerOrientation.collectAsStateWithLifecycle()

    // 初始页在 pages 加载前 pageCount 可能为 1，直接传 initialPage>0 会越界崩溃；
    // 因此从第 0 页开始，待 pages 就绪后再滚动到目标页（钳制在合法范围）。
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { pages.size.coerceAtLeast(1) },
    )
    LaunchedEffect(pages.size) {
        if (pages.isNotEmpty()) {
            val target = viewModel.initialPage.coerceIn(0, pages.size - 1)
            if (target > 0) pagerState.scrollToPage(target)
        }
    }
    // 无缝竖向模式用列表状态（与 pagerState 二选一，按当前方向取用）
    val listState = rememberLazyListState()
    LaunchedEffect(pages.size) {
        if (pages.isNotEmpty()) {
            val target = viewModel.initialPage.coerceIn(0, pages.size - 1)
            if (target > 0) listState.scrollToItem(target)
        }
    }
    var anyZoomed by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    // 图库式工具栏显隐：仅单击图片区切换，滑动翻页不影响其显隐
    var barsVisible by remember { mutableStateOf(true) }
    val barsShown = barsVisible
    // 当前页：无缝竖向按首可见项，其余按 pager 当前页（页码指示 / 下载 / 壁纸共用）
    val currentIndex = if (orientation == ViewerOrientation.SEAMLESS) {
        listState.firstVisibleItemIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    } else {
        pagerState.currentPage
    }
    val notificationHostState = rememberNotificationHostState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.message.collect { msg ->
            notificationHostState.show(
                context.getString(
                    msg.res,
                    *msg.args.toTypedArray()
                ), type = msg.type.toNotificationType()
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ViewerScrim),
    ) {
        if (isGif) {
            // 动图同样支持单击切换工具栏（UgoiraPlayer 自身不消费点击）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { barsVisible = !barsVisible }
                    },
            ) {
                UgoiraPlayer(
                    frames = ugoiraFrames,
                    modifier = Modifier.fillMaxSize(),
                    loadingContent = {
                        Text(
                            text = stringResource(R.string.viewer_ugoira_loading),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        } else {
            // 页内容（放大 / 预览·原图切换），三种方向共用。
            // 默认 Fit 完整展示（相册标准：黑边只在图片比例 ≠ 屏幕比例时出现），
            // 缩放手势由 telephoto 处理（捏合放大/缩小、双击切换、放大后平移）
            val pageContent: @Composable (Int) -> Unit = { index ->
                val page = pages.getOrNull(index)
                if (page != null) {
                    ZoomableImage(
                        model = if (isOriginal) {
                            page.originalUrl ?: page.displayUrl
                        } else {
                            page.displayUrl
                        },
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        onZoomChanged = { zoomed -> anyZoomed = zoomed },
                        // 单击切换工具栏显隐
                        onClick = { barsVisible = !barsVisible },
                    )
                }
            }
            when (orientation) {
                // 无缝竖向：按自然宽高比连续堆叠，上下滚动（我的页-浏览设置可切）
                ViewerOrientation.SEAMLESS -> SeamlessViewer(
                    pages = pages,
                    state = listState,
                    userScrollEnabled = !anyZoomed,
                    content = pageContent,
                )
                // 竖向翻页：整页上下滑动切换
                ViewerOrientation.VERTICAL -> VerticalPager(
                    state = pagerState,
                    userScrollEnabled = !anyZoomed,
                    // 预组合相邻页：翻页前邻图已在加载，滑动即显
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize(),
                ) { index -> pageContent(index) }
                // 横向翻页（默认）
                ViewerOrientation.HORIZONTAL -> HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !anyZoomed,
                    // 预组合相邻页：翻页前邻图已在加载，滑动即显
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize(),
                ) { index -> pageContent(index) }
            }
        }

        // 顶部栏（图库式：单击图片区切换显隐，翻页手势中强制隐藏）
        AnimatedVisibility(
            visible = barsShown,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent),
                        ),
                    )
                    .statusBarsPadding()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.viewer_cd_back),
                        tint = Color.White
                    )
                }
                Text(
                    text = viewModel.illust.value?.title.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                // 更多菜单：收藏 / 下载 / 举报
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.viewer_cd_more),
                            tint = Color.White
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (isBookmarked) R.string.viewer_menu_unbookmark else R.string.viewer_menu_bookmark)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isBookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = null,
                                )
                            },
                            onClick = { menuExpanded = false; viewModel.toggleBookmark() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.viewer_menu_download_original)) },
                            leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                if (isGif) {
                                    viewModel.downloadGifStub()
                                } else {
                                    pages.getOrNull(currentIndex)?.let(viewModel::download)
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.viewer_menu_report)) },
                            leadingIcon = { Icon(Icons.Filled.Report, contentDescription = null) },
                            onClick = { menuExpanded = false; viewModel.report() },
                        )
                    }
                }
            }
        }

        // 页码指示（随工具栏一同显隐）
        AnimatedVisibility(
            visible = barsShown,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            if (pages.size > 1 && !isGif) {
                Row(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(top = 52.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = Spacing.md, vertical = Spacing.xsPlus),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${currentIndex + 1} / ${pages.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
        }

        // 底部操作条：收藏 / 下载 / 壁纸 / 原图（随工具栏一同显隐）
        AnimatedVisibility(
            visible = barsShown,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ViewerActionBar(
                modifier = Modifier,
                isBookmarked = isBookmarked,
                isGif = isGif,
                isOriginal = isOriginal,
                onBookmark = viewModel::toggleBookmark,
                onDownload = {
                    if (isGif) {
                        viewModel.downloadGifStub()
                    } else {
                        pages.getOrNull(currentIndex)?.let(viewModel::download)
                    }
                },
                onWallpaper = {
                    if (!isGif) pages.getOrNull(currentIndex)?.let(viewModel::wallpaper)
                },
                onOriginal = viewModel::toggleOriginal,
            )
        }

        NotificationHost(
            state = notificationHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = Spacing.xl, end = Spacing.xl, bottom = 100.dp),
        )
    }
}

// ── 无缝竖向滚动（webtoon 连续堆叠） ───────────────────────────────────────────

/** 无缝模式宽高缺失时兜底 3:4 竖图比例（loadRealSizes 补齐前/失败时） */
private const val FALLBACK_ASPECT_RATIO = 0.75f

/**
 * 无缝竖向模式：每 P 按真实宽高比撑满屏宽连续堆叠（无间距），
 * 单指上下连续滚动；缩放时由 [LazyColumn.userScrollEnabled] 锁定滚动（与翻页模式一致）。
 */
@Composable
private fun SeamlessViewer(
    pages: List<IllustPageInfo>,
    state: LazyListState,
    userScrollEnabled: Boolean,
    content: @Composable (Int) -> Unit,
) {
    LazyColumn(
        state = state,
        userScrollEnabled = userScrollEnabled,
        modifier = Modifier.fillMaxSize(),
        // 单图且图高不足一屏时垂直居中；多图/超一屏时从顶部排布可滚动
        verticalArrangement = if (pages.size == 1) Arrangement.Center else Arrangement.Top,
    ) {
        itemsIndexed(pages) { index, page ->
            val ratio = if (page.width > 0 && page.height > 0) {
                page.width.toFloat() / page.height.toFloat()
            } else {
                FALLBACK_ASPECT_RATIO
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio),
            ) {
                content(index)
            }
        }
    }
}

// ── 底部圆形操作条 ───────────────────────────────────────────────────────────

@Composable
private fun ViewerActionBar(
    modifier: Modifier = Modifier,
    isBookmarked: Boolean,
    isGif: Boolean,
    isOriginal: Boolean,
    onBookmark: () -> Unit,
    onDownload: () -> Unit,
    onWallpaper: () -> Unit,
    onOriginal: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                ),
            )
            .navigationBarsPadding()
            .padding(vertical = Spacing.lg),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ViewerActionButton(
            icon = if (isBookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(if (isBookmarked) R.string.viewer_cd_unbookmark else R.string.viewer_cd_bookmark),
            tint = if (isBookmarked) FavoriteRed else Color.White,
            onClick = onBookmark,
        )
        ViewerActionButton(
            icon = Icons.Filled.Download,
            contentDescription = stringResource(R.string.viewer_cd_download_original),
            onClick = onDownload,
        )
        ViewerActionButton(
            icon = Icons.Filled.Wallpaper,
            contentDescription = stringResource(R.string.viewer_cd_set_wallpaper),
            enabled = !isGif,
            onClick = onWallpaper,
        )
        ViewerActionButton(
            icon = Icons.Filled.HighQuality,
            contentDescription = stringResource(R.string.viewer_cd_view_original),
            enabled = !isGif,
            selected = isOriginal,
            onClick = onOriginal,
        )
    }
}

@Composable
private fun ViewerActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    tint: Color = Color.White,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(
                if (selected) Color.White.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.45f),
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(Sizes.s22),
        )
    }
}
