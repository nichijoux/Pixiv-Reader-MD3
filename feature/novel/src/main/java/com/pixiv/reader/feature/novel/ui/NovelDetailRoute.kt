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
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.await
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.PixivConstants
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.database.entity.ReadingProgressEntity
import com.pixiv.reader.core.network.download.DownloadQueue
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
 * 沉浸式 banner + 标题信息 + 操作行 + 系列目录（手机限高滚动 / 平板左栏固定，滚动互不影响）。
 * hero 元素由 banner 独占并恒组合：数据未到以 seed 占位，到达后同一容器原地转真图，否则子树硬切会闪现。
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
    onSearchTag: (String) -> Unit = {},
    viewModel: NovelViewModel = hiltViewModel(),
) {
    val novel by viewModel.novel.collectAsStateWithLifecycle()
    val seriesNovels by viewModel.seriesNovels.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    // 三对开关状态机：单流收集后派生展示态 / 进行中
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
    // 按钮隐私图标只反映已提交生效值，编辑器草稿不参与
    val editorOpen by viewModel.bookmarkEditor.isOpen.collectAsStateWithLifecycle()
    val editorRestrict by viewModel.bookmarkEditor.restrict.collectAsStateWithLifecycle()
    val committedRestrict by viewModel.bookmarkEditor.committedRestrict.collectAsStateWithLifecycle()
    val editorSavedTags by viewModel.bookmarkEditor.savedTags.collectAsStateWithLifecycle()
    val editorAllTags by viewModel.bookmarkEditor.allTags.collectAsStateWithLifecycle()
    val editorTagsLoading by viewModel.bookmarkEditor.tagsLoading.collectAsStateWithLifecycle()
    val editorSaving by viewModel.isEditorSaving.collectAsStateWithLifecycle()
    // 已收藏且已提交私密 → PRIVATE
    val bookmarkPrivacy = when {
        !isBookmarked -> BookmarkPrivacy.NONE
        committedRestrict == PixivConstants.RESTRICT_PRIVATE -> BookmarkPrivacy.PRIVATE
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
    // 导出触发接线：core VM 只转发事件，入队装配（feature 层的 NovelExportQueue）在此
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.exportRequest = { novelId, seriesId, formatName ->
            val format = runCatching {
                NovelExportFormat.valueOf(formatName)
            }.getOrDefault(NovelExportFormat.TXT)
            val entry = viewModel.novel.value.let { novel ->
                NovelExportQueue.pendingEntry(
                    novelId = novelId,
                    seriesId = seriesId,
                    // 单本详情无「选取部分」：chapterIds 恒空，scopeKey 由 helper 依 seriesId 推导
                    chapterIds = emptyList(),
                    format = format,
                    title = if ((seriesId ?: 0L) > 0L) novel?.series?.title ?: novel?.title else novel?.title,
                    coverUrl = novel?.image_urls?.medium,
                    seriesTitle = novel?.series?.title,
                    authorName = novel?.user?.name,
                    authorAvatarUrl = novel?.user?.profile_image_urls?.best(),
                    wordCount = novel?.text_length ?: 0,
                    favoriteCount = novel?.total_bookmarks ?: 0,
                    publishDate = novel?.create_date,
                )
            }
            // 先等 pending 索引落库，再按复合主键唯一入队，防重复导出任务
            viewModel.markDownloadPending(entry)
            val workTag = DownloadQueue.workTag("novel", entry.targetId, entry.format, entry.scopeKey)
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    workTag,
                    ExistingWorkPolicy.KEEP,
                    NovelExportQueue.buildRequest(
                        novelId = novelId,
                        seriesId = seriesId,
                        chapterIds = emptyList(),
                        format = format,
                    ),
                )
                .await()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        // 顶部 inset 置 0 保住 banner 贴顶（FloatingBackButton 自带 statusBarsPadding）；
        // 底部由 bottomBar 自带 navigationBarsPadding 承担，innerPadding 不重复计
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            val actionNovel = novel
            if (actionNovel != null) {
                NovelActionBar(
                    novelId = actionNovel.id,
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
        // innerPadding 已排除 bottomBar 高度：内容滚不到操作条下方
        when {
            error != null && novel == null -> ErrorBox(
                message = error?.let { stringResource(it.res, *it.args.toTypedArray()) }.orEmpty(),
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding),
            )

            novel == null && !isLoading -> EmptyBox(
                stringResource(R.string.novel_not_found),
                modifier = Modifier.padding(padding),
            )

            // 深链进入无 seed 且详情未到：banner 无法占位，用整页骨架兜底
            novel == null && heroSeed == null -> Box(modifier = Modifier.padding(padding)) {
                NovelDetailSkeleton()
            }

            // hero 元素全程唯一：banner 常驻组合（数据未到时以 seed 封面占位），数据到达后同一容器
            // 原地转真图；用 loading/content 互斥子树分别挂同 key 会让 SharedTransition 重播进入动画
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
                // 点击格式即入队，立即关弹窗并用 queued toast 反馈
                showDownloadDialog = false
            },
            onDismiss = { showDownloadDialog = false },
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
}

/**
 * 平板且有系列走双栏（左目录固定），否则单列。
 * banner 恒渲染以保 hero 元素唯一：detail 为 null 时以 [seed] 占位、信息区用 [NovelInfoSkeleton]。
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
    showBackButton: Boolean = true,
) {
    val isTablet = LocalConfiguration.current.screenWidthDp >= TABLET_WIDTH_DP
    val seriesId = detail?.series?.id
    if (isTablet && seriesNovels.isNotEmpty() && !forceSingleColumn) {
        // banner 随正文滚动，左目录浮层固定（sticky 等效）
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
                    // 正文避开左目录宽度
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = NOVEL_TOC_PANEL_WIDTH + Spacing.lg),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            // 信息区骨架占位（hero 已由 banner 的 seed 承载）
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
            // 左侧目录浮层：从 banner 底部开始固定，不随正文滚动
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = NOVEL_BANNER_TABLET_HEIGHT, start = Spacing.lg),
            ) {
                NovelTocList(
                    seriesNovels = seriesNovels,
                    // 占位期用路由 id 兜底，两者皆无时不定位任何行（0L 不命中）
                    currentId = detail?.id ?: novelIdForHero ?: 0L,
                    seriesId = seriesId,
                    onOpenNovel = onOpenNovel,
                    onOpenSeries = onOpenSeries,
                    maxHeight = null,
                    modifier = Modifier
                        .width(NOVEL_TOC_PANEL_WIDTH)
                        .fillMaxHeight(),
                )
            }
            // 返回按钮：右上角（pane 场景隐藏）
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

/** 手机单列：banner 随滚 + 信息 + 操作 + 系列目录（限高内部滚动）。 */
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
    // 目录当前章：占位期用路由 id 兜底，两者皆无时不定位任何行
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
                        // 信息区骨架占位（hero 已由 banner 的 seed 承载）
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
                            maxHeight = tocMaxHeight,
                        )
                    }
                }
            }
        }
        // 返回按钮：左上角（pane 场景隐藏）
        if (showBackButton) {
            FloatingBackButton(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
        }
    }
}
