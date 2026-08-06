package com.pixiv.reader.feature.reader.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.common.ReaderPageMode
import com.pixiv.reader.core.common.ReaderThemeMode
import com.pixiv.reader.core.novel.NovelDocument
import com.pixiv.reader.core.ui.component.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.ConfirmDialog
import com.pixiv.reader.core.ui.component.EmptyBox
import com.pixiv.reader.core.ui.component.ErrorBox
import com.pixiv.reader.core.ui.component.LoadingBox
import com.pixiv.reader.core.ui.component.NotificationHost
import com.pixiv.reader.core.ui.component.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.toNotificationType
import com.pixiv.reader.feature.reader.R
import com.pixiv.reader.feature.reader.state.ReaderViewModel
import com.pixiv.reader.feature.reader.state.ReaderPage
import com.pixiv.reader.feature.reader.state.rememberReaderFontFamily
import com.pixiv.reader.feature.reader.state.rememberReaderPages
import com.pixiv.reader.feature.reader.state.rememberReaderTextStyle
import com.pixiv.reader.feature.reader.state.readerImageHeight
import java.io.File
import kotlinx.coroutines.launch

/**
 * 小说阅读器（P4 核心）。
 *
 * 支持：
 * - 3 种翻页模式：滑动（[ScrollReaderContent]）/ 翻页（[PagerReaderContent]）/ 仿真（[SimulationPageContent]）
 * - 4 套主题 + 亮度调节
 * - 字号 / 行距 / 字体调节
 * - 字符级进度落库 + 官方 marker 同步
 * - 阅读书签 / 收藏 / 追更
 * - 目录（[ReaderTocSheet]）/ 全文搜索（[ReaderSearchSheet]）/ 设置（[ReaderSettingsSheet]）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderRoute(
    novelId: Long,
    onBack: () -> Unit,
    onOpenNovel: (Long) -> Unit,
    localDocument: NovelDocument? = null,
    localTitle: String? = null,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val novel by viewModel.novel.collectAsStateWithLifecycle()
    val document by viewModel.document.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()

    // 本地文件阅读（TXT/EPUB 解析后直接渲染，跳过网络）
    LaunchedEffect(localDocument) {
        if (localDocument != null) {
            viewModel.useLocalDocument(localDocument, localTitle.orEmpty())
        }
    }

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
    val effectiveTheme = if (followSystem) {
        if (isDark) ReaderThemeMode.NIGHT else ReaderThemeMode.PAPER
    } else {
        readerTheme
    }
    val themeColors = remember(effectiveTheme) { readerThemeColors(effectiveTheme) }
    val notificationHostState = rememberNotificationHostState()
    val context = LocalContext.current
    var settingsOpen by remember { mutableStateOf(false) }
    var pageInfo by remember { mutableStateOf(0 to 0) }
    // 清除自定义字体确认
    var showClearFontConfirm by remember { mutableStateOf(false) }
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
        viewModel.message.collect { msg ->
            notificationHostState.show(
                context.getString(
                    msg.res,
                    *msg.args.toTypedArray()
                ), type = msg.type.toNotificationType()
            )
        }
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.flushProgress() }
    }

    val readerScope = rememberCoroutineScope()
    // 翻页模式：把 pagerState 提到外层，供左右边缘点击翻页与 HorizontalPager 共用
    val pagerStateRef =
        remember { mutableStateOf<androidx.compose.foundation.pager.PagerState?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(themeColors.background)
    ) {
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
                    if (pageMode == ReaderPageMode.SIMULATION) return@pointerInput
                    detectTapGestures(onTap = { offset ->
                        if (pageMode != ReaderPageMode.PAGINATE) return@detectTapGestures
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
                    ErrorBox(message = error?.let {
                        stringResource(
                            it.res,
                            *it.args.toTypedArray()
                        )
                    }.orEmpty(), onRetry = viewModel::load)

                document == null -> EmptyBox(stringResource(R.string.reader_empty_content))
                else -> {
                    val doc = checkNotNull(document)
                    AdaptiveContentBox {
                        BoxWithConstraints {
                            val contentWidth = maxWidth - PAGE_H_PADDING * 2
                            val pageHeight = maxHeight - PAGE_V_PADDING * 2
                            val fontFamilyInstance =
                                rememberReaderFontFamily(fontFamily, customFont)
                            val baseStyle = rememberReaderTextStyle(
                                fontSize,
                                lineHeight,
                                fontFamilyInstance
                            )
                            val imageHeight = readerImageHeight(contentWidth)
                            val restoreOffset = if (progressRestored) charOffset else 0

                            if (pageMode == ReaderPageMode.SCROLL) {
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
                                val pages: List<ReaderPage> = rememberReaderPages(
                                    document = doc,
                                    fontSizeSp = fontSize,
                                    lineHeightMultiplier = lineHeight,
                                    fontFamilyName = fontFamily,
                                    customFont = customFont,
                                    contentWidthDp = contentWidth,
                                    pageHeightDp = pageHeight,
                                )
                                if (pageMode == ReaderPageMode.SIMULATION) {
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
                            if (pageMode != ReaderPageMode.SCROLL) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(maxWidth / 3f)
                                        .align(Alignment.Center)
                                        .pointerInput(Unit) {
                                            detectTapGestures(onTap = {
                                                barsVisible = !barsVisible
                                            })
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
            ReaderTopBar(
                themeColors = themeColors,
                title = novel?.title ?: stringResource(R.string.reader_title_default),
                isOffline = isOffline,
                isBookmarked = isBookmarked,
                isMarked = isMarked,
                isWatchlisted = isWatchlisted,
                canWatch = novel?.series?.id != null,
                onBack = onBack,
                onToggleBookmark = viewModel::toggleBookmark,
                onToggleMark = viewModel::toggleMark,
                onToggleWatchlist = viewModel::toggleWatchlist,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        // 底栏浮层：目录 / 搜索 / 设置
        if (barsVisible) {
            ReaderBottomToolBar(
                themeColors = themeColors,
                onToc = { tocOpen = true },
                onSearch = { searchOpen = true },
                onSettings = { settingsOpen = true },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // 消息提示
        NotificationHost(
            state = notificationHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
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
            onImportFont = {
                fontPicker.launch(
                    arrayOf(
                        "font/ttf",
                        "font/otf",
                        "application/octet-stream"
                    )
                )
            },
            onClearFont = { showClearFontConfirm = true },
            onDismiss = { settingsOpen = false },
        )
    }

    // 清除自定义字体确认（删除字体文件并恢复默认）
    if (showClearFontConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.reader_settings_clear_font_title),
            message = stringResource(R.string.reader_settings_clear_font_message),
            confirmText = stringResource(R.string.reader_settings_clear),
            onConfirm = {
                viewModel.clearCustomFont()
                showClearFontConfirm = false
            },
            onDismiss = { showClearFontConfirm = false },
        )
    }

    // 目录面板
    if (tocOpen) {
        ReaderTocSheet(
            toc = toc,
            tocLoading = tocLoading,
            currentNovelId = novelId,
            onJumpToChar = { jumpToChar = it },
            onOpenNovel = onOpenNovel,
            onDismiss = { tocOpen = false },
        )
    }

    // 搜索面板：跳转当前匹配项，支持上一条/下一条
    LaunchedEffect(searchOpen, searchIndex) {
        if (searchOpen && searchIndex >= 0) {
            searchResults.getOrNull(searchIndex)?.let { jumpToChar = it }
        }
    }
    if (searchOpen) {
        ReaderSearchSheet(
            query = searchQuery,
            onQueryChange = {
                searchQuery = it
                viewModel.searchText(it)
            },
            searchResults = searchResults,
            searchIndex = searchIndex,
            fullText = document?.fullText,
            onPrev = { viewModel.setSearchIndex(searchIndex - 1) },
            onNext = { viewModel.setSearchIndex(searchIndex + 1) },
            onSelect = { offset ->
                jumpToChar = offset
                searchOpen = false
            },
            onDismiss = { searchOpen = false },
        )
    }
}
