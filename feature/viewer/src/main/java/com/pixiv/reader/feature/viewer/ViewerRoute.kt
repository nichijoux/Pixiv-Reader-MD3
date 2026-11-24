package com.pixiv.reader.feature.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.common.config.ViewerOrientation
import com.pixiv.reader.core.network.download.UgoiraExportFormat
import com.pixiv.reader.core.network.model.IllustPageInfo
import com.pixiv.reader.core.ui.R as CoreUiR
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.bookmark.BookmarkEditSheet
import com.pixiv.reader.core.ui.component.image.UgoiraPlayer
import com.pixiv.reader.core.ui.component.image.ZoomableImage
import com.pixiv.reader.core.ui.component.input.ConfirmDialog
import com.pixiv.reader.core.ui.component.input.ConfirmDialogVariant
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.feedback.toNotificationType
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.ImageScrim
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.ViewerScrim
import com.pixiv.reader.core.ui.theme.extendedColors

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ViewerRoute(
    onBack: () -> Unit,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    BackHandler(onBack = onBack)
    val illust by viewModel.illust.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val isGif by viewModel.isGif.collectAsStateWithLifecycle()
    val ugoiraFrames by viewModel.ugoiraFrames.collectAsStateWithLifecycle()
    val bookmarkState by viewModel.bookmarkState.collectAsStateWithLifecycle()
    val isBookmarked = bookmarkState.isOn
    val isOriginal by viewModel.isOriginal.collectAsStateWithLifecycle()
    val orientation by viewModel.viewerOrientation.collectAsStateWithLifecycle()
    val editorOpen by viewModel.bookmarkEditor.isOpen.collectAsStateWithLifecycle()
    val editorRestrict by viewModel.bookmarkEditor.restrict.collectAsStateWithLifecycle()
    val editorSavedTags by viewModel.bookmarkEditor.savedTags.collectAsStateWithLifecycle()
    val editorAllTags by viewModel.bookmarkEditor.allTags.collectAsStateWithLifecycle()
    val editorTagsLoading by viewModel.bookmarkEditor.tagsLoading.collectAsStateWithLifecycle()
    val editorSaving by viewModel.isEditorSaving.collectAsStateWithLifecycle()

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
    val listState = rememberLazyListState()
    LaunchedEffect(pages.size) {
        if (pages.isNotEmpty()) {
            val target = viewModel.initialPage.coerceIn(0, pages.size - 1)
            if (target > 0) listState.scrollToItem(target)
        }
    }
    var anyZoomed by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showGifExportSheet by remember { mutableStateOf(false) }
    var showWallpaperConfirm by remember { mutableStateOf(false) }
    // 仅单击图片区切换显隐，滑动翻页不影响
    var barsVisible by remember { mutableStateOf(true) }
    val barsShown = barsVisible
    // 无缝竖向取首可见项，其余取 pager 当前页（页码 / 下载 / 壁纸共用）
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
            // UgoiraPlayer 自身不消费点击，需外层包一层才能切工具栏
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
            // 默认 Fit 完整展示（相册标准：黑边只在图片比例 ≠ 屏幕比例时出现），
            // 缩放手势由 telephoto 处理
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
                        onClick = { barsVisible = !barsVisible },
                    )
                }
            }
            when (orientation) {
                ViewerOrientation.SEAMLESS -> SeamlessViewer(
                    pages = pages,
                    state = listState,
                    userScrollEnabled = !anyZoomed,
                    content = pageContent,
                )
                ViewerOrientation.VERTICAL -> VerticalPager(
                    state = pagerState,
                    userScrollEnabled = !anyZoomed,
                    // 预组合相邻页：翻页前邻图已在加载，滑动即显
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize(),
                ) { index -> pageContent(index) }
                ViewerOrientation.HORIZONTAL -> HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !anyZoomed,
                    // 预组合相邻页：翻页前邻图已在加载，滑动即显
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize(),
                ) { index -> pageContent(index) }
            }
        }

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
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.viewer_cd_back),
                        tint = Color.White
                    )
                }
                Text(
                    text = illust?.title.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
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
                        // 全 App 统一浮层：高圆角 + 抬高一层容器色
                        shape = AppShapes.large,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                        // 收藏设置：公开/私密 + 标签（弹层保存）
                        DropdownMenuItem(
                            text = { Text(stringResource(CoreUiR.string.bookmark_edit_title)) },
                            leadingIcon = { Icon(Icons.Filled.Label, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                viewModel.bookmarkEditor.open()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.viewer_menu_download_original)) },
                            leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                if (isGif) {
                                    showGifExportSheet = true
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
                        .background(ImageScrim)
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
                        showGifExportSheet = true
                    } else {
                        pages.getOrNull(currentIndex)?.let(viewModel::download)
                    }
                },
                onWallpaper = { if (!isGif) showWallpaperConfirm = true },
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

    BookmarkEditSheet(
        visible = editorOpen,
        restrict = editorRestrict,
        savedTags = editorSavedTags,
        allTags = editorAllTags,
        tagsLoading = editorTagsLoading,
        saving = editorSaving,
        onDismiss = viewModel.bookmarkEditor::close,
        onRestrictChange = viewModel.bookmarkEditor::setRestrict,
        onToggleTag = viewModel.bookmarkEditor::toggleTag,
        onCreateTag = viewModel.bookmarkEditor::createTag,
        onConfirm = viewModel::saveBookmarkEditor,
    )

    if (showWallpaperConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.viewer_wallpaper_confirm_title),
            message = stringResource(R.string.viewer_wallpaper_confirm_message),
            confirmText = stringResource(R.string.viewer_cd_set_wallpaper),
            onConfirm = {
                showWallpaperConfirm = false
                pages.getOrNull(currentIndex)?.let(viewModel::wallpaper)
            },
            onDismiss = { showWallpaperConfirm = false },
            variant = ConfirmDialogVariant.WARNING,
        )
    }

    if (showGifExportSheet) {
        ModalBottomSheet(onDismissRequest = { showGifExportSheet = false }) {
            GifExportOption(
                icon = Icons.Filled.Videocam,
                title = stringResource(R.string.viewer_gif_export_mp4),
                subtitle = stringResource(R.string.viewer_gif_export_mp4_desc),
            ) {
                showGifExportSheet = false
                viewModel.downloadGif(UgoiraExportFormat.MP4)
            }
            GifExportOption(
                icon = Icons.Filled.FolderZip,
                title = stringResource(R.string.viewer_gif_export_zip),
                subtitle = stringResource(R.string.viewer_gif_export_zip_desc),
            ) {
                showGifExportSheet = false
                viewModel.downloadGif(UgoiraExportFormat.ZIP)
            }
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun GifExportOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.xl, vertical = Spacing.smPlus),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** loadRealSizes 补齐前或失败时的兜底宽高比 3:4 */
private const val FALLBACK_ASPECT_RATIO = 0.75f

/**
 * 无缝竖向：每 P 按真实宽高比连续堆叠，缩放时锁滚动（与翻页模式一致）。
 * 单图特例必须整屏作缩放画布（fillParentMaxSize）：telephoto 的平移被约束在自身边界内，
 * 若按宽高比裁框，放大后平移会被框限制。
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
    ) {
        itemsIndexed(pages) { index, page ->
            if (pages.size == 1) {
                Box(modifier = Modifier.fillParentMaxSize()) {
                    content(index)
                }
            } else {
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
}

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
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                ),
            )
            .navigationBarsPadding()
            .padding(vertical = Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            colors = FloatingToolbarDefaults.standardFloatingToolbarColors().copy(
                // 黑底浏览场景：统一 ImageScrim（70% 黑）+ 白色/遮罩红内容
                toolbarContainerColor = ImageScrim,
                toolbarContentColor = Color.White,
            ),
        ) {
            IconButton(onClick = onBookmark) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(if (isBookmarked) R.string.viewer_cd_unbookmark else R.string.viewer_cd_bookmark),
                    tint = if (isBookmarked) MaterialTheme.extendedColors.favoriteOnScrim else Color.White,
                )
            }
            IconButton(onClick = onDownload) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = stringResource(R.string.viewer_cd_download_original),
                )
            }
            IconButton(onClick = onWallpaper, enabled = !isGif) {
                Icon(
                    imageVector = Icons.Filled.Wallpaper,
                    contentDescription = stringResource(R.string.viewer_cd_set_wallpaper),
                )
            }
            FilledIconToggleButton(
                checked = isOriginal,
                onCheckedChange = { onOriginal() },
                enabled = !isGif,
            ) {
                Icon(
                    imageVector = Icons.Filled.HighQuality,
                    contentDescription = stringResource(R.string.viewer_cd_view_original),
                )
            }
        }
    }
}
