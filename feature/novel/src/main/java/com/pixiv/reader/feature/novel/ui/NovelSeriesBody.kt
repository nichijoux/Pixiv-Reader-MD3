package com.pixiv.reader.feature.novel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ui.component.card.NovelCard
import com.pixiv.reader.core.ui.component.card.toCardData
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.SkeletonBlock
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.feedback.skeletonPulseColor
import com.pixiv.reader.core.ui.component.list.loadMoreFooter
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.novel.R
import com.pixiv.reader.feature.novel.data.NovelExportFormat
import com.pixiv.reader.feature.novel.state.NovelSeriesViewModel

/**
 * 系列详情共享主体（全屏路由 / Master-Detail 右栏 pane 共用）：
 * surface 背景 Box + 内容区（[content] 槽位：全屏页传 Scaffold 壳，pane 传占位/列表）
 * + 下载格式弹窗 + 消息通知宿主（底部居中、避让导航栏）。
 * 弹窗可见性由调用方持有（全屏页 rememberSaveable / pane remember），经 [onShowDownloadDialogChange] 上抛修改。
 *
 * @param viewModel 系列 ViewModel（调用方注入）
 * @param showDownloadDialog 下载弹窗可见性
 * @param onShowDownloadDialogChange 修改下载弹窗可见性（确认导出 / 关闭时置 false）
 * @param content 内容区槽位（处于 [BoxScope]，下载弹窗与通知宿主叠加其上）
 * @return 无返回值（渲染 Composable）
 */
@Composable
internal fun NovelSeriesBody(
    viewModel: NovelSeriesViewModel,
    showDownloadDialog: Boolean,
    onShowDownloadDialogChange: (Boolean) -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val allChapters by viewModel.allChapters.collectAsStateWithLifecycle()
    val notificationHostState = rememberNotificationHostState()
    UiMessageEffect(viewModel.message, notificationHostState)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        content()
        if (showDownloadDialog) {
            DownloadSheet(
                config = DownloadSheetConfig.Series(allChapters),
                onFormat = { format: NovelExportFormat, scope: NovelDownloadScope, chapterIds: List<Long> ->
                    // 仅「选取部分」携带分册 id；单本/整系列传空列表（整系列导出范围由 VM 状态决定）
                    viewModel.export(
                        format,
                        if (scope == NovelDownloadScope.PARTIAL) chapterIds else emptyList(),
                    )
                    onShowDownloadDialogChange(false)
                },
                onDismiss = { onShowDownloadDialogChange(false) },
            )
        }
        NotificationHost(
            state = notificationHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }
}

/**
 * 系列详情内容区：状态收集 + 三态（加载/错误/空）+ LazyColumn
 * （系列信息头 / 分册分区标题 / 分册 NovelCard / 触底加载 / 底部沉浸式收尾）。
 * 全屏路由与 pane 共用；分册卡点系列标题经 [onOpenSeries] 上抛（全屏页回当前系列，pane 经宿主原地切换）。
 *
 * @param viewModel 系列 ViewModel（调用方注入）
 * @param onOpenNovel 打开分册详情
 * @param onOpenCover 打开封面全屏大图
 * @param onOpenUser 打开作者主页
 * @param onSearchTag 标签搜索
 * @param onOpenSeries 打开系列详情（分册点系列标题）
 * @param onDownloadClick 点击信息头下载按钮（先拉全量分册后由本回调打开下载弹窗）
 * @return 无返回值（渲染 Composable）
 */
@Composable
internal fun NovelSeriesList(
    viewModel: NovelSeriesViewModel,
    onOpenNovel: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
    onOpenUser: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onDownloadClick: () -> Unit,
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val firstNovelCover by viewModel.firstNovelCover.collectAsStateWithLifecycle()
    val items by viewModel.paged.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.paged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.paged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.paged.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.paged.error.collectAsStateWithLifecycle()
    // 开关状态机：单流收集，派生展示态/进行中两个只读值供下游使用
    val authorFollowState by viewModel.authorFollowState.collectAsStateWithLifecycle()
    val isAuthorFollowed = authorFollowState.isOn
    val isAuthorFollowing = authorFollowState.inFlight
    val watchlistState by viewModel.watchlistState.collectAsStateWithLifecycle()
    val isWatchlisted = watchlistState.isOn
    val isWatchlisting = watchlistState.inFlight
    val downloading by viewModel.downloading.collectAsStateWithLifecycle()
    // 动效方案在组合期取好：item lambda 不是组合上下文，无法在其中读取 MaterialTheme
    val motionScheme = MaterialTheme.motionScheme

    when {
        isLoading && items.isEmpty() && detail == null -> NovelSeriesSkeleton()
        error != null && items.isEmpty() && detail == null ->
            ErrorBox(message = error.orEmpty(), onRetry = viewModel::load)

        items.isEmpty() && detail == null -> EmptyBox(stringResource(R.string.novel_not_found))
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.smPlus),
        ) {
            if (detail != null) {
                item(key = "header") {
                    SeriesHeader(
                        detail = detail!!,
                        onOpenAuthor = onOpenUser,
                        coverUrl = firstNovelCover,
                        onOpenCover = { firstNovelCover?.let(onOpenCover) },
                        isAuthorFollowed = isAuthorFollowed,
                        isAuthorFollowing = isAuthorFollowing,
                        onToggleFollowAuthor = viewModel::toggleFollowAuthor,
                        isWatchlisted = isWatchlisted,
                        isWatchlisting = isWatchlisting,
                        onToggleWatchlist = viewModel::toggleWatchlist,
                        downloading = downloading,
                        onDownload = {
                            // 打开下载弹窗前先拉取全量分册（供「选取部分」）
                            viewModel.ensureAllChaptersLoaded()
                            onDownloadClick()
                        },
                    )
                }
            }
            item(key = "volumes_section") {
                Text(
                    text = stringResource(
                        R.string.novel_series_volumes_section,
                        items.size
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xsPlus),
                )
            }
            items(items, key = { it.id }) { novel ->
                NovelCard(
                    novel = novel.toCardData(),
                    onClick = { onOpenNovel(novel.id) },
                    onOpenAuthor = { novel.user?.id?.let(onOpenUser) },
                    onToggleFavorite = { fav ->
                        viewModel.toggleNovelFavorite(
                            novel.id,
                            fav
                        )
                    },
                    onTagClick = onSearchTag,
                    // 分册卡点系列标题：全屏页回当前系列（popUpTo 语义由导航处理）；pane 经宿主原地切换
                    onSeriesClick = { novel.series?.id?.let(onOpenSeries) },
                    // 进场淡入 + 翻页追加时位置平滑过渡
                    modifier = Modifier
                        .padding(horizontal = Spacing.lg)
                        .animateItem(
                            fadeInSpec = motionScheme.fastEffectsSpec<Float>(),
                            placementSpec = motionScheme.fastSpatialSpec<IntOffset>(),
                        ),
                )
            }
            loadMoreFooter(hasMore = hasMore, isLoadingMore = isLoadingMore, onLoadMore = viewModel::loadMore)
            // 底部沉浸式收尾：导航栏区域 padding（背景已延伸，内容避让手势条）
            item(key = "bottom_space") {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .navigationBarsPadding(),
                )
            }
        }
    }
}

/**
 * 小说系列页加载骨架：仿 [SeriesHeader]（3:4 大封面 + 右侧标题/徽章/作者行 + 简介两行）
 * + 分册列表占位（复用 [NovelCardSkeleton]），呼吸脉冲替代全屏转圈。
 *
 * 结构与真实页一致（`LazyColumn` + 同款间距），切到真实内容时不产生布局跳变。
 *
 * @param modifier 外部传入的 Modifier
 * @return 无返回值（渲染 Composable）
 */
@Composable
internal fun NovelSeriesSkeleton(modifier: Modifier = Modifier) {
    val color = skeletonPulseColor(label = "novelSeriesSkeleton")
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.smPlus),
    ) {
        item(key = "skeleton_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    // 大封面：与 SeriesHeader 同规格（110 × 3:4）
                    SkeletonBlock(
                        modifier = Modifier
                            .width(110.dp)
                            .aspectRatio(3f / 4f)
                            .clip(AppShapes.card),
                        color = color,
                    )
                    Column(modifier = Modifier.padding(start = Spacing.lg).weight(1f)) {
                        // 标题两行
                        SkeletonBlock(
                            modifier = Modifier.fillMaxWidth(0.9f).height(20.dp).clip(AppShapes.tiny),
                            color = color,
                        )
                        SkeletonBlock(
                            modifier = Modifier
                                .padding(top = Spacing.sm)
                                .fillMaxWidth(0.55f)
                                .height(20.dp)
                                .clip(AppShapes.tiny),
                            color = color,
                        )
                        // 徽章行：话数 + 连载态
                        Row(
                            modifier = Modifier.padding(top = Spacing.smPlus),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            repeat(2) {
                                SkeletonBlock(
                                    modifier = Modifier.width(64.dp).height(24.dp).clip(AppShapes.pill),
                                    color = color,
                                )
                            }
                        }
                        // 总字数条
                        SkeletonBlock(
                            modifier = Modifier
                                .padding(top = Spacing.smPlus)
                                .width(96.dp)
                                .height(12.dp)
                                .clip(AppShapes.tiny),
                            color = color,
                        )
                    }
                }
                // 简介两行
                SkeletonBlock(
                    modifier = Modifier
                        .padding(top = Spacing.mdPlus)
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(AppShapes.tiny),
                    color = color,
                )
                SkeletonBlock(
                    modifier = Modifier
                        .padding(top = Spacing.sm)
                        .fillMaxWidth(0.7f)
                        .height(12.dp)
                        .clip(AppShapes.tiny),
                    color = color,
                )
                // 作者行：头像 + 昵称 + 关注胶囊
                Row(
                    modifier = Modifier.padding(top = Spacing.mdPlus),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonBlock(
                        modifier = Modifier.size(Sizes.s44).clip(AppShapes.circle),
                        color = color,
                    )
                    SkeletonBlock(
                        modifier = Modifier
                            .padding(start = Spacing.md)
                            .width(120.dp)
                            .height(14.dp)
                            .clip(AppShapes.tiny),
                        color = color,
                    )
                    Box(Modifier.weight(1f))
                    SkeletonBlock(
                        modifier = Modifier.width(72.dp).height(28.dp).clip(AppShapes.pill),
                        color = color,
                    )
                }
                // 操作行：追更 / 下载两个等宽按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    repeat(2) {
                        SkeletonBlock(
                            modifier = Modifier.weight(1f).height(40.dp).clip(AppShapes.pill),
                            color = color,
                        )
                    }
                }
            }
        }
        // 分册标题条 + 分册卡占位（与真实列表同款卡骨架）
        items(count = 4) {
            NovelCardSkeleton(color = color, modifier = Modifier.padding(horizontal = Spacing.lg))
        }
    }
}
