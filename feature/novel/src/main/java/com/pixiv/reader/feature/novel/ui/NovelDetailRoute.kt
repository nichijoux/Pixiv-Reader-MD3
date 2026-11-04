package com.pixiv.reader.feature.novel.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.work.WorkManager
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.PixivConstants
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.database.entity.ReadingProgressEntity
import com.pixiv.reader.core.network.novel.NovelViewModel
import com.pixiv.reader.core.ui.component.bookmark.BookmarkEditSheet
import com.pixiv.reader.core.ui.component.detail.HeroSeed
import com.pixiv.reader.core.ui.component.detail.HeroSeedStore
import com.pixiv.reader.core.ui.component.detail.novelCoverKey
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.novel.R
import com.pixiv.reader.feature.novel.data.NovelExportFormat
import com.pixiv.reader.feature.novel.data.NovelExportQueue

/**
 * 小说详情（第六十四轮完全重写，对齐 design/novel-detail-ui.html）：
 * 沉浸式封面 banner（仅作背景、无视差）+ 标题 / 作者 / 发布时间 / 统计 / 标签 / 简介（首行缩进 + 展开全文）+
 * 阅读 / 收藏 / 追更 / 下载 / 评论（竖排卡片按钮）+ 系列目录（手机限高滚动 / 平板左栏固定、滚动互不影响）+ 查看完整系列。
 * 评论区走通用页 `comments/novel/{id}`（core:comment）。
 *
 * hero 元素由 banner 独占并恒组合：数据未到达时 banner 以列表卡片写入的 seed 封面占位，
 * 数据到达后同一容器原地转真图，避免「骨架 + 占位封面 → 真图 banner」子树硬切导致的闪现。
 *
 * @param novelId 小说 id
 * @param onBack 返回回调
 * @param onOpenNovel 打开其它小说详情（系列目录 / 简介链接）
 * @param onOpenReader 打开阅读器
 * @param onOpenUser 打开用户主页
 * @param onOpenSeries 打开完整系列页
 * @param onOpenComments 打开评论页
 * @param onSearchTag 标签点击跳搜索（由外层导航接线；默认空实现纯展示）
 * @param viewModel 小说详情 ViewModel
 * @return 无返回值（渲染 Composable）
 */
@Composable
fun NovelDetailRoute(
    novelId: Long,
    onBack: () -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenReader: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenComments: (Long) -> Unit,
    // 标签点击跳搜索（由外层导航接线；默认空实现纯展示）
    onSearchTag: (String) -> Unit = {},
    viewModel: NovelViewModel = hiltViewModel(),
) {
    val novel by viewModel.novel.collectAsStateWithLifecycle()
    val seriesNovels by viewModel.seriesNovels.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    // 三对开关状态机：单流收集，派生展示态/进行中两个只读值供下游使用
    val bookmarkState by viewModel.bookmarkState.collectAsStateWithLifecycle()
    val isBookmarked = bookmarkState.isOn
    val isBookmarking = bookmarkState.inFlight
    val watchlistState by viewModel.watchlistState.collectAsStateWithLifecycle()
    val isWatchlisted = watchlistState.isOn
    val isWatchlisting = watchlistState.inFlight
    val authorFollowState by viewModel.authorFollowState.collectAsStateWithLifecycle()
    val isAuthorFollowed = authorFollowState.isOn
    val isAuthorFollowing = authorFollowState.inFlight
    val downloading by viewModel.downloading.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    // 收藏编辑器状态（公开/私密 + 标签弹层）
    val editorOpen by viewModel.bookmarkEditor.isOpen.collectAsStateWithLifecycle()
    val editorRestrict by viewModel.bookmarkEditor.restrict.collectAsStateWithLifecycle()
    val editorSavedTags by viewModel.bookmarkEditor.savedTags.collectAsStateWithLifecycle()
    val editorAllTags by viewModel.bookmarkEditor.allTags.collectAsStateWithLifecycle()
    val editorTagsLoading by viewModel.bookmarkEditor.tagsLoading.collectAsStateWithLifecycle()
    val editorSaving by viewModel.isEditorSaving.collectAsStateWithLifecycle()
    // 收藏三态（已收藏且为私密 → PRIVATE；底部收藏按钮图标与文案随之切换）
    val bookmarkPrivacy = when {
        !isBookmarked -> BookmarkPrivacy.NONE
        editorRestrict == PixivConstants.RESTRICT_PRIVATE -> BookmarkPrivacy.PRIVATE
        else -> BookmarkPrivacy.PUBLIC
    }
    var showDownloadDialog by rememberSaveable { mutableStateOf(false) }
    // hero 种子在 Route 层消费一次，经参数下发：上下游各自 consume 会互相抢（取出即删）
    val heroKey = novelCoverKey(novelId)
    val heroSeed = remember(heroKey) { HeroSeedStore.consume(heroKey) }
    // hero 种子兜底清理：详情页销毁时丢弃未消费的种子（详情命中缓存 / 占位分支未组合的情况）
    DisposableEffect(heroKey) {
        onDispose { HeroSeedStore.clear(heroKey) }
    }

    val notificationHostState = rememberNotificationHostState()
    UiMessageEffect(viewModel.message, notificationHostState)
    // 导出触发接线：core VM 只转发事件，入队装配走共享 NovelExportQueue（feature 层实现）在此
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.exportRequest = { novelId, seriesId, formatName ->
            val format = runCatching {
                NovelExportFormat.valueOf(formatName)
            }.getOrDefault(NovelExportFormat.TXT)
            // 入队即建「待同步」索引条目：断网停留待同步（Worker 网络约束），联网自动开始
            val novel = viewModel.novel.value
            viewModel.markDownloadPending(
                NovelExportQueue.pendingEntry(
                    novelId = novelId,
                    seriesId = seriesId,
                    // 单本详情无「选取部分」：chapterIds 恒空（scopeKey 由 helper 依 seriesId 推导）
                    chapterIds = emptyList(),
                    format = format,
                    // 系列导出卡片以系列标题展示（单本用本作标题）
                    title = if ((seriesId ?: 0L) > 0L) novel?.series?.title ?: novel?.title else novel?.title,
                    coverUrl = novel?.image_urls?.medium,
                    seriesTitle = novel?.series?.title,
                    authorName = novel?.user?.name,
                    authorAvatarUrl = novel?.user?.profile_image_urls?.best(),
                    wordCount = novel?.text_length ?: 0,
                    favoriteCount = novel?.total_bookmarks ?: 0,
                    publishDate = novel?.create_date,
                ),
            )
            WorkManager.getInstance(context).enqueue(
                NovelExportQueue.buildRequest(
                    novelId = novelId,
                    seriesId = seriesId,
                    chapterIds = emptyList(),
                    format = format,
                )
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        // 顶部 inset 置 0：沉浸式 banner 贴顶设计保持不变（FloatingBackButton 自带 statusBarsPadding）；
        // 底部由 bottomBar 承担（NovelActionBar 自带 navigationBarsPadding），innerPadding 不重复计
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // 底部操作条：收藏 / 追更 / 下载 / 评论（详情加载完成后显示）
            val actionNovel = novel
            if (actionNovel != null) {
                NovelActionBar(
                    seriesId = actionNovel.series?.id,
                    privacy = bookmarkPrivacy,
                    isBookmarking = isBookmarking,
                    isWatchlisted = isWatchlisted,
                    isWatchlisting = isWatchlisting,
                    downloading = downloading,
                    onBookmark = viewModel::toggleBookmark,
                    onBookmarkLongClick = viewModel.bookmarkEditor::open,
                    onWatchlist = viewModel::toggleWatchlist,
                    onDownload = { showDownloadDialog = true },
                    onComments = { onOpenComments(actionNovel.id) },
                )
            }
        },
        snackbarHost = { NotificationHost(notificationHostState) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        // Scaffold innerPadding 已排除 bottomBar 高度：内容滚不到操作条下方（对齐插画详情页）
        when {
            error != null && novel == null -> ErrorBox(
                message = error?.let { stringResource(it.res, *it.args.toTypedArray()) }.orEmpty(),
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding),
            )

            // 加载结束且无数据、无错误：作品不存在
            novel == null && !isLoading -> EmptyBox(
                stringResource(R.string.novel_not_found),
                modifier = Modifier.padding(padding),
            )

            // 深链进入：无 hero 种子且详情未到 → 完整骨架兜底（banner 无 seed 可占位）
            novel == null && heroSeed == null -> Box(modifier = Modifier.padding(padding)) {
                NovelDetailSkeleton()
            }

            // hero 元素全程唯一：banner 常驻组合（数据未到时以 seed 封面占位），
            // 数据到达后同一容器原地转真图，不再有 skeleton+seed → 真图 banner 的硬切
            else -> NovelDetailContent(
                detail = novel,
                seed = heroSeed,
                novelIdForHero = novelId.takeIf { it > 0L },
                seriesNovels = seriesNovels,
                progress = progress,
                isAuthorFollowed = isAuthorFollowed,
                isAuthorFollowing = isAuthorFollowing,
                downloading = downloading,
                downloadProgress = downloadProgress,
                onBack = onBack,
                onOpenNovel = onOpenNovel,
                onOpenReader = onOpenReader,
                onOpenUser = onOpenUser,
                onOpenSeries = onOpenSeries,
                onToggleFollowAuthor = viewModel::toggleFollowAuthor,
                onSearchTag = onSearchTag,
                modifier = Modifier.padding(padding),
            )
        }
        val dialogNovel = novel
        NovelDetailDownloadSheet(
            visible = showDownloadDialog && dialogNovel != null,
            seriesId = dialogNovel?.series?.id,
            onExport = { formatName, series ->
                viewModel.export(formatName, series)
                // 点击格式即入队：立即关弹窗，配合 queued toast 给予已点击反馈（与系列页一致）
                showDownloadDialog = false
            },
            onDismiss = { showDownloadDialog = false },
        )
    }

    // 收藏编辑弹层：公开/私密 + 标签多选，保存走 viewModel.saveBookmarkEditor
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
}

/** 平板 + 手机双布局分发：平板且有系列走双栏（目录固定），否则单列。
 *
 * banner 恒渲染（hero 元素唯一）：`detail` 为 null 时以 [seed] 占位，数据到达后原地转真图；
 * 信息区在 `detail` 为 null 时以 [NovelInfoSkeleton] 占位。
 *
 * @param detail 小说详情数据；null 表示数据未到达（banner 以 seed 占位、信息区骨架占位）
 * @param seed hero 种子（列表卡片封面快照；数据未到达时 banner 的占位图）
 * @param novelIdForHero hero 共享元素 key 的回退作品 id（路由参数，detail 未到达时定位 key）
 * @param seriesNovels 系列内分册列表（平板双栏左目录 / 手机限高目录）
 * @param progress 阅读进度
 * @param isAuthorFollowed 当前是否已关注作者
 * @param isAuthorFollowing 关注操作进行中
 * @param downloading 下载中
 * @param downloadProgress 下载进度文案（null 无进度）
 * @param onBack 返回回调
 * @param onOpenNovel 打开其它小说详情
 * @param onOpenReader 打开阅读器
 * @param onOpenUser 打开用户主页
 * @param onOpenSeries 打开完整系列页
 * @param onToggleFollowAuthor 关注 / 取关回调
 * @param onSearchTag 标签点击跳搜索
 * @param modifier 外部修饰
 * @param forceSingleColumn 排行右栏复用：强制单列（右栏宽度不足再分目录/内容双栏），默认 false 保持原行为
 * @param showBackButton 是否显示悬浮返回按钮（全屏路由页显示；右栏 pane 隐藏）
 * @return 无返回值（渲染 Composable）
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun NovelDetailContent(
    detail: Novel?,
    seed: HeroSeed? = null,
    novelIdForHero: Long? = null,
    seriesNovels: List<Novel>,
    progress: ReadingProgressEntity?,
    isAuthorFollowed: Boolean,
    isAuthorFollowing: Boolean,
    downloading: Boolean,
    downloadProgress: String?,
    onBack: () -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenReader: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onToggleFollowAuthor: () -> Unit,
    onSearchTag: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    forceSingleColumn: Boolean = false,
    // 是否显示悬浮返回按钮：全屏路由页显示；右栏 pane 隐藏（关闭由返回手势 / 外层入口承担）
    showBackButton: Boolean = true,
) {
    val isTablet = LocalConfiguration.current.screenWidthDp >= TABLET_WIDTH_DP
    val seriesId = detail?.series?.id
    if (isTablet && seriesNovels.isNotEmpty() && !forceSingleColumn) {
        // 平板双栏：banner 随正文滚动，左目录固定（sticky 等效），滚动互不影响
        Box(modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "banner") {
                    NovelBanner(
                        detail = detail,
                        seed = seed,
                        fallbackId = novelIdForHero,
                        height = NOVEL_BANNER_TABLET_HEIGHT,
                    )
                }
                item(key = "info_actions") {
                    // 右侧正文：避开左目录（264dp + 间距）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = NOVEL_TOC_PANEL_WIDTH + Spacing.lg),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            // 数据未到达：信息区骨架占位（banner 已由 seed 承载 hero）
                            if (detail == null) {
                                NovelInfoSkeleton()
                            } else {
                                NovelHeader(
                                    detail,
                                    onOpenUser = onOpenUser,
                                    expandableIntro = false,
                                    isAuthorFollowed = isAuthorFollowed,
                                    isAuthorFollowing = isAuthorFollowing,
                                    onToggleFollowAuthor = onToggleFollowAuthor,
                                    // 简介内 pixiv://novels 链接 → 系列内/详情切换
                                    onOpenNovel = onOpenNovel,
                                    onSearchTag = onSearchTag,
                                )
                                NovelActions(
                                    downloading = downloading,
                                    downloadProgress = downloadProgress,
                                    onRead = { onOpenReader(detail.id) },
                                )
                            }
                        }
                    }
                }
            }
            // 左侧目录浮层：从 banner 底部开始固定（不随正文滚、不影响 banner）
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = NOVEL_BANNER_TABLET_HEIGHT, start = Spacing.lg),
            ) {
                NovelTocList(
                    seriesNovels = seriesNovels,
                    // 占位期（detail 未到）用路由 id 兜底；两者皆无时不定位任何行（0L 不命中）
                    currentId = detail?.id ?: novelIdForHero ?: 0L,
                    seriesId = seriesId,
                    onOpenNovel = onOpenNovel,
                    onOpenSeries = onOpenSeries,
                    // maxHeight=null：平板左栏形态（weight 填充整卡）
                    maxHeight = null,
                    modifier = Modifier
                        .width(NOVEL_TOC_PANEL_WIDTH)
                        .fillMaxHeight(),
                )
            }
            // 平板返回按钮：右上角（pane 场景隐藏）
            if (showBackButton) {
                FloatingBackButton(onBack = onBack, modifier = Modifier.align(Alignment.TopEnd))
            }
        }
    } else {
        PhoneNovelDetail(
            detail = detail,
            seed = seed,
            novelIdForHero = novelIdForHero,
            seriesNovels = seriesNovels,
            progress = progress,
            isAuthorFollowed = isAuthorFollowed,
            isAuthorFollowing = isAuthorFollowing,
            downloading = downloading,
            downloadProgress = downloadProgress,
            onBack = onBack,
            onOpenNovel = onOpenNovel,
            onOpenReader = onOpenReader,
            onOpenUser = onOpenUser,
            onOpenSeries = onOpenSeries,
            onToggleFollowAuthor = onToggleFollowAuthor,
            onSearchTag = onSearchTag,
            showBackButton = showBackButton,
            modifier = modifier,
        )
    }
}

/**
 * 手机单列详情：banner 随滚 + 标题信息 + 操作 + 系列目录（限高内部滚动）。
 *
 * banner 恒渲染（hero 元素唯一）：`detail` 为 null 时以 [seed] 占位；
 * 信息区在 `detail` 为 null 时以 [NovelInfoSkeleton] 占位。
 *
 * @param detail 小说详情数据；null 表示数据未到达（banner 以 seed 占位、信息区骨架占位）
 * @param seed hero 种子（列表卡片封面快照；数据未到达时 banner 的占位图）
 * @param novelIdForHero hero 共享元素 key 的回退作品 id（路由参数，detail 未到达时定位 key）
 * @param seriesNovels 系列内分册列表
 * @param progress 阅读进度
 * @param isAuthorFollowed 当前是否已关注作者
 * @param isAuthorFollowing 关注操作进行中
 * @param downloading 下载中
 * @param downloadProgress 下载进度文案（null 无进度）
 * @param onBack 返回回调
 * @param onOpenNovel 打开其它小说详情
 * @param onOpenReader 打开阅读器
 * @param onOpenUser 打开用户主页
 * @param onOpenSeries 打开完整系列页
 * @param onToggleFollowAuthor 关注 / 取关回调
 * @param onSearchTag 标签点击跳搜索
 * @param modifier 外部修饰
 * @param showBackButton 是否显示悬浮返回按钮
 * @return 无返回值（渲染 Composable）
 */
@Composable
private fun PhoneNovelDetail(
    detail: Novel?,
    seed: HeroSeed?,
    novelIdForHero: Long?,
    seriesNovels: List<Novel>,
    progress: ReadingProgressEntity?,
    isAuthorFollowed: Boolean,
    isAuthorFollowing: Boolean,
    downloading: Boolean,
    downloadProgress: String?,
    onBack: () -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenReader: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onToggleFollowAuthor: () -> Unit,
    onSearchTag: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
) {
    val tocMaxHeight = (LocalConfiguration.current.screenHeightDp * NOVEL_TOC_MAX_HEIGHT_FRACTION).dp
    val seriesId = detail?.series?.id
    // 目录当前章：占位期用路由 id 兜底（两者皆无时不定位任何行）
    val currentNovelId = detail?.id ?: novelIdForHero ?: 0L

    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "banner") {
                NovelBanner(
                    detail = detail,
                    seed = seed,
                    fallbackId = novelIdForHero,
                    height = NOVEL_BANNER_HEIGHT,
                )
            }
            item(key = "info_actions") {
                NovelCenteredBox {
                    Column {
                        // 数据未到达：信息区骨架占位（banner 已由 seed 承载 hero）
                        if (detail == null) {
                            NovelInfoSkeleton()
                        } else {
                            NovelHeader(
                                detail,
                                onOpenUser = onOpenUser,
                                expandableIntro = true,
                                isAuthorFollowed = isAuthorFollowed,
                                isAuthorFollowing = isAuthorFollowing,
                                onToggleFollowAuthor = onToggleFollowAuthor,
                                // 简介内 pixiv://novels 链接 → 系列内/详情切换
                                onOpenNovel = onOpenNovel,
                                onSearchTag = onSearchTag,
                            )
                            NovelActions(
                                downloading = downloading,
                                downloadProgress = downloadProgress,
                                onRead = { onOpenReader(detail.id) },
                            )
                        }
                    }
                }
            }
            // 系列目录（有系列才渲染）：限高内部滚动，不随分册数量增高
            if (seriesNovels.isNotEmpty()) {
                item(key = "series_toc") {
                    NovelCenteredBox {
                        NovelTocList(
                            seriesNovels = seriesNovels,
                            currentId = currentNovelId,
                            seriesId = seriesId,
                            onOpenNovel = onOpenNovel,
                            onOpenSeries = onOpenSeries,
                            // 限高形态：手机端列表内部滚动（不随分册数量增高）
                            maxHeight = tocMaxHeight,
                        )
                    }
                }
            }
        }
        // 手机返回按钮：左上角（pane 场景隐藏）
        if (showBackButton) {
            FloatingBackButton(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
        }
    }
}
