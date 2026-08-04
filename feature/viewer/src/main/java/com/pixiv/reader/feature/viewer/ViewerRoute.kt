package com.pixiv.reader.feature.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ui.component.ZoomableImage

/**
 * 全屏插画查看器：多 P 翻页 + 捏合缩放 + 页码 + 底部操作。
 * 平板/横屏：图片自适应居中，底部操作条宽度受限。
 */
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
    var anyZoomed by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.message.collect { snackbarHostState.showSnackbar(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)),
    ) {
        if (isGif) {
            UgoiraPlayer(
                frames = ugoiraFrames,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !anyZoomed,
                modifier = Modifier.fillMaxSize(),
            ) { index ->
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
                    )
                }
            }
        }

        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent),
                    ),
                )
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
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
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多", tint = Color.White)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isBookmarked) "取消收藏" else "收藏") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = null,
                            )
                        },
                        onClick = { menuExpanded = false; viewModel.toggleBookmark() },
                    )
                    DropdownMenuItem(
                        text = { Text("下载原图") },
                        leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            if (isGif) {
                                viewModel.downloadGifStub()
                            } else {
                                pages.getOrNull(pagerState.currentPage)?.let(viewModel::download)
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("举报") },
                        leadingIcon = { Icon(Icons.Filled.Report, contentDescription = null) },
                        onClick = { menuExpanded = false; viewModel.report() },
                    )
                }
            }
        }

        // 页码指示
        if (pages.size > 1 && !isGif) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 52.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${pages.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }

        // 底部操作条：收藏 / 下载 / 壁纸 / 原图
        ViewerActionBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            isBookmarked = isBookmarked,
            isGif = isGif,
            isOriginal = isOriginal,
            onBookmark = viewModel::toggleBookmark,
            onDownload = {
                if (isGif) {
                    viewModel.downloadGifStub()
                } else {
                    pages.getOrNull(pagerState.currentPage)?.let(viewModel::download)
                }
            },
            onWallpaper = {
                if (!isGif) pages.getOrNull(pagerState.currentPage)?.let(viewModel::wallpaper)
            },
            onOriginal = viewModel::toggleOriginal,
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = 100.dp),
        )
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
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ViewerActionButton(
            icon = if (isBookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (isBookmarked) "取消收藏" else "收藏",
            tint = if (isBookmarked) Color(0xFFFF5252) else Color.White,
            onClick = onBookmark,
        )
        ViewerActionButton(
            icon = Icons.Filled.Download,
            contentDescription = "下载原图",
            onClick = onDownload,
        )
        ViewerActionButton(
            icon = Icons.Filled.Wallpaper,
            contentDescription = "设为壁纸",
            enabled = !isGif,
            onClick = onWallpaper,
        )
        ViewerActionButton(
            icon = Icons.Filled.HighQuality,
            contentDescription = "查看原图",
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
            modifier = Modifier.size(22.dp),
        )
    }
}
