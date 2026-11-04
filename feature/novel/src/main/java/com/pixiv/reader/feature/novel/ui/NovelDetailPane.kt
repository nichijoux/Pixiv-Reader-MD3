package com.pixiv.reader.feature.novel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.comment.state.CommentListViewModel
import com.pixiv.reader.core.comment.state.CommentTarget
import com.pixiv.reader.core.network.novel.NovelViewModel
import com.pixiv.reader.core.comment.ui.CommentPane
import com.pixiv.reader.core.ui.component.detail.HeroSeedStore
import com.pixiv.reader.core.ui.component.detail.novelCoverKey
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.layout.PanePlaceholder

/**
 * 小说详情 pane（Master-Detail 右栏，小说 Tab / 小说排行页共用；关注页经 app 组合根注入槽位复用）。
 *
 * 复用 [NovelViewModel]（调用方 `hiltViewModel()` 注入）——选中项变化时 [switchTo] 加载；
 * 系列目录内分册点击原地替换。详情内容复用 [NovelDetailContent]
 * （`forceSingleColumn=true` 单栏，避免 pane 宽度下双栏挤压；`showBackButton=false`
 * 不显示悬浮返回按钮，关闭由返回手势 / 外层入口承担）+ [NovelActionBar] 操作行
 * + 下载格式弹窗（复用详情页 [DownloadSheet]）。
 *
 * pane 无外层消息收集链路，故自持消息通知宿主（[NotificationHost] + [UiMessageEffect]）：
 * 导出 / 收藏 / 追更等 VM toast 以底部居中叠层展示，详情与评论两态均可见。
 *
 * @param selectedId 当前选中小说 id（null = 未选中，显示 [placeholder]）
 * @param placeholder 未选中时的占位提示文案
 * @param onOpenReader 打开阅读器（全屏路由）
 * @param onOpenUser 点击作者打开用户主页（全屏路由）
 * @param onOpenSeries 打开系列页（全屏路由）
 * @param onSearchTag 标签点击跳搜索（宿主接线；默认空实现纯展示）
 * @param commentVm 评论 ViewModel（调用方注入；进入评论区时按当前小说 switchTo）
 * @param viewModel 小说详情 ViewModel（调用方注入）
 * @return 无返回值（UI 渲染）
 */
@Composable
fun NovelDetailPane(
    selectedId: Long?,
    placeholder: String,
    onOpenReader: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    // 标签点击跳搜索（宿主接线；不接线时标签纯展示）
    onSearchTag: (String) -> Unit = {},
    commentVm: CommentListViewModel,
    viewModel: NovelViewModel,
) {
    val currentId = selectedId
    // 选中项变化时加载详情（幂等：同 id 不重载；排行右栏同款模式）
    LaunchedEffect(currentId) {
        if (currentId != null) viewModel.switchTo(currentId)
    }
    // hero 种子在 pane 层消费一次，经参数下发 NovelDetailContent：
    // 上下游各自 consume 会互相抢（取出即删，第二个拿到 null）
    val heroSeed = remember(currentId) {
        currentId?.let { HeroSeedStore.consume(novelCoverKey(it)) }
    }
    // hero 种子兜底清理：pane 层已统一消费种子，此处仅兜底未消费的残留（如 future 分支调整），
    // 切换选中项 / 销毁时清掉，避免左栏反复点卡后进程内无界增长
    DisposableEffect(currentId) {
        onDispose {
            if (currentId != null) HeroSeedStore.clear(novelCoverKey(currentId))
        }
    }
    // 评论区开关：右栏内嵌（详情 ↔ 评论切换，不跳全屏）
    var showComments by remember { mutableStateOf(false) }
    // 返回键导航：评论 → 详情（先于外层 pane 关闭的 BackHandler 触发）
    BackHandler(enabled = showComments) { showComments = false }
    val novel by viewModel.novel.collectAsStateWithLifecycle()
    val seriesNovels by viewModel.seriesNovels.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    // 开关状态机：单流收集，派生展示态/进行中两个只读值供下游使用
    val bookmarkState by viewModel.bookmarkState.collectAsStateWithLifecycle()
    val isBookmarked = bookmarkState.isOn
    val isBookmarking = bookmarkState.inFlight
    // 收藏三态（pane 无私密编辑入口，跟随收藏态取 PUBLIC/NONE）
    val bookmarkPrivacy = if (isBookmarked) BookmarkPrivacy.PUBLIC else BookmarkPrivacy.NONE
    val watchlistState by viewModel.watchlistState.collectAsStateWithLifecycle()
    val isWatchlisted = watchlistState.isOn
    val isWatchlisting = watchlistState.inFlight
    val authorFollowState by viewModel.authorFollowState.collectAsStateWithLifecycle()
    val isAuthorFollowed = authorFollowState.isOn
    val isAuthorFollowing = authorFollowState.inFlight
    val downloading by viewModel.downloading.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    // 下载格式弹窗（复用详情页 DownloadSheet）
    var showDownloadDialog by remember { mutableStateOf(false) }
    // 消息通知宿主：pane 无外层收集链路，自持宿主展示 VM 消息（导出/收藏/追更 toast）
    val notificationHostState = rememberNotificationHostState()
    UiMessageEffect(viewModel.message, notificationHostState)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // hero 共享边界：scope 由宿主 ListDetailOverlay 的 AnimatedVisibility 下发
        // （经 LocalNavAnimatedVisibilityScope），pane 内 banner 与左栏卡片按同 key 匹配 morph；
        // 无宿主（预览 / 其他直接调用场景）时自然退化为 null，不挂共享边界
        when {
            currentId == null -> PanePlaceholder(text = placeholder)

            error != null && novel == null -> ErrorBox(
                message = error?.let { stringResource(it.res, *it.args.toTypedArray()) },
                onRetry = viewModel::load,
            )

            // hero 元素全程唯一：banner 常驻组合（数据未到时以 seed 封面占位），
            // 数据到达后同一容器原地转真图，消除骨架 + 占位封面 → 真图 banner 的硬切闪现
            else -> {
                if (showComments) {
                    // 评论区：右栏内嵌（详情 ↔ 评论切换，不跳全屏）
                    CommentPane(
                        commentVm = commentVm,
                        onOpenUser = onOpenUser,
                        onBackToDetail = { showComments = false },
                    )
                } else {
                    Column(Modifier.fillMaxSize()) {
                        NovelDetailContent(
                            detail = novel,
                            seed = heroSeed,
                            novelIdForHero = currentId,
                            seriesNovels = seriesNovels,
                            progress = progress,
                            isAuthorFollowed = isAuthorFollowed,
                            isAuthorFollowing = isAuthorFollowing,
                            downloading = downloading,
                            downloadProgress = downloadProgress,
                            // pane 无悬浮返回按钮（onBack 不会被触发，占位安全）
                            onBack = {},
                            onOpenNovel = viewModel::switchTo,
                            onOpenReader = onOpenReader,
                            onOpenUser = onOpenUser,
                            onOpenSeries = onOpenSeries,
                            onToggleFollowAuthor = viewModel::toggleFollowAuthor,
                            onSearchTag = onSearchTag,
                            modifier = Modifier.weight(1f),
                            forceSingleColumn = true,
                            showBackButton = false,
                        )
                        // 操作条依赖非空详情（系列 id / 评论区目标），数据未到达时不渲染
                        val detail = novel
                        if (detail != null) {
                            NovelActionBar(
                                novelId = detail.id,
                                seriesId = detail.series?.id,
                                privacy = bookmarkPrivacy,
                                isBookmarking = isBookmarking,
                                isWatchlisted = isWatchlisted,
                                isWatchlisting = isWatchlisting,
                                downloading = downloading,
                                onBookmark = viewModel::toggleBookmark,
                                onWatchlist = viewModel::toggleWatchlist,
                                onDownload = { showDownloadDialog = true },
                                onComments = {
                                    showComments = true
                                    commentVm.switchTo(CommentTarget.NOVEL, currentId)
                                },
                                // pane 操作条不自行避让导航栏（底边与左栏列表底边一致，
                                // 直通屏幕底）；导航栏避让由沉浸式页面整体方案处理
                                navigationBarInset = false,
                            )
                        }
                        // 下载格式选择弹窗（详情页 / pane 共用调用块）
                        NovelDetailDownloadSheet(
                            visible = showDownloadDialog && novel != null,
                            seriesId = novel?.series?.id,
                            onExport = { formatName, series ->
                                viewModel.export(formatName, series)
                                // 点击格式即入队：立即关弹窗，配合 queued toast 给予已点击反馈（与系列页一致）
                                showDownloadDialog = false
                            },
                            onDismiss = { showDownloadDialog = false },
                        )
                    }
                }
            }
        }

        // 底部居中通知叠层：contentModifier 承担导航栏避让（放 modifier 会空闲占位，见 NotificationHost KDoc）
        NotificationHost(
            state = notificationHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
            contentModifier = Modifier.navigationBarsPadding(),
        )
    }
}
