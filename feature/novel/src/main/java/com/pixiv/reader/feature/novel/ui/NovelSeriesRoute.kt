package com.pixiv.reader.feature.novel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.list.LoadMoreItem
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.card.NovelCard
import com.pixiv.reader.core.ui.component.card.NovelCardData
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.card.toCardData
import com.pixiv.reader.feature.novel.R
import com.pixiv.reader.feature.novel.state.NovelSeriesViewModel
import com.pixiv.reader.feature.novel.data.NovelExportFormat
import androidx.compose.ui.platform.LocalContext

/**
 * 小说系列详情页：系列信息头（标题/简介/篇数/连载态/作者行+关注/下载）+ 分册 NovelCard 列表。
 * 底部沉浸式：Scaffold 不消耗系统栏 insets，列表内容背景延伸覆盖导航栏（列表底 padding 避让手势条）。
 *
 * @param onBack 返回
 * @param onOpenNovel 打开分册详情
 * @param onOpenCover 打开封面全屏大图
 * @param onOpenUser 打开作者主页
 * @param onSearchTag 标签搜索
 * @param onOpenSeries 打开系列详情（分册点系列标题回当前系列）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelSeriesRoute(
    onBack: () -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
    onOpenUser: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenSeries: (Long) -> Unit,
    viewModel: NovelSeriesViewModel = hiltViewModel(),
) {
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
    var showDownloadDialog by rememberSaveable { mutableStateOf(false) }

    val notificationHostState = rememberNotificationHostState()
    val context = LocalContext.current
    val message by viewModel.message.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(message) {
        message?.let { msg ->
            notificationHostState.show(context.getString(msg.res, *msg.args.toTypedArray()))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Scaffold(
            // 底部沉浸式：不消耗系统栏 insets，内容背景延伸到导航栏后面
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = detail?.title ?: stringResource(R.string.novel_series_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.novel_cd_back),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) { padding ->
            AdaptiveContentBox(modifier = Modifier.padding(padding)) {
                when {
                    isLoading && items.isEmpty() && detail == null -> LoadingBox()
                    error != null && items.isEmpty() && detail == null ->
                        ErrorBox(message = error.orEmpty(), onRetry = viewModel::load)
                    items.isEmpty() && detail == null -> EmptyBox(stringResource(R.string.novel_not_found))
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                        items(items, key = { it.id }) { novel ->
                            NovelCard(
                                novel = novel.toCardData(),
                                onClick = { onOpenNovel(novel.id) },
                                onOpenCover = { (novel.image_urls?.square_medium ?: novel.image_urls?.medium)?.let(onOpenCover) },
                                onOpenAuthor = { novel.user?.id?.let(onOpenUser) },
                                onToggleFavorite = { fav -> viewModel.toggleNovelFavorite(novel.id, fav) },
                                onTagClick = onSearchTag,
                                // 系列页内分册：点系列标题回到当前系列（同路由，popUpTo 语义由导航处理）
                                onSeriesClick = { novel.series?.id?.let(onOpenSeries) },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        if (hasMore) {
                            item(key = "load_more") {
                                LoadMoreItem(isLoadingMore = isLoadingMore, onLoadMore = viewModel::loadMore)
                            }
                        }
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
