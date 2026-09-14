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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
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
    viewModel: NovelViewModel = hiltViewModel(),
) {
    val novel by viewModel.novel.collectAsStateWithLifecycle()
    val seriesNovels by viewModel.seriesNovels.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val isBookmarking by viewModel.isBookmarking.collectAsStateWithLifecycle()
    val isWatchlisted by viewModel.isWatchlisted.collectAsStateWithLifecycle()
    val isWatchlisting by viewModel.isWatchlisting.collectAsStateWithLifecycle()
    val isAuthorFollowed by viewModel.isAuthorFollowed.collectAsStateWithLifecycle()
    val isAuthorFollowing by viewModel.isAuthorFollowing.collectAsStateWithLifecycle()
    val downloading by viewModel.downloading.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    // 收藏编辑器状态（公开/私密 + 标签弹层）
    val editorOpen by viewModel.bookmarkEditor.isOpen.collectAsStateWithLifecycle()
    val editorRestrict by viewModel.bookmarkEditor.restrict.collectAsStateWithLifecycle()
    val editorSavedTags by viewModel.bookmarkEditor.savedTags.collectAsStateWithLifecycle()
    val editorAllTags by viewModel.bookmarkEditor.allTags.collectAsStateWithLifecycle()
    val editorTagsLoading by viewModel.bookmarkEditor.tagsLoading.collectAsStateWithLifecycle()
    val editorSaving by viewModel.bookmarkEditor.saving.collectAsStateWithLifecycle()
    // 收藏三态（已收藏且为私密 → PRIVATE；底部收藏按钮图标与文案随之切换）
    val bookmarkPrivacy = when {
        !isBookmarked -> BookmarkPrivacy.NONE
        editorRestrict == PixivConstants.RESTRICT_PRIVATE -> BookmarkPrivacy.PRIVATE
        else -> BookmarkPrivacy.PUBLIC
    }
    var showDownloadDialog by rememberSaveable { mutableStateOf(false) }

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
                    title = novel?.title,
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
            isLoading && novel == null -> LoadingBox(modifier = Modifier.padding(padding))
            error != null && novel == null -> ErrorBox(
                message = error?.let { stringResource(it.res, *it.args.toTypedArray()) }.orEmpty(),
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding),
            )

            novel == null -> EmptyBox(
                stringResource(R.string.novel_not_found),
                modifier = Modifier.padding(padding),
            )
            else -> {
                val detail = checkNotNull(novel)
                NovelDetailContent(
                    detail = detail,
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
                    modifier = Modifier.padding(padding),
                )
            }
        }
        val dialogNovel = novel
        NovelDetailDownloadSheet(
            visible = showDownloadDialog && dialogNovel != null,
            seriesId = dialogNovel?.series?.id,
            onExport = viewModel::export,
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
 * @param forceSingleColumn 排行右栏复用：强制单列（右栏宽度不足再分目录/内容双栏），默认 false 保持原行为 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun NovelDetailContent(
    detail: Novel,
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
    modifier: Modifier = Modifier,
    forceSingleColumn: Boolean = false,
    // 是否显示悬浮返回按钮：全屏路由页显示；右栏 pane 隐藏（关闭由返回手势 / 外层入口承担）
    showBackButton: Boolean = true,
) {
    val isTablet = LocalConfiguration.current.screenWidthDp >= TABLET_WIDTH_DP
    val seriesId = detail.series?.id
    if (isTablet && seriesNovels.isNotEmpty() && !forceSingleColumn) {
        // 平板双栏：banner 随正文滚动，左目录固定（sticky 等效），滚动互不影响
        Box(modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "banner") {
                    NovelBanner(detail = detail, height = NOVEL_BANNER_TABLET_HEIGHT)
                }
                item(key = "info_actions") {
                    // 右侧正文：避开左目录（264dp + 间距）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = NOVEL_TOC_PANEL_WIDTH + Spacing.lg),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            NovelHeader(
                                detail,
                                onOpenUser = onOpenUser,
                                expandableIntro = false,
                                isAuthorFollowed = isAuthorFollowed,
                                isAuthorFollowing = isAuthorFollowing,
                                onToggleFollowAuthor = onToggleFollowAuthor,
                                // 简介内 pixiv://novels 链接 → 系列内/详情切换
                                onOpenNovel = onOpenNovel,
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
            // 左侧目录浮层：从 banner 底部开始固定（不随正文滚、不影响 banner）
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = NOVEL_BANNER_TABLET_HEIGHT, start = Spacing.lg),
            ) {
                NovelTocList(
                    seriesNovels = seriesNovels,
                    currentId = detail.id,
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
            showBackButton = showBackButton,
            modifier = modifier,
        )
    }
}

/** 手机单列详情：banner 随滚 + 标题信息 + 操作 + 系列目录（限高内部滚动）。 */
@Composable
private fun PhoneNovelDetail(
    detail: Novel,
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
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
) {
    val tocMaxHeight = (LocalConfiguration.current.screenHeightDp * NOVEL_TOC_MAX_HEIGHT_FRACTION).dp
    val seriesId = detail.series?.id

    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "banner") {
                NovelBanner(detail = detail, height = NOVEL_BANNER_HEIGHT)
            }
            item(key = "info_actions") {
                NovelCenteredBox {
                    Column {
                        NovelHeader(
                            detail,
                            onOpenUser = onOpenUser,
                            expandableIntro = true,
                            isAuthorFollowed = isAuthorFollowed,
                            isAuthorFollowing = isAuthorFollowing,
                            onToggleFollowAuthor = onToggleFollowAuthor,
                            // 简介内 pixiv://novels 链接 → 系列内/详情切换
                            onOpenNovel = onOpenNovel,
                        )
                        NovelActions(
                            downloading = downloading,
                            downloadProgress = downloadProgress,
                            onRead = { onOpenReader(detail.id) },
                        )
                    }
                }
            }
            // 系列目录（有系列才渲染）：限高内部滚动，不随分册数量增高
            if (seriesNovels.isNotEmpty()) {
                item(key = "series_toc") {
                    NovelCenteredBox {
                        NovelTocList(
                            seriesNovels = seriesNovels,
                            currentId = detail.id,
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
