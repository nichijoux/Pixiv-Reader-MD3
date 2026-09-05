package com.pixiv.reader.feature.novel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ui.component.card.NovelCard
import com.pixiv.reader.core.ui.component.card.toCardData
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.list.LoadMoreItem
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.feature.novel.R
import com.pixiv.reader.feature.novel.data.NovelExportFormat
import com.pixiv.reader.feature.novel.state.NovelSeriesViewModel

/**
 * 小说系列 pane（Master-Detail 右栏；用户页经 app 组合根槽位注入复用，小说 Tab 同模块直用）。
 *
 * 复用 [NovelSeriesViewModel]（调用方 `hiltViewModel()` 注入）——选中项变化时 [switchTo] 加载；
 * 内容与系列全屏页同构（[SeriesHeader] 信息头 + 分册 [NovelCard] 列表 + [DownloadSheet] 下载弹窗），
 * 无 Scaffold/TopAppBar，也无内建关闭按钮：pane 关闭由系统返回 / 外层入口承担。
 * 分册卡上点系列标题 → [onOpenSeries]（宿主接管：维护返回栈后原地切换系列，不跳出右栏）；
 * 分册卡点击 = [onOpenNovel]（宿主分流：pane 内切换到小说详情或全屏路由）。
 * 底部沉浸式跟随宿主方案（pane 底边与左栏列表一致直通屏幕底，列表尾 padding 避让导航栏）；
 * 消息通知自带 [NotificationHost]（下载 / 关注作者操作反馈在 pane 内展示）。
 *
 * @param selectedId 当前选中系列 id（null = 未选中，显示 [placeholder]）
 * @param placeholder 未选中时的占位提示文案
 * @param onOpenNovel 分册点击回调（宿主分流：pane 内切换小说详情 / 全屏路由）
 * @param onOpenSeries 分册卡系列标题点击回调（宿主维护返回栈后原地切换系列）
 * @param onOpenUser 点击作者打开用户主页（全屏路由）
 * @param onOpenCover 打开封面全屏大图（全屏路由）
 * @param onSearchTag 标签搜索（跳发现页）
 * @param viewModel 系列 ViewModel（调用方注入）
 */
@Composable
fun NovelSeriesPane(
    selectedId: Long?,
    placeholder: String,
    onOpenNovel: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
    onSearchTag: (String) -> Unit,
    viewModel: NovelSeriesViewModel,
) {
    val currentId = selectedId
    // 选中项变化时加载详情（幂等：同 id 不重载；小说详情 pane 同款模式）
    LaunchedEffect(currentId) {
        if (currentId != null) viewModel.switchTo(currentId)
    }
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val firstNovelCover by viewModel.firstNovelCover.collectAsStateWithLifecycle()
    val items by viewModel.paged.items.collectAsStateWithLifecycle()
    val isLoading by viewModel.paged.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.paged.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.paged.hasMore.collectAsStateWithLifecycle()
    val error by viewModel.paged.error.collectAsStateWithLifecycle()
    val isAuthorFollowed by viewModel.isAuthorFollowed.collectAsStateWithLifecycle()
    val isAuthorFollowing by viewModel.isAuthorFollowing.collectAsStateWithLifecycle()
    val downloading by viewModel.downloading.collectAsStateWithLifecycle()
    val allChapters by viewModel.allChapters.collectAsStateWithLifecycle()
    // 下载格式弹窗（复用系列页 DownloadSheet）
    var showDownloadDialog by remember { mutableStateOf(false) }

    val notificationHostState = rememberNotificationHostState()
    UiMessageEffect(viewModel.message, notificationHostState)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        when {
            currentId == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            isLoading && items.isEmpty() && detail == null -> LoadingBox()
            error != null && items.isEmpty() && detail == null -> ErrorBox(
                message = error.orEmpty(),
                onRetry = viewModel::load,
            )

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
                            downloading = downloading,
                            onDownload = {
                                // 打开下载弹窗前先拉取全量分册（供「选取部分」）
                                viewModel.ensureAllChaptersLoaded()
                                showDownloadDialog = true
                            },
                        )
                    }
                }
                item(key = "volumes_section") {
                    Text(
                        text = stringResource(R.string.novel_series_volumes_section, items.size),
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
                        onToggleFavorite = { fav -> viewModel.toggleNovelFavorite(novel.id, fav) },
                        onTagClick = onSearchTag,
                        // pane 内点分册卡系列标题：经宿主回调原地切换系列（返回栈由宿主维护）
                        onSeriesClick = { novel.series?.id?.let(onOpenSeries) },
                        modifier = Modifier.padding(horizontal = Spacing.lg),
                    )
                }
                if (hasMore) {
                    item(key = "load_more") {
                        LoadMoreItem(isLoadingMore = isLoadingMore, onLoadMore = viewModel::loadMore)
                    }
                }
                // 底部沉浸式收尾：导航栏区域 padding（pane 背景已延伸，内容避让手势条）
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
        if (showDownloadDialog) {
            DownloadSheet(
                config = DownloadSheetConfig.Series(allChapters),
                onFormat = { format: NovelExportFormat, scope: NovelDownloadScope, chapterIds: List<Long> ->
                    when (scope) {
                        NovelDownloadScope.SINGLE -> viewModel.export(format, emptyList())
                        NovelDownloadScope.SERIES -> viewModel.export(format, emptyList())
                        NovelDownloadScope.PARTIAL -> viewModel.export(format, chapterIds)
                    }
                    showDownloadDialog = false
                },
                onDismiss = { showDownloadDialog = false },
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
