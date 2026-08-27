package com.pixiv.reader.feature.reader.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.common.config.ReaderPageMode
import com.pixiv.reader.core.common.config.ReaderThemeMode
import com.pixiv.reader.core.novel.model.NovelDocument
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.input.ConfirmDialog
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.feedback.toNotificationType
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.reader.R
import com.pixiv.reader.feature.reader.state.ReaderViewModel
import com.pixiv.reader.feature.reader.state.ReaderPage
import com.pixiv.reader.feature.reader.state.rememberReaderElements
import com.pixiv.reader.feature.reader.state.rememberReaderFontFamily
import com.pixiv.reader.feature.reader.state.rememberReaderPages
import com.pixiv.reader.feature.reader.state.rememberReaderTextStyle
import com.pixiv.reader.feature.reader.state.readerImageHeight
import java.io.File
import kotlinx.coroutines.launch

/**
 * 快速轻点检测 modifier：无移动且在长按阈值内抬起才回调 onTap。
 * - 长按（按住 ≥ 长按阈值）：抬起时时间差超阈值 → 不回调
 * - 长按后拖动 / 普通拖动（移动超 slop）：break 不回调，事件不消费 → 穿透下层滚动/卷页
 * - 全程不消费 down/move（requireUnconsumed=false、不调 consume），滚动不被打断
 * - requireUnconsumed=false 收事件：下层 Pager/LazyColumn/卷页 消费的移动也可见，
 *   翻页/滚动手势的松手 up 不会被误判为轻点（这是"滑动/翻页误触工具栏"的根因）
 * pointerInput block 是受限协程（PointerInputScope），可直接调用受限挂起函数。
 * @param onTap 轻点回调（点击位置 + 容器尺寸；无位置/尺寸需求的调用方可忽略参数）
 */
private fun Modifier.quickTap(onTap: (Offset, Size) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var tapPosition = Offset.Zero
            var tapped = false
            while (true) {
                // awaitPointerEvent 默认 Main pass：下层 Pager/LazyColumn/卷页 消费的移动事件仍可见，
                // 翻页/滚动手势的松手 up 不会因消费被跳过 → 配合下方移动判定取消误判点击
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                if (change.changedToUpIgnoreConsumed()) {
                    // 抬起：按住时长 < 长按阈值 且 无移动 → 快速点击
                    if (change.uptimeMillis - down.uptimeMillis <
                        viewConfiguration.longPressTimeoutMillis
                    ) {
                        tapPosition = down.position
                        tapped = true
                    }
                    break
                }
                // 移动超 slop = 拖动（含长按后拖动）→ 取消点击，事件留给下层滚动
                if (change.positionChanged() &&
                    (change.position - down.position).getDistance() >
                    viewConfiguration.touchSlop
                ) {
                    break
                }
            }
            if (tapped) {
                onTap(tapPosition, Size(size.width.toFloat(), size.height.toFloat()))
            }
        }
    }

/**
 * 触控井字九宫格分区（quickTap 之上）：宽高各三等分，
 * 中间格（宽、高各 1/3 的正方形）点击切换工具栏（[onToggleBars]）；
 * 其余 8 格沿中间对称轴分为左右两半——工具栏显示时点击关闭（[onCloseBars]，避免误翻页），
 * 隐藏时左半区触发 [onPrevPage]、右半区触发 [onNextPage]（翻页/仿真模式翻页，滑动模式为空操作）。
 *
 * @param isBarsVisible 工具栏可见性（lambda 读取实时状态，避免 pointerInput 捕获陈旧值）
 */
private fun Modifier.tapZones(
    isBarsVisible: () -> Boolean,
    onToggleBars: () -> Unit,
    onCloseBars: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
): Modifier = quickTap { offset, containerSize ->
    val w = containerSize.width
    val h = containerSize.height
    val x = offset.x
    val y = offset.y
    // 井字九宫格中间格：宽、高各 1/3 的正方形
    val centerCell = x >= w / 3f && x <= 2f * w / 3f && y >= h / 3f && y <= 2f * h / 3f
    if (centerCell) {
        onToggleBars()
        return@quickTap
    }
    // 其余 8 格沿中间对称轴平分：左半区 / 右半区
    if (x < w / 2f) {
        if (isBarsVisible()) onCloseBars() else onPrevPage()
    } else {
        if (isBarsVisible()) onCloseBars() else onNextPage()
    }
}

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
    toEnd: Boolean = false,
    onOpenNovelToEnd: (Long) -> Unit = onOpenNovel,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val novel by viewModel.novel.collectAsStateWithLifecycle()
    val document by viewModel.document.collectAsStateWithLifecycle()
    val displayDocument by viewModel.displayDocument.collectAsStateWithLifecycle()
    val chineseConvert by viewModel.chineseConvert.collectAsStateWithLifecycle()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()

    // 简繁转换区仅应用语言为中文（简体/繁体）时显示
    val systemLanguage = LocalConfiguration.current.locales[0].language
    val showChineseConvert = when (appLanguage) {
        "zh", "zh-TW" -> true
        "en" -> false
        else -> systemLanguage.startsWith("zh")
    }

    // 本地文件阅读（TXT/EPUB 解析后直接渲染，跳过网络）
    LaunchedEffect(localDocument) {
        if (localDocument != null) {
            viewModel.useLocalDocument(localDocument, localTitle.orEmpty())
        }
    }

    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val lineHeight by viewModel.lineHeight.collectAsStateWithLifecycle()
    val fontFamily by viewModel.fontFamily.collectAsStateWithLifecycle()
    val fontWeight by viewModel.fontWeight.collectAsStateWithLifecycle()
    val paragraphIndent by viewModel.paragraphIndent.collectAsStateWithLifecycle()
    val paragraphSpacing by viewModel.paragraphSpacing.collectAsStateWithLifecycle()
    val letterSpacing by viewModel.letterSpacing.collectAsStateWithLifecycle()
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

    // 首页向前翻页：系列且有上一章 → 打开上一章并定位尾页；非系列 / 系列第一章 → 无操作（禁用向前翻页）
    val onPrevChapterRequest: () -> Unit = {
        val tocIndex = toc.indexOfFirst { it.novelId == novelId }
        val prevChapterId = if (tocIndex > 0) toc.getOrNull(tocIndex - 1)?.novelId else null
        if (prevChapterId != null) onOpenNovelToEnd(prevChapterId)
    }

    // 末页向后翻页：系列且有下一章 → 打开下一章（开头）；非系列 / 系列最后一章 → 无操作
    val onNextChapterRequest: () -> Unit = {
        val tocIndex = toc.indexOfFirst { it.novelId == novelId }
        val nextChapterId = if (tocIndex >= 0) toc.getOrNull(tocIndex + 1)?.novelId else null
        if (nextChapterId != null) onOpenNovel(nextChapterId)
    }

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

    // 系列上一章「尾页」进入（reader/{id}?toEnd=true）：正文就绪后定位到文档末尾。
    // 走 jumpToChar 通道（无 restored 门闩）：避免 progressRestored 未就绪时先定位到开头，
    // 之后尾页偏移就绪却因门闩被拦截（表现为"跳到上一章是开头"）。
    LaunchedEffect(toEnd, document?.textLength) {
        val doc = document
        if (toEnd && doc != null) {
            viewModel.seekToEnd()
            jumpToChar = doc.textLength
        }
    }

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

    // 亮度联动系统（真实屏幕亮度，非遮罩）：
    // screenBrightness 为窗口属性——滑块值直接应用（0.05~1.0），拖到最右 1.0 = 跟随系统（不覆盖）；
    // reader 页在 MainActivity 内，离开页面需恢复进入前的窗口亮度
    val window = (LocalContext.current as? Activity)?.window
    val originalScreenBrightness = remember {
        window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }
    LaunchedEffect(brightness) {
        val w = window ?: return@LaunchedEffect
        val lp = w.attributes
        lp.screenBrightness = if (brightness >= 1f) {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        } else {
            brightness.coerceIn(0.05f, 1f)
        }
        w.attributes = lp
    }
    DisposableEffect(Unit) {
        onDispose {
            val w = window ?: return@onDispose
            val lp = w.attributes
            if (lp.screenBrightness != originalScreenBrightness) {
                lp.screenBrightness = originalScreenBrightness
                w.attributes = lp
            }
        }
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
        // 正文容器：始终全屏（工具栏为浮层，不挤压正文）。
        // 底部不做 navigationBarsPadding：页面纸面（含仿真卷页几何）延伸到系统导航栏实现沉浸，
        // 文字避让由下方 pageHeight 减去导航栏高度承担（最后一行不会进导航栏）。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                // 触控井字九宫格：宽高各三等分，仅中间格点击切换工具栏（唤出/关闭）；
                // 其余 8 格沿中间对称轴平分左右两半——工具栏显示时点击关闭（避免误翻页），
                // 隐藏时左半区翻上一页、右半区翻下一页（滑动模式无翻页，左右半区仅关闭工具栏）；
                // 仿真模式：点击由内部处理（同一九宫格分区）。
                .then(
                    when (pageMode) {
                        ReaderPageMode.PAGINATE -> Modifier.tapZones(
                            isBarsVisible = { barsVisible },
                            onToggleBars = { barsVisible = !barsVisible },
                            onCloseBars = { barsVisible = false },
                            onPrevPage = {
                                val ps = pagerStateRef.value
                                if (ps != null && ps.currentPage > 0) {
                                    readerScope.launch { ps.animateScrollToPage(ps.currentPage - 1, animationSpec = tween(220)) }
                                } else if (ps != null) {
                                    // 当前章首页向前翻：系列跳上一章尾页，非系列无操作
                                    onPrevChapterRequest()
                                }
                            },
                            onNextPage = {
                                val ps = pagerStateRef.value
                                if (ps != null && ps.currentPage < ps.pageCount - 1) {
                                    readerScope.launch { ps.animateScrollToPage(ps.currentPage + 1, animationSpec = tween(220)) }
                                } else if (ps != null) {
                                    // 当前章末页向后翻：系列跳下一章开头，非系列无操作
                                    onNextChapterRequest()
                                }
                            },
                        )

                        ReaderPageMode.SCROLL -> Modifier.tapZones(
                            isBarsVisible = { barsVisible },
                            onToggleBars = { barsVisible = !barsVisible },
                            onCloseBars = { barsVisible = false },
                            // 滑动模式无翻页：左右半区仅工具栏显示时关闭，隐藏时无操作
                            onPrevPage = {},
                            onNextPage = {},
                        )

                        ReaderPageMode.SIMULATION -> Modifier // 仿真模式点击由内部处理
                    }
                ),
        ) {
            // Crossfade：加载完成（Loading/Error/空态 → 正文）淡入过渡，
            // 缓存命中的章节秒开时也有内容动画（与慢加载观感一致）。
            // 渲染用 displayDocument（按简繁转换设置转换文本块）；进度/搜索锚点仍在原始 document 上。
            Crossfade(targetState = displayDocument, animationSpec = tween(200)) { doc ->
                when {
                    doc == null && isLoading -> LoadingBox()
                    doc == null && error != null ->
                        ErrorBox(message = error?.let {
                            stringResource(
                                it.res,
                                *it.args.toTypedArray()
                            )
                        }.orEmpty(), onRetry = viewModel::load)

                    doc == null -> EmptyBox(stringResource(R.string.reader_empty_content))
                    else -> {
                        AdaptiveContentBox {
                            BoxWithConstraints {
                                val contentWidth = maxWidth - PAGE_H_PADDING * 2
                                // 页高减去系统导航栏高度：文字排版避开导航栏（纸面仍延伸到屏幕底，沉浸式）
                                val navBarBottom = WindowInsets.navigationBars
                                    .asPaddingValues()
                                    .calculateBottomPadding()
                                val pageHeight =
                                    maxHeight - PAGE_V_PADDING * 2 - navBarBottom - READER_STATUS_BAR_HEIGHT
                                val fontFamilyInstance =
                                    rememberReaderFontFamily(fontFamily, customFont)
                                val baseStyle = rememberReaderTextStyle(
                                    fontSize,
                                    lineHeight,
                                    fontFamilyInstance,
                                    fontWeight,
                                    paragraphIndent,
                                    letterSpacing,
                                    // 阅读器主题文字色（NIGHT/DEEP_BLACK 下正文可读）
                                    textColor = themeColors.text,
                                )
                                val restoreOffset = if (progressRestored) charOffset else 0

                                if (pageMode == ReaderPageMode.SCROLL) {
                                    // 行元素流：与分页模式共用同一排版引擎（行是元素，段落只是排版输入）
                                    val elements = rememberReaderElements(
                                        document = doc,
                                        baseStyle = baseStyle,
                                        paragraphSpacingEm = paragraphSpacing,
                                        contentWidthDp = contentWidth,
                                        imageHeightDp = readerImageHeight(contentWidth),
                                    )
                                    ScrollReaderContent(
                                        elements = elements,
                                        restoreCharOffset = restoreOffset,
                                        jumpToChar = jumpToChar,
                                        onScrollOffset = viewModel::reportScrollOffset,
                                        onPageInfo = { c, t -> pageInfo = c to t },
                                        modifier = Modifier
                                            .navigationBarsPadding()
                                            .padding(bottom = READER_STATUS_BAR_HEIGHT),
                                    )
                                } else {
                                    val pages: List<ReaderPage> = rememberReaderPages(
                                        document = doc,
                                        baseStyle = baseStyle,
                                        paragraphSpacingEm = paragraphSpacing,
                                        contentWidthDp = contentWidth,
                                        pageHeightDp = pageHeight,
                                    )
                                    if (pageMode == ReaderPageMode.SIMULATION) {
                                        // 仿真模式：位置驱动的贝塞尔卷页（legado 移植）
                                        SimulationPageContent(
                                            pages = pages,
                                            pageHeight = pageHeight,
                                            backgroundColor = themeColors.background,
                                            restoreCharOffset = restoreOffset,
                                            jumpToChar = jumpToChar,
                                            onPageChange = { index ->
                                                pages.getOrNull(index)?.let {
                                                    viewModel.reportPage(
                                                        it.startChar
                                                    )
                                                }
                                            },
                                            onPageInfo = { c, t -> pageInfo = c to t },
                                            barsVisible = barsVisible,
                                            onCloseBars = { barsVisible = false },
                                            onToggleBars = { barsVisible = !barsVisible },
                                            onPrevChapterRequest = onPrevChapterRequest,
                                            onNextChapterRequest = onNextChapterRequest,
                                        )
                                    } else {
                                        val pagerState =
                                            rememberPagerState(pageCount = { pages.size })
                                        LaunchedEffect(pagerState) {
                                            pagerStateRef.value = pagerState
                                        }
                                        PagerReaderContent(
                                            pagerState = pagerState,
                                            pages = pages,
                                            pageHeight = pageHeight,
                                            restoreCharOffset = restoreOffset,
                                            jumpToChar = jumpToChar,
                                            onPageChange = { index ->
                                                pages.getOrNull(index)?.let {
                                                    viewModel.reportPage(
                                                        it.startChar
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

        // 顶栏浮层：返回 / 标题 / 更多（浮在正文之上，不挤压正文布局）
        // 沉浸式：Box 实色背景覆盖状态栏（时间栏）区域，内部内容再避让状态栏
        // 进出场动画：从顶部滑入 / 滑出 + 淡入淡出
        AnimatedVisibility(
            visible = barsVisible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(tween(200)),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ReaderTopBar(
                themeColors = themeColors,
                title = novel?.title ?: stringResource(R.string.reader_title_default),
                isOffline = isOffline,
                isBookmarked = isBookmarked,
                isMarked = isMarked,
                isWatchlisted = isWatchlisted,
                canWatch = novel?.series?.id?.let { it > 0L } == true,
                onBack = onBack,
                onToggleBookmark = viewModel::toggleBookmark,
                onToggleMark = viewModel::toggleMark,
                onToggleWatchlist = viewModel::toggleWatchlist,
            )
        }

        // 底部信息条（常驻，工具栏弹出时被其覆盖）：左侧章节标题 + 右侧 第 x/y 页
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(READER_STATUS_BAR_HEIGHT)
                .padding(horizontal = Spacing.lg),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = novel?.title.orEmpty(),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (pageInfo.second > 0) {
                    Text(
                        text = "${pageInfo.first + 1} / ${pageInfo.second}",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.md),
                    )
                }
            }
        }

        // 底栏浮层：目录 / 搜索 / 设置；进出场动画：从底部滑入 / 滑出 + 淡入淡出
        AnimatedVisibility(
            visible = barsVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(200)),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReaderBottomToolBar(
                themeColors = themeColors,
                onToc = { tocOpen = true },
                onSearch = { searchOpen = true },
                onSettings = { settingsOpen = true },
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
            fontWeight = fontWeight,
            paragraphIndent = paragraphIndent,
            paragraphSpacing = paragraphSpacing,
            letterSpacing = letterSpacing,
            theme = effectiveTheme,
            pageMode = pageMode,
            brightness = brightness,
            followSystem = followSystem,
            hasCustomFont = customFontPath.isNotBlank(),
            onFontSizeChange = viewModel::onFontSizeChange,
            onLineHeightChange = viewModel::onLineHeightChange,
            onFontFamilyChange = viewModel::onFontFamilyChange,
            onFontWeightChange = viewModel::onFontWeightChange,
            onParagraphIndentChange = viewModel::onParagraphIndentChange,
            onParagraphSpacingChange = viewModel::onParagraphSpacingChange,
            onLetterSpacingChange = viewModel::onLetterSpacingChange,
            onThemeChange = viewModel::onReaderThemeChange,
            onPageModeChange = viewModel::onPageModeChange,
            onBrightnessChange = viewModel::onBrightnessChange,
            onFollowSystemChange = viewModel::onFollowSystemChange,
            chineseConvert = chineseConvert,
            showChineseConvert = showChineseConvert,
            onChineseConvertChange = viewModel::onChineseConvertChange,
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
