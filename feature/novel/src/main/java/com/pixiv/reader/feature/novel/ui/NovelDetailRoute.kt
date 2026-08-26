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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.database.entity.ReadingProgressEntity
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.novel.R
import com.pixiv.reader.feature.novel.data.NovelExportFormat
import com.pixiv.reader.feature.novel.state.NovelViewModel

/**
 * 小说详情（第六十四轮完全重写，对齐 design/novel-detail-ui.html）：
 * 沉浸式封面 banner（仅作背景、无视差）+ 标题 / 作者 / 发布时间 / 统计 / 标签 / 简介（首行缩进 + 展开全文）+
 * 阅读 / 收藏 / 追更 / 下载 / 评论（竖排卡片按钮）+ 系列目录（手机限高滚动 / 平板左栏固定、滚动互不影响）+ 查看完整系列。
 * 评论区走通用页 `comments/novel/{id}`（feature:comments）。
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
    var showDownloadDialog by rememberSaveable { mutableStateOf(false) }

    val notificationHostState = rememberNotificationHostState()
    UiMessageEffect(viewModel.message, notificationHostState)

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
                    isBookmarked = isBookmarked,
                    isBookmarking = isBookmarking,
                    isWatchlisted = isWatchlisted,
                    isWatchlisting = isWatchlisting,
                    downloading = downloading,
                    onBookmark = viewModel::toggleBookmark,
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
        if (showDownloadDialog && dialogNovel != null) {
            DownloadSheet(
                // 真实系列 id 必为正：过滤 pixiv 空对象（Series(id=0)）误判，无系列时整行隐藏
                config = DownloadSheetConfig.Detail(dialogNovel.series?.id?.let { it > 0L } == true),
                onFormat = { format: NovelExportFormat, scope: NovelDownloadScope, _: List<Long> ->
                    viewModel.export(format, scope == NovelDownloadScope.SERIES)
                    showDownloadDialog = false
                },
                onDismiss = { showDownloadDialog = false },
            )
        }
    }
}

/** 平板 + 手机双布局分发：平板且有系列走双栏（目录固定），否则单列。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun NovelDetailContent(
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
) {
    val isTablet = LocalConfiguration.current.screenWidthDp >= TABLET_WIDTH_DP
    val seriesId = detail.series?.id
    if (isTablet && seriesNovels.isNotEmpty()) {
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
                NovelTocPanel(
                    seriesNovels = seriesNovels,
                    currentId = detail.id,
                    seriesId = seriesId,
                    onOpenNovel = onOpenNovel,
                    onOpenSeries = onOpenSeries,
                    modifier = Modifier
                        .width(NOVEL_TOC_PANEL_WIDTH)
                        .fillMaxHeight(),
                )
            }
            // 平板返回按钮：右上角
            FloatingBackButton(onBack = onBack, modifier = Modifier.align(Alignment.TopEnd))
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
                        NovelTocScroll(
                            seriesNovels = seriesNovels,
                            currentId = detail.id,
                            seriesId = seriesId,
                            onOpenNovel = onOpenNovel,
                            onOpenSeries = onOpenSeries,
                            maxHeight = tocMaxHeight,
                        )
                    }
                }
            }
        }
        // 手机返回按钮：左上角
        FloatingBackButton(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
    }
}
