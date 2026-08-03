package com.pixiv.reader.feature.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import com.pixiv.reader.core.novel.NovelBlock
import com.pixiv.reader.core.novel.NovelDocument
import com.pixiv.reader.core.ui.component.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.EmptyBox
import com.pixiv.reader.core.ui.component.ErrorBox
import com.pixiv.reader.core.ui.component.LoadingBox
import com.pixiv.reader.core.ui.component.PixivImage
import kotlinx.coroutines.launch

private val PAGE_H_PADDING = 24.dp
private val PAGE_V_PADDING = 16.dp

/**
 * 小说阅读器（P4 核心）。
 *
 * 支持：
 * - 3 种翻页模式：滑动 / 翻页 / 仿真
 * - 4 套主题 + 亮度调节
 * - 字号 / 行距 / 字体调节
 * - 字符级进度落库 + 官方 marker 同步
 * - 阅读书签 / 收藏 / 追更
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderRoute(
    novelId: Long,
    onBack: () -> Unit,
    onOpenNovel: (Long) -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val novel by viewModel.novel.collectAsStateWithLifecycle()
    val document by viewModel.document.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val lineHeight by viewModel.lineHeight.collectAsStateWithLifecycle()
    val fontFamily by viewModel.fontFamily.collectAsStateWithLifecycle()
    val readerTheme by viewModel.readerTheme.collectAsStateWithLifecycle()
    val pageMode by viewModel.pageMode.collectAsStateWithLifecycle()
    val brightness by viewModel.brightness.collectAsStateWithLifecycle()

    val charOffset by viewModel.charOffset.collectAsStateWithLifecycle()
    val progressRestored by viewModel.progressRestored.collectAsStateWithLifecycle()
    val percentage by viewModel.percentage.collectAsStateWithLifecycle()
    val isMarked by viewModel.isMarked.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val isWatchlisted by viewModel.isWatchlisted.collectAsStateWithLifecycle()

    val themeColors = remember(readerTheme) { readerThemeColors(readerTheme) }
    val snackbarHostState = remember { SnackbarHostState() }
    var settingsOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var pageInfo by remember { mutableStateOf(0 to 0) }

    LaunchedEffect(Unit) {
        viewModel.message.collect { snackbarHostState.showSnackbar(it) }
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.flushProgress() }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(themeColors.background)) {
        Scaffold(
            containerColor = themeColors.background,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = novel?.title ?: "阅读",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = themeColors.text,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = themeColors.text
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::toggleBookmark) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (isBookmarked) "取消收藏" else "收藏",
                                tint = if (isBookmarked) MaterialTheme.colorScheme.primary else themeColors.text,
                            )
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = "更多",
                                    tint = themeColors.text
                                )
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (isMarked) "移除阅读书签" else "添加阅读书签") },
                                    leadingIcon = {
                                        Icon(
                                            if (isMarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = { menuOpen = false; viewModel.toggleMark() },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (isWatchlisted) "取消追更" else "加入追更") },
                                    leadingIcon = {
                                        Icon(
                                            if (isWatchlisted) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                                            contentDescription = null,
                                        )
                                    },
                                    enabled = novel?.series?.id != null,
                                    onClick = { menuOpen = false; viewModel.toggleWatchlist() },
                                )
                            }
                        }
                        IconButton(onClick = { settingsOpen = true }) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "阅读设置",
                                tint = themeColors.text
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = themeColors.topBar,
                        scrolledContainerColor = themeColors.topBar,
                    ),
                )
            },
            bottomBar = {
                ReaderBottomBar(
                    percentage = percentage,
                    pageMode = pageMode,
                    pageInfo = pageInfo,
                    textLength = document?.textLength ?: 0,
                    themeColors = themeColors,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            modifier = Modifier.fillMaxSize(),
        ) { padding ->
            val scope = rememberCoroutineScope()
            // 翻页/仿真模式：把 pagerState 提到外层，供点击翻页与 HorizontalPager 共用
            val pagerStateRef =
                remember { mutableStateOf<androidx.compose.foundation.pager.PagerState?>(null) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .pointerInput(pageMode) {
                        // 点击分三区：左 1/3 上一页、右 1/3 下一页、中间 1/3 弹设置；
                        // 滑动翻页完全不受影响（仅已完成轻点才触发）
                        // 仿真模式由 BookPageTurnContent 自行处理点击，这里跳过避免冲突
                        if (pageMode == 2) return@pointerInput
                        detectTapGestures(onTap = { offset ->
                            val w = size.width.toFloat()
                            val third = w / 3f
                            val ps = pagerStateRef.value
                            when {
                                pageMode != 0 && ps != null && offset.x < third &&
                                    ps.currentPage > 0 ->
                                    scope.launch { ps.animateScrollToPage(ps.currentPage - 1) }

                                pageMode != 0 && ps != null && offset.x > w - third &&
                                    ps.currentPage < ps.pageCount - 1 ->
                                    scope.launch { ps.animateScrollToPage(ps.currentPage + 1) }

                                offset.x > w * 0.3f && offset.x < w * 0.7f ->
                                    settingsOpen = true
                                // 边缘空白处：不响应（避免误触）
                                else -> {}
                            }
                        })
                    },
            ) {
                when {
                    isLoading && document == null -> LoadingBox()
                    error != null && document == null ->
                        ErrorBox(message = error.orEmpty(), onRetry = viewModel::load)

                    document == null -> EmptyBox("没有正文内容")
                    else -> {
                        val doc = checkNotNull(document)
                        AdaptiveContentBox {
                            BoxWithConstraints {
                                val contentWidth = maxWidth - PAGE_H_PADDING * 2
                                val pageHeight = maxHeight - PAGE_V_PADDING * 2
                                val fontFamilyInstance = rememberReaderFontFamily(fontFamily)
                                val baseStyle = rememberReaderTextStyle(
                                    fontSize,
                                    lineHeight,
                                    fontFamilyInstance
                                )
                                val imageHeight = readerImageHeight(contentWidth)
                                val restoreOffset = if (progressRestored) charOffset else 0

                                if (pageMode == 0) {
                                    ScrollReaderContent(
                                        document = doc,
                                        baseStyle = baseStyle,
                                        imageHeight = imageHeight,
                                        restoreCharOffset = restoreOffset,
                                        onScrollOffset = viewModel::reportScrollOffset,
                                        onPageInfo = { c, t -> pageInfo = c to t },
                                    )
                                } else {
                                    val pages = rememberReaderPages(
                                        document = doc,
                                        fontSizeSp = fontSize,
                                        lineHeightMultiplier = lineHeight,
                                        fontFamilyName = fontFamily,
                                        contentWidthDp = contentWidth,
                                        pageHeightDp = pageHeight,
                                    )
                                    if (pageMode == 2) {
                                        // 仿真模式：位置驱动的贝塞尔卷页（legado 移植）
                                        SimulationPageContent(
                                            pages = pages,
                                            baseStyle = baseStyle,
                                            imageHeight = imageHeight,
                                            backgroundColor = themeColors.background,
                                            restoreCharOffset = restoreOffset,
                                            onPageChange = { index ->
                                                pages.getOrNull(index)?.let {
                                                    viewModel.reportPage(
                                                        it.startChar,
                                                        pages.size
                                                    )
                                                }
                                            },
                                            onPageInfo = { c, t -> pageInfo = c to t },
                                            onOpenSettings = { settingsOpen = true },
                                        )
                                    } else {
                                        val pagerState = rememberPagerState(pageCount = { pages.size })
                                        LaunchedEffect(pagerState) { pagerStateRef.value = pagerState }
                                        PagerReaderContent(
                                            pagerState = pagerState,
                                            pages = pages,
                                            baseStyle = baseStyle,
                                            imageHeight = imageHeight,
                                            restoreCharOffset = restoreOffset,
                                            onPageChange = { index ->
                                                pages.getOrNull(index)?.let {
                                                    viewModel.reportPage(
                                                        it.startChar,
                                                        pages.size
                                                    )
                                                }
                                            },
                                            onPageInfo = { c, t -> pageInfo = c to t },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 亮度遮罩（0.3 ~ 1.0，1.0 为不遮）
        if (brightness < 1f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 1f - brightness)),
            )
        }
    }

    if (settingsOpen) {
        ReaderSettingsSheet(
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontFamilyKey = fontFamily,
            theme = readerTheme,
            pageMode = pageMode,
            brightness = brightness,
            onFontSizeChange = viewModel::onFontSizeChange,
            onLineHeightChange = viewModel::onLineHeightChange,
            onFontFamilyChange = viewModel::onFontFamilyChange,
            onThemeChange = viewModel::onReaderThemeChange,
            onPageModeChange = viewModel::onPageModeChange,
            onBrightnessChange = viewModel::onBrightnessChange,
            onDismiss = { settingsOpen = false },
        )
    }
}

@Composable
private fun ReaderBottomBar(
    percentage: Int,
    pageMode: Int,
    pageInfo: Pair<Int, Int>,
    textLength: Int,
    themeColors: ReaderThemeColors,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(themeColors.topBar)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "$percentage%",
            style = MaterialTheme.typography.labelMedium,
            color = themeColors.secondary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = if (pageMode == 0) "共 ${formatCountForReader(textLength)} 字" else "第 ${pageInfo.first + 1} 页 / 共 ${pageInfo.second} 页",
            style = MaterialTheme.typography.labelMedium,
            color = themeColors.secondary,
        )
    }
}

private fun formatCountForReader(count: Int): String = when {
    count >= 10000 -> String.format("%.1f万", count / 10000f)
    count >= 1000 -> String.format("%.1f千", count / 1000f)
    else -> count.toString()
}

// ── 滑动模式 ───────────────────────────────────────────────────────────────

private data class ScrollItem(
    val key: Long,
    val block: NovelBlock,
    val anchorStart: Int,
    val anchorEnd: Int,
)

private fun buildScrollItems(document: NovelDocument): List<ScrollItem> {
    val result = mutableListOf<ScrollItem>()
    var cursor = 0
    document.blocks.forEachIndexed { index, block ->
        when (block) {
            is NovelBlock.Paragraph, is NovelBlock.Heading, is NovelBlock.Quote -> {
                result.add(ScrollItem(index.toLong(), block, block.startChar, block.endChar))
                cursor = block.endChar
            }

            is NovelBlock.Image, is NovelBlock.Separator -> {
                result.add(ScrollItem(index.toLong(), block, cursor, cursor))
            }
        }
    }
    return result
}

@Composable
private fun ScrollReaderContent(
    document: NovelDocument,
    baseStyle: TextStyle,
    imageHeight: Dp,
    restoreCharOffset: Int,
    onScrollOffset: (Int) -> Unit,
    onPageInfo: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember(document) { buildScrollItems(document) }
    val listState = rememberLazyListState()
    var restored by remember { mutableStateOf(false) }

    // 首次定位到上次阅读位置
    LaunchedEffect(items, restoreCharOffset) {
        if (restored || items.isEmpty()) return@LaunchedEffect
        val index = items.indexOfFirst { it.anchorEnd > restoreCharOffset }
            .let { if (it >= 0) it else items.size - 1 }
        listState.scrollToItem(index.coerceAtLeast(0))
        restored = true
    }

    // 滚动进度：首可见块的字符偏移 + 块内滚动比例
    LaunchedEffect(listState) {
        snapshotFlow {
            val index = listState.firstVisibleItemIndex
            val offsetPx = listState.firstVisibleItemScrollOffset
            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            Triple(index, offsetPx, item?.size ?: 0)
        }.collect { (index, offsetPx, itemSize) ->
            val item = items.getOrNull(index) ?: return@collect
            val span = (item.anchorEnd - item.anchorStart).coerceAtLeast(0)
            val fraction =
                if (itemSize > 0) (offsetPx.toFloat() / itemSize).coerceIn(0f, 1f) else 0f
            onScrollOffset(item.anchorStart + (span * fraction).toInt())
        }
    }

    LaunchedEffect(Unit) { onPageInfo(0, 1) }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = PAGE_H_PADDING, vertical = PAGE_V_PADDING),
    ) {
        items(count = items.size, key = { items[it].key }) { index ->
            when (val block = items[index].block) {
                is NovelBlock.Paragraph -> Text(
                    text = block.text,
                    style = baseStyle.copy(textAlign = TextAlign.Justify),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )

                is NovelBlock.Heading -> Text(
                    text = block.text,
                    style = baseStyle.copy(
                        fontSize = baseStyle.fontSize * 1.25f,
                        lineHeight = baseStyle.lineHeight * 1.15f,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 6.dp),
                )

                is NovelBlock.Quote -> Text(
                    text = block.text,
                    style = baseStyle.copy(
                        color = baseStyle.color.copy(alpha = 0.72f),
                        textAlign = TextAlign.Justify
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )

                is NovelBlock.Separator -> Text(
                    text = block.symbol,
                    style = baseStyle.copy(textAlign = TextAlign.Center),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )

                is NovelBlock.Image -> ReaderImageBlock(block.url, block.caption, imageHeight)
            }
        }
    }
}

// ── 翻页 / 仿真模式 ──────────────────────────────────────────────────────────

/** 翻页模式：普通横向滑动翻页（无 3D 特效）。 */
@Composable
private fun PagerReaderContent(
    pagerState: androidx.compose.foundation.pager.PagerState,
    pages: List<ReaderPage>,
    baseStyle: TextStyle,
    imageHeight: Dp,
    restoreCharOffset: Int,
    onPageChange: (Int) -> Unit,
    onPageInfo: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var restored by remember { mutableStateOf(false) }

    LaunchedEffect(pages, restoreCharOffset) {
        if (restored || pages.isEmpty()) return@LaunchedEffect
        val index = pages.pageIndexForChar(restoreCharOffset)
        pagerState.scrollToPage(index)
        restored = true
    }

    LaunchedEffect(pagerState.settledPage, pages.size) {
        val index = pagerState.settledPage
        onPageInfo(index, pages.size)
        onPageChange(index)
    }

    if (pages.isEmpty()) {
        EmptyBox("没有正文内容", modifier = modifier)
        return
    }

    HorizontalPager(state = pagerState, modifier = modifier) { index ->
        RenderPage(
            pages[index],
            baseStyle,
            imageHeight,
            Modifier
                .fillMaxSize()
                .padding(PAGE_H_PADDING, PAGE_V_PADDING)
        )
    }
}

/**
 * 仿真模式：位置驱动的角落卷页（翻纸张）效果。
 *
 * 只有纸页的一个角落被掀起：折痕是「角落 → 手指位置」的垂直平分线，
 * 卷角大小完全跟随手指拖动。被卷起的部分实时绘制"纸背"
 * （当前页内容沿折痕反射 + 灰色遮罩降低文字重叠），折角处露出下一页。
 * 翻下一页卷右下角，翻上一页卷左下角。
 */
@Composable
private fun BookPageTurnContent(
    pages: List<ReaderPage>,
    baseStyle: TextStyle,
    imageHeight: Dp,
    restoreCharOffset: Int,
    onPageChange: (Int) -> Unit,
    onPageInfo: (Int, Int) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var restored by remember { mutableStateOf(false) }
    val currentIndex = remember { mutableIntStateOf(0) }
    // 翻页进度：0=未翻，1=完全翻过去
    val curlProgress = remember { Animatable(0f) }
    // true=正在翻下一页（卷右半），false=翻上一页（卷左半）
    var turningForward by remember { mutableStateOf(true) }

    // 首次定位到上次阅读位置
    LaunchedEffect(pages, restoreCharOffset) {
        if (restored || pages.isEmpty()) return@LaunchedEffect
        val index = pages.pageIndexForChar(restoreCharOffset)
        currentIndex.intValue = index
        restored = true
        onPageInfo(index, pages.size)
        onPageChange(index)
    }

    // 翻页完成后上报当前页
    LaunchedEffect(currentIndex.intValue) {
        if (restored) {
            onPageInfo(currentIndex.intValue, pages.size)
            onPageChange(currentIndex.intValue)
        }
    }

    if (pages.isEmpty()) {
        EmptyBox("没有正文内容", modifier = modifier)
        return
    }

    suspend fun finishTurn() {
        if (turningForward) {
            if (currentIndex.intValue < pages.size - 1) currentIndex.intValue += 1
        } else {
            if (currentIndex.intValue > 0) currentIndex.intValue -= 1
        }
        curlProgress.snapTo(0f)
    }

    suspend fun turnNext() {
        if (currentIndex.intValue >= pages.size - 1) return
        turningForward = true
        curlProgress.animateTo(1f, tween(300))
        finishTurn()
    }

    suspend fun turnPrev() {
        if (currentIndex.intValue <= 0) return
        turningForward = false
        curlProgress.animateTo(1f, tween(300))
        finishTurn()
    }

    BoxWithConstraints(
        modifier = modifier
            .pointerInput(pages.size) {
                detectTapGestures(onTap = { offset ->
                    val third = size.width / 3f
                    when {
                        offset.x < third -> scope.launch { turnPrev() }
                        offset.x > size.width - third -> scope.launch { turnNext() }
                        else -> onOpenSettings()
                    }
                })
            }
            .pointerInput(pages.size) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val w = size.width.toFloat()
                        // 向左拖（delta<0）= 翻下一页；向右拖 = 翻上一页
                        val delta = dragAmount.x
                        if (delta != 0f) {
                            if (delta < 0f) {
                                if (currentIndex.intValue >= pages.size - 1) return@detectDragGestures
                                turningForward = true
                            } else {
                                if (currentIndex.intValue <= 0) return@detectDragGestures
                                turningForward = false
                            }
                            val next = (curlProgress.value + -delta / w).coerceIn(0f, 1f)
                            scope.launch { curlProgress.snapTo(next) }
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            if (curlProgress.value > 0.25f) {
                                curlProgress.animateTo(1f, tween(250))
                                finishTurn()
                            } else {
                                curlProgress.animateTo(0f, tween(200))
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { curlProgress.animateTo(0f, tween(200)) }
                    },
                )
            },
    ) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val progress = curlProgress.value

        // 被卷起的角落：翻下一页卷右下角，翻上一页卷左下角；
        // 手指位置 = 从角落线性移动到目标点（由进度驱动，卷角大小跟随拖动）
        val corner = if (turningForward) Offset(w, h) else Offset(0f, h)
        val target = if (turningForward) Offset(w * 0.42f, h * 0.58f) else Offset(w * 0.58f, h * 0.58f)
        val finger = Offset(
            corner.x + (target.x - corner.x) * progress,
            corner.y + (target.y - corner.y) * progress,
        )

        val current = pages[currentIndex.intValue]
        val reveal = pages.getOrNull(
            if (turningForward) currentIndex.intValue + 1 else currentIndex.intValue - 1
        )

        // 折痕：角落↔手指的垂直平分线
        val vx = finger.x - corner.x
        val vy = finger.y - corner.y
        val len = sqrt(vx * vx + vy * vy)
        val hasCurl = progress > 0.01f && len >= 2f
        val mid = Offset((corner.x + finger.x) / 2f, (corner.y + finger.y) / 2f)
        val nx = if (len >= 2f) -vy / len else 0f
        val ny = if (len >= 2f) vx / len else 1f
        val ints = if (hasCurl) lineRectIntersections(mid, Offset(nx, ny), w, h) else emptyList()
        val p1 = ints.getOrNull(0)
        val p2 = ints.getOrNull(1)
        val angleDeg = atan2(ny, nx) * 180f / PI.toFloat()

        // 底层：被翻出的那一页（完全平放）
        if (reveal != null && progress > 0.01f) {
            Box(Modifier.fillMaxSize()) {
                RenderPage(
                    reveal,
                    baseStyle,
                    imageHeight,
                    Modifier
                        .fillMaxSize()
                        .padding(PAGE_H_PADDING, PAGE_V_PADDING)
                )
            }
        }

        // 当前页静态部分：整页减去卷角三角（露出下一页），卷角处加阴影
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    if (!hasCurl || p1 == null || p2 == null) {
                        this@drawWithContent.drawContent()
                        return@drawWithContent
                    }
                    val flapPath = Path().apply {
                        moveTo(p1.x, p1.y)
                        lineTo(corner.x, corner.y)
                        lineTo(p2.x, p2.y)
                        close()
                    }
                    // 打开区域（下一页上）的阴影，模拟纸被掀起
                    clipPath(flapPath) {
                        drawRect(Color.Black.copy(alpha = 0.10f))
                    }
                    // 静态部分 = 整页 - 卷角三角
                    val fullPath = Path().apply { addRect(Rect(0f, 0f, w, h)) }
                    clipPath(fullPath) {
                        clipPath(flapPath, ClipOp.Difference) {
                            this@drawWithContent.drawContent()
                        }
                    }
                },
        ) {
            RenderPage(
                current,
                baseStyle,
                imageHeight,
                Modifier
                    .fillMaxSize()
                    .padding(PAGE_H_PADDING, PAGE_V_PADDING)
            )
        }

        // 卷起的"纸背"：当前页在卷角三角内的内容，沿折痕反射绘制，叠加灰色遮罩降低文字重叠
        if (hasCurl && p1 != null && p2 != null) {
            val flapPath = Path().apply {
                moveTo(p1.x, p1.y)
                lineTo(corner.x, corner.y)
                lineTo(p2.x, p2.y)
                close()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        withTransform({
                            rotate(degrees = angleDeg, pivot = mid)
                            scale(scaleX = 1f, scaleY = -1f, pivot = mid)
                            rotate(degrees = -angleDeg, pivot = mid)
                        }) {
                            clipPath(flapPath) {
                                this@drawWithContent.drawContent()
                                // 纸背灰化：文字转灰，降低与正面的重叠感
                                drawRect(Color.Gray.copy(alpha = 0.60f))
                            }
                        }
                    },
            ) {
                RenderPage(
                    current,
                    baseStyle,
                    imageHeight,
                    Modifier
                        .fillMaxSize()
                        .padding(PAGE_H_PADDING, PAGE_V_PADDING)
                )
            }
        }
    }
}

/** 直线（过 mid，方向 dir）与矩形边界的交点。 */
private fun lineRectIntersections(mid: Offset, dir: Offset, w: Float, h: Float): List<Offset> {
    val pts = mutableListOf<Offset>()
    if (abs(dir.x) > 1e-6f) {
        var t = (0f - mid.x) / dir.x
        val y0 = mid.y + t * dir.y
        if (y0 >= -0.5f && y0 <= h + 0.5f) pts.add(Offset(0f, y0.coerceIn(0f, h)))
        t = (w - mid.x) / dir.x
        val y1 = mid.y + t * dir.y
        if (y1 >= -0.5f && y1 <= h + 0.5f) pts.add(Offset(w, y1.coerceIn(0f, h)))
    }
    if (abs(dir.y) > 1e-6f) {
        var t = (0f - mid.y) / dir.y
        val x0 = mid.x + t * dir.x
        if (x0 >= -0.5f && x0 <= w + 0.5f) pts.add(Offset(x0.coerceIn(0f, w), 0f))
        t = (h - mid.y) / dir.y
        val x1 = mid.x + t * dir.x
        if (x1 >= -0.5f && x1 <= w + 0.5f) pts.add(Offset(x1.coerceIn(0f, w), h))
    }
    return pts.distinctBy { (it.x * 20).toInt() to (it.y * 20).toInt() }
}

@Composable
private fun RenderPage(
    page: ReaderPage,
    baseStyle: TextStyle,
    imageHeight: Dp,
    modifier: Modifier = Modifier,
) {
    when (page) {
        is ReaderPage.Text -> Text(
            text = page.annotated,
            style = baseStyle,
            modifier = modifier,
        )

        is ReaderPage.Image -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            ReaderImageBlock(page.url, page.caption, imageHeight)
        }
    }
}

@Composable
private fun ReaderImageBlock(url: String, caption: String?, height: Dp) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixivImage(
            url = url,
            contentDescription = caption,
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            contentScale = ContentScale.Fit,
        )
        if (!caption.isNullOrBlank()) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
