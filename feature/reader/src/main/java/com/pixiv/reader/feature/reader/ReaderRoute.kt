package com.pixiv.reader.feature.reader

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
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

    // P4 增强：目录 / 搜索 / 自定义字体 / 跟随系统
    val toc by viewModel.toc.collectAsStateWithLifecycle()
    val tocLoading by viewModel.tocLoading.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val searchIndex by viewModel.searchIndex.collectAsStateWithLifecycle()
    val customFontPath by viewModel.customFontPath.collectAsStateWithLifecycle()
    val followSystem by viewModel.followSystem.collectAsStateWithLifecycle()

    // 主题跟随系统：开启后按系统深色模式选「夜间/纸张」
    val isDark = isSystemInDarkTheme()
    val effectiveTheme = if (followSystem) (if (isDark) 2 else 1) else readerTheme
    val themeColors = remember(effectiveTheme) { readerThemeColors(effectiveTheme) }
    val snackbarHostState = remember { SnackbarHostState() }
    var settingsOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var pageInfo by remember { mutableStateOf(0 to 0) }
    // 沉浸式阅读：默认只显示正文，点击正文切换工具栏显隐
    var barsVisible by remember { mutableStateOf(false) }
    // 目录 / 搜索跳转目标（全文字符偏移），由各内容组件响应
    var jumpToChar by remember { mutableStateOf<Int?>(null) }
    var tocOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // 自定义字体：从文件路径加载 FontFamily（损坏时回退 null）
    val customFont = remember(customFontPath) {
        if (customFontPath.isNotBlank()) {
            runCatching { FontFamily(Font(File(customFontPath))) }.getOrNull()
        } else {
            null
        }
    }
    val fontPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importCustomFont) }

    LaunchedEffect(Unit) {
        viewModel.message.collect { snackbarHostState.showSnackbar(it) }
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.flushProgress() }
    }

    val readerScope = rememberCoroutineScope()
    // 翻页模式：把 pagerState 提到外层，供左右边缘点击翻页与 HorizontalPager 共用
    val pagerStateRef =
        remember { mutableStateOf<androidx.compose.foundation.pager.PagerState?>(null) }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(themeColors.background)) {
        // 正文容器：始终全屏（工具栏为浮层，不挤压正文）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .pointerInput(pageMode, barsVisible) {
                    // 中间 1/3 点击切换工具栏由内容上方的透明覆盖层处理；
                    // 工具栏显示时：左右边缘点击关闭工具栏（不翻页）；
                    // 隐藏时：翻页模式左右边缘翻页；仿真模式左右边缘由内部处理
                    if (pageMode == 2) return@pointerInput
                    detectTapGestures(onTap = { offset ->
                        if (pageMode != 1) return@detectTapGestures
                        val w = size.width.toFloat()
                        val third = w / 3f
                        val edge = offset.x < third || offset.x > w - third
                        if (barsVisible) {
                            if (edge) barsVisible = false
                            return@detectTapGestures
                        }
                        if (!edge) return@detectTapGestures
                        val ps = pagerStateRef.value ?: return@detectTapGestures
                        if (offset.x < third && ps.currentPage > 0) {
                            readerScope.launch { ps.animateScrollToPage(ps.currentPage - 1) }
                        } else if (offset.x > w - third && ps.currentPage < ps.pageCount - 1) {
                            readerScope.launch { ps.animateScrollToPage(ps.currentPage + 1) }
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
                                val fontFamilyInstance = rememberReaderFontFamily(fontFamily, customFont)
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
                                        jumpToChar = jumpToChar,
                                        onScrollOffset = viewModel::reportScrollOffset,
                                        onPageInfo = { c, t -> pageInfo = c to t },
                                    )
                                } else {
                                    val pages = rememberReaderPages(
                                        document = doc,
                                        fontSizeSp = fontSize,
                                        lineHeightMultiplier = lineHeight,
                                        fontFamilyName = fontFamily,
                                        customFont = customFont,
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
                                            jumpToChar = jumpToChar,
                                            onPageChange = { index ->
                                                pages.getOrNull(index)?.let {
                                                    viewModel.reportPage(
                                                        it.startChar,
                                                        pages.size
                                                    )
                                                }
                                            },
                                            onPageInfo = { c, t -> pageInfo = c to t },
                                            barsVisible = barsVisible,
                                            onCloseBars = { barsVisible = false },
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
                                            jumpToChar = jumpToChar,
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

                                // 中间 1/3 透明覆盖层：点击切换工具栏（不消费拖动，滑动翻页不受影响）。
                                // 放在内容之上，确保在子层手势消费指针事件后仍能收到轻点。
                                // 滑动模式不叠加覆盖层（避免滚动时误触工具栏），工具栏由顶部窄条触发。
                                if (pageMode != 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(maxWidth / 3f)
                                            .align(Alignment.Center)
                                            .pointerInput(Unit) {
                                                detectTapGestures(onTap = { barsVisible = !barsVisible })
                                            },
                                    )
                                } else {
                                    // 滑动模式：顶部窄条点按显示工具栏（不干扰上下滚动）
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(28.dp)
                                            .align(Alignment.TopCenter)
                                            .pointerInput(Unit) {
                                                detectTapGestures(onTap = { barsVisible = true })
                                            },
                                    )
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

        // 顶栏浮层：返回 / 标题 / 更多（浮在正文之上，不挤压正文布局）
        // 沉浸式：Box 实色背景覆盖状态栏（时间栏）区域，内部内容再避让状态栏
        if (barsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(themeColors.topBar),
            ) {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
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
                                    text = { Text(if (isBookmarked) "取消收藏" else "收藏") },
                                    leadingIcon = {
                                        Icon(
                                            if (isBookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = { menuOpen = false; viewModel.toggleBookmark() },
                                )
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
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                )
            }
        }

        // 底栏浮层：目录 / 搜索 / 设置
        // 沉浸式：Box 实色背景覆盖导航栏（小白条）区域，内部内容再避让导航栏
        if (barsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(themeColors.topBar),
            ) {
                Box(modifier = Modifier.navigationBarsPadding()) {
                    ReaderToolBar(
                        themeColors = themeColors,
                        onToc = { tocOpen = true },
                        onSearch = { searchOpen = true },
                        onSettings = { settingsOpen = true },
                    )
                }
            }
        }

        // 消息提示
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
        )
    }

    if (settingsOpen) {
        ReaderSettingsSheet(
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontFamilyKey = fontFamily,
            theme = effectiveTheme,
            pageMode = pageMode,
            brightness = brightness,
            followSystem = followSystem,
            hasCustomFont = customFontPath.isNotBlank(),
            onFontSizeChange = viewModel::onFontSizeChange,
            onLineHeightChange = viewModel::onLineHeightChange,
            onFontFamilyChange = viewModel::onFontFamilyChange,
            onThemeChange = viewModel::onReaderThemeChange,
            onPageModeChange = viewModel::onPageModeChange,
            onBrightnessChange = viewModel::onBrightnessChange,
            onFollowSystemChange = viewModel::onFollowSystemChange,
            onImportFont = { fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream")) },
            onClearFont = viewModel::clearCustomFont,
            onDismiss = { settingsOpen = false },
        )
    }

    // 目录面板
    if (tocOpen) {
        ModalBottomSheet(onDismissRequest = { tocOpen = false }) {
            Text(
                text = "目录",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            when {
                tocLoading -> Text(
                    text = "加载中…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )

                toc.isEmpty() -> Text(
                    text = "没有可展示的目录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(toc, key = { it.novelId }) { item ->
                        val isCurrent = item.novelId == novelId || item.novelId == -1L
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isCurrent) {
                                androidx.compose.ui.text.font.FontWeight.SemiBold
                            } else {
                                androidx.compose.ui.text.font.FontWeight.Normal
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isCurrent) {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    } else {
                                        Color.Transparent
                                    },
                                )
                                .clickable {
                                    if (isCurrent) {
                                        jumpToChar = item.charOffset
                                    } else {
                                        onOpenNovel(item.novelId)
                                    }
                                    tocOpen = false
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }
            }
        }
    }

    // 搜索面板：跳转当前匹配项，支持上一条/下一条
    LaunchedEffect(searchOpen, searchIndex) {
        if (searchOpen && searchIndex >= 0) {
            searchResults.getOrNull(searchIndex)?.let { jumpToChar = it }
        }
    }
    if (searchOpen) {
        ModalBottomSheet(onDismissRequest = { searchOpen = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        viewModel.searchText(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索正文关键词") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (searchIndex >= 0) {
                            "第 ${searchIndex + 1} / ${searchResults.size} 处"
                        } else {
                            "共 ${searchResults.size} 处匹配"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        viewModel.setSearchIndex(searchIndex - 1)
                    }, enabled = searchIndex > 0) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = "上一个")
                    }
                    IconButton(onClick = {
                        viewModel.setSearchIndex(searchIndex + 1)
                    }, enabled = searchIndex in 0 until searchResults.size - 1) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = "下一个")
                    }
                }
                if (searchResults.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                    ) {
                        items(searchResults.take(100)) { offset ->
                            val ctx = searchSnippet(document?.fullText, offset)
                            Text(
                                text = ctx,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        jumpToChar = offset
                                        searchOpen = false
                                    }
                                    .padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 搜索匹配的上下文片段（前后各截取一段）。 */
private fun searchSnippet(fullText: String?, offset: Int): String {
    val text = fullText ?: return ""
    val start = (offset - 12).coerceAtLeast(0)
    val end = (offset + 40).coerceAtMost(text.length)
    val prefix = if (start > 0) "…" else ""
    return prefix + text.substring(start, end).replace('\n', ' ')
}

@Composable
private fun ReaderToolBar(
    themeColors: ReaderThemeColors,
    onToc: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(themeColors.topBar)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        IconButton(onClick = onToc) {
            Icon(
                Icons.AutoMirrored.Filled.List,
                contentDescription = "目录",
                tint = themeColors.text
            )
        }
        IconButton(onClick = onSearch) {
            Icon(
                Icons.Filled.Search,
                contentDescription = "搜索",
                tint = themeColors.text
            )
        }
        IconButton(onClick = onSettings) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "阅读设置",
                tint = themeColors.text
            )
        }
    }
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
    jumpToChar: Int?,
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

    // 目录/搜索跳转
    LaunchedEffect(jumpToChar) {
        val j = jumpToChar ?: return@LaunchedEffect
        if (items.isEmpty()) return@LaunchedEffect
        val index = items.indexOfFirst { it.anchorEnd > j }
            .let { if (it >= 0) it else items.size - 1 }
        listState.scrollToItem(index.coerceAtLeast(0))
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
    jumpToChar: Int?,
    modifier: Modifier = Modifier,
) {
    var restored by remember { mutableStateOf(false) }

    LaunchedEffect(pages, restoreCharOffset) {
        if (restored || pages.isEmpty()) return@LaunchedEffect
        val index = pages.pageIndexForChar(restoreCharOffset)
        pagerState.scrollToPage(index)
        restored = true
    }

    // 目录/搜索跳转
    LaunchedEffect(jumpToChar) {
        val j = jumpToChar ?: return@LaunchedEffect
        if (pages.isEmpty()) return@LaunchedEffect
        pagerState.scrollToPage(pages.pageIndexForChar(j))
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
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    Column(modifier = modifier) {
        page.elements.forEach { el ->
            when (el) {
                is PageElement.TextLine -> if (el.text.isEmpty()) {
                    // 空行（段落间距）按行高占位
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(with(density) { el.heightPx.toDp() }),
                    )
                } else {
                    Text(
                        text = el.text,
                        style = el.style,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is PageElement.Image -> ReaderImageBlock(
                    url = el.url,
                    caption = el.caption,
                    height = with(density) { el.heightPx.toDp() },
                )
            }
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
