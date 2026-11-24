package com.pixiv.reader.feature.novel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import androidx.work.await
import com.pixiv.reader.core.comment.state.CommentListViewModel
import com.pixiv.reader.core.comment.state.CommentTarget
import com.pixiv.reader.core.network.download.DownloadQueue
import com.pixiv.reader.core.network.novel.NovelViewModel
import com.pixiv.reader.core.comment.ui.CommentPane
import com.pixiv.reader.core.ui.component.detail.HeroSeedStore
import com.pixiv.reader.core.ui.component.detail.novelCoverKey
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.layout.LocalBottomNavBarStyle
import com.pixiv.reader.core.ui.component.layout.PanePlaceholder
import com.pixiv.reader.core.ui.component.layout.computeDetailActionBarBottomPadding
import com.pixiv.reader.core.ui.component.layout.rememberBottomBarAutoHideController
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.novel.data.NovelExportFormat
import com.pixiv.reader.feature.novel.data.NovelExportQueue

/**
 * 小说详情 pane（Master-Detail 右栏，小说 Tab / 排行页 / 关注页共用）。
 * 复用 [NovelViewModel]：选中项变化时 switchTo 加载，系列目录内分册点击原地替换；
 * 内容强制单栏且不显示悬浮返回（pane 宽度窄，关闭由返回手势 / 外层入口承担）。
 * pane 无外层消息收集链路，故自持 [NotificationHost]；导出复用详情页索引快照与 Worker 装配。
 */
@Composable
fun NovelDetailPane(
    selectedId: Long?,
    placeholder: String,
    onOpenReader: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onSearchTag: (String) -> Unit = {},
    commentVm: CommentListViewModel,
    viewModel: NovelViewModel,
) {
    val currentId = selectedId
    // 幂等：同 id 不重载
    LaunchedEffect(currentId) {
        if (currentId != null) viewModel.switchTo(currentId)
    }
    // hero 种子在 pane 层单次消费后经参数下发：上下游各自 consume 会互相抢（取出即删）
    val heroSeed = remember(currentId) {
        currentId?.let { HeroSeedStore.consume(novelCoverKey(it)) }
    }
    // 兜底清理 pane 层未消费的残留种子，避免左栏反复点卡后进程内无界增长
    DisposableEffect(currentId) {
        onDispose {
            if (currentId != null) HeroSeedStore.clear(novelCoverKey(currentId))
        }
    }
    var showComments by remember { mutableStateOf(false) }
    // 评论 → 详情，需先于外层 pane 关闭的 BackHandler 触发
    BackHandler(enabled = showComments) { showComments = false }
    val novel by viewModel.novel.collectAsStateWithLifecycle()
    val seriesNovels by viewModel.seriesNovels.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    // 开关状态机：单流收集，派生展示态 / 进行中两个只读值
    val bookmarkState by viewModel.bookmarkState.collectAsStateWithLifecycle()
    val isBookmarked = bookmarkState.isOn
    val isBookmarking = bookmarkState.inFlight
    // pane 无私密编辑入口
    val bookmarkPrivacy = if (isBookmarked) BookmarkPrivacy.PUBLIC else BookmarkPrivacy.NONE
    val watchlistState by viewModel.watchlistState.collectAsStateWithLifecycle()
    val isWatchlisted = watchlistState.isOn
    val isWatchlisting = watchlistState.inFlight
    val authorFollowState by viewModel.authorFollowState.collectAsStateWithLifecycle()
    val isAuthorFollowed = authorFollowState.isOn
    val isAuthorFollowing = authorFollowState.inFlight
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    var showDownloadDialog by remember { mutableStateOf(false) }
    val notificationHostState = rememberNotificationHostState()
    UiMessageEffect(viewModel.message, notificationHostState)

    // 每个 pane 自持格式选择产生的导出请求，复用手机详情的索引快照与 Worker 装配
    val context = LocalContext.current.applicationContext
    DisposableEffect(viewModel, context) {
        val request: suspend (Long, Long?, String) -> Unit = { novelId, seriesId, formatName ->
            val format = runCatching { NovelExportFormat.valueOf(formatName) }
                .getOrDefault(NovelExportFormat.TXT)
            val detail = viewModel.novel.value
            val entry = NovelExportQueue.pendingEntry(
                novelId = novelId,
                seriesId = seriesId,
                chapterIds = emptyList(),
                format = format,
                title = if ((seriesId ?: 0L) > 0L) detail?.series?.title ?: detail?.title else detail?.title,
                coverUrl = detail?.image_urls?.medium,
                seriesTitle = detail?.series?.title,
                authorName = detail?.user?.name,
                authorAvatarUrl = detail?.user?.profile_image_urls?.best(),
                wordCount = detail?.text_length ?: 0,
                favoriteCount = detail?.total_bookmarks ?: 0,
                publishDate = detail?.create_date,
            )
            // 先写好 pending 索引再唯一入队，确保 Worker 与状态观察时序一致
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
        viewModel.exportRequest = request
        onDispose {
            // 若过渡中的新 pane 已接管，保留新回调
            if (viewModel.exportRequest === request) viewModel.exportRequest = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // hero 共享边界 scope 由宿主 ListDetailOverlay 的 AnimatedVisibility 下发
        // （经 LocalNavAnimatedVisibilityScope）；无宿主（预览等）时为 null，不挂共享边界
        when {
            currentId == null -> PanePlaceholder(text = placeholder)

            error != null && novel == null -> ErrorBox(
                message = error?.let { stringResource(it.res, *it.args.toTypedArray()) },
                onRetry = viewModel::load,
            )

            // hero 元素全程唯一：banner 常驻组合（数据未到时以 seed 封面占位），数据到达后
            // 同一容器原地转真图，否则骨架 + 占位封面 → 真图 banner 的子树硬切会重播共享过渡（闪现）
            else -> {
                if (showComments) {
                    CommentPane(
                        commentVm = commentVm,
                        onOpenUser = onOpenUser,
                        onBackToDetail = { showComments = false },
                    )
                } else {
                    val navBarStyle = LocalBottomNavBarStyle.current
                    val bottomBarPadding = computeDetailActionBarBottomPadding(navBarStyle)
                    val autoHideController = rememberBottomBarAutoHideController()
                    LaunchedEffect(currentId, navBarStyle) {
                        autoHideController.show()
                    }
                    val navBarInsetBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(autoHideController.connection),
                    ) {
                        NovelDetailContent(
                            detail = novel,
                            seed = heroSeed,
                            novelIdForHero = currentId,
                            seriesNovels = seriesNovels,
                            progress = progress,
                            isAuthorFollowed = isAuthorFollowed,
                            isAuthorFollowing = isAuthorFollowing,
                            exportState = exportState,
                            // pane 无悬浮返回按钮，onBack 不会被触发，占位安全
                            onBack = {},
                            onOpenNovel = viewModel::switchTo,
                            onOpenReader = onOpenReader,
                            onOpenUser = onOpenUser,
                            onOpenSeries = onOpenSeries,
                            onToggleFollowAuthor = viewModel::toggleFollowAuthor,
                            onSearchTag = onSearchTag,
                            modifier = Modifier.fillMaxSize(),
                            forceSingleColumn = true,
                            showBackButton = false,
                            bottomContentPadding = Spacing.lg + bottomBarPadding + navBarInsetBottom,
                        )
                        // 依赖非空详情（系列 id / 评论目标），数据未到达时不渲染
                        val detail = novel
                        if (detail != null) {
                            NovelActionBar(
                                novelId = detail.id,
                                seriesId = detail.series?.id,
                                privacy = bookmarkPrivacy,
                                isBookmarking = isBookmarking,
                                isWatchlisted = isWatchlisted,
                                isWatchlisting = isWatchlisting,
                                downloading = exportState.isRunning,
                                onBookmark = viewModel::toggleBookmark,
                                onWatchlist = viewModel::toggleWatchlist,
                                onDownload = { showDownloadDialog = true },
                                onComments = {
                                    showComments = true
                                    commentVm.switchTo(CommentTarget.NOVEL, currentId)
                                },
                                visible = autoHideController.isVisible,
                                style = navBarStyle,
                                // pane 操作条不自行避让导航栏：底边需与左栏列表底边一致直通屏幕底，
                                // 导航栏避让由沉浸式页面整体方案处理
                                navigationBarInset = false,
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
                        }
                        NovelDetailDownloadSheet(
                            visible = showDownloadDialog && novel != null,
                            seriesId = novel?.series?.id,
                            onExport = { formatName, series ->
                                viewModel.export(formatName, series)
                                // 点击格式即入队，立即关弹窗并用 queued toast 反馈
                                showDownloadDialog = false
                            },
                            onDismiss = { showDownloadDialog = false },
                        )
                    }
                }
            }
        }

        // 导航栏避让需走 contentModifier（放 modifier 会空闲占位，见 NotificationHost KDoc）
        NotificationHost(
            state = notificationHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
            contentModifier = Modifier.navigationBarsPadding(),
        )
    }
}
