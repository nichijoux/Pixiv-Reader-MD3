package com.pixiv.reader.feature.novel.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pixiv.reader.core.ui.component.AdaptiveContentTitle
import com.pixiv.reader.core.ui.component.NotificationHost
import com.pixiv.reader.core.ui.component.NovelCard
import com.pixiv.reader.core.ui.component.NovelCardData
import com.pixiv.reader.core.ui.component.RankingList
import com.pixiv.reader.core.ui.component.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.toNotificationType
import com.pixiv.reader.feature.novel.R
import com.pixiv.reader.feature.novel.state.NovelRankingViewModel

/**
 * 小说排行榜全屏页：分段 Tab + 左右滑动切换（复用通用 [RankingList]）。
 * 条目使用通用 [NovelCard]（上下两部分布局），封面左上角叠加排名徽标（[NovelCard.rank]）。
 *
 * 每段数据由 ViewModel 内独立 PagedState 承载（RankingList 按段 collect），滑动切回已加载段
 * 不重复请求、无过渡动画。
 *
 * @param onBack 返回
 * @param onOpenNovel 点击卡片打开小说详情
 * @param onOpenCover 点击封面打开全屏大图
 * @param onOpenUser 点击作者行打开用户主页
 * @param onSearchTag 点击标签搜索（排行榜页暂不跳转，由调用方决定）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelRankingRoute(
    onBack: () -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
    onOpenUser: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenSeries: (Long) -> Unit,
    viewModel: NovelRankingViewModel = hiltViewModel(),
) {
    val notificationHostState = rememberNotificationHostState()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.message.collect { msg ->
            notificationHostState.show(context.getString(msg.res, *msg.args.toTypedArray()), type = msg.type.toNotificationType())
        }
    }

    Scaffold(
        snackbarHost = { NotificationHost(notificationHostState) },
        topBar = {
            TopAppBar(
                title = {
                    // 平板限宽居中（与下方 RankingList 内容对齐）
                    AdaptiveContentTitle(stringResource(R.string.novel_ranking_title))
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
        RankingList(
            modes = viewModel.modes,
            onModeSelect = viewModel::onPageSelected,
            stateFor = viewModel::stateFor,
            onRetry = viewModel::retry,
            onLoadMore = viewModel::loadMore,
            modifier = Modifier.padding(padding),
            emptyText = stringResource(R.string.novel_ranking_empty),
            // 加载骨架与小说页一致（仿 NovelCard 竖版卡片），而非默认仿 RankingRow 行布局
            skeleton = { NovelFeedSkeleton(showBannerHeader = false) },
        ) { item, rank ->
            NovelCard(
                novel = NovelCardData(
                    id = item.id,
                    title = item.title.orEmpty(),
                    coverUrl = item.image_urls?.square_medium ?: item.image_urls?.medium,
                    authorId = item.user?.id ?: 0L,
                    authorName = item.user?.name.orEmpty(),
                    authorAvatarUrl = item.user?.profile_image_urls?.best(),
                    publishDate = item.create_date,
                    seriesTitle = item.series?.title,
                    seriesId = item.series?.id,
                    favoriteCount = item.total_bookmarks ?: 0,
                    wordCount = item.text_length ?: 0,
                    tags = item.tags.orEmpty()
                        .take(6)
                        .map { it.translated_name ?: it.name ?: "" }
                        .filter { it.isNotBlank() },
                    isFavorite = item.is_bookmarked == true,
                ),
                rank = rank,
                onClick = { onOpenNovel(item.id) },
                onOpenCover = { (item.image_urls?.square_medium ?: item.image_urls?.medium)?.let(onOpenCover) },
                onOpenAuthor = { item.user?.id?.let(onOpenUser) },
                onToggleFavorite = { fav -> viewModel.toggleNovelFavorite(item.id, fav) },
                onTagClick = onSearchTag,
                onSeriesClick = { item.series?.id?.let(onOpenSeries) },
                // RankingList 列表项之间无间距，用卡片底部留白分隔
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
    }
}
