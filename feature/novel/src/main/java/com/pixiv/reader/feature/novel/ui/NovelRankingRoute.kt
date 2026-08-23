package com.pixiv.reader.feature.novel.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ui.component.card.NovelCard
import com.pixiv.reader.core.ui.component.card.toCardData
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.feedback.toNotificationType
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentTitle
import com.pixiv.reader.core.ui.component.list.RankingList
import com.pixiv.reader.feature.novel.R
import com.pixiv.reader.feature.novel.state.NovelLanguageFilter
import com.pixiv.reader.feature.novel.state.NovelRankingViewModel
import com.pixiv.reader.feature.novel.state.labelRes
import com.pixiv.reader.feature.novel.state.matchesLanguageFilter

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
            notificationHostState.show(
                context.getString(msg.res, *msg.args.toTypedArray()),
                type = msg.type.toNotificationType()
            )
        }
    }
    val languageFilter by viewModel.languageFilter.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }

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
                actions = {
                    Box {
                        TextButton(
                            onClick = { menuExpanded = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        ) {
                            Icon(
                                Icons.Filled.Translate,
                                contentDescription = null,
                                modifier = Modifier.width(18.dp),
                            )
                            Text(
                                text = stringResource(languageFilter.labelRes()),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = stringResource(R.string.novel_cd_language_filter),
                                modifier = Modifier.width(18.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            NovelLanguageFilter.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(option.labelRes())) },
                                    leadingIcon = {
                                        if (option == languageFilter) {
                                            Icon(Icons.Filled.Check, contentDescription = null)
                                        } else {
                                            Spacer(Modifier.width(24.dp))
                                        }
                                    },
                                    onClick = {
                                        viewModel.setLanguageFilter(option)
                                        menuExpanded = false
                                    },
                                )
                            }
                        }
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
            filter = { novel -> novel.matchesLanguageFilter(languageFilter) },
            filteredEmptyText = stringResource(R.string.novel_ranking_filter_empty),
            // 加载骨架与小说页一致（仿 NovelCard 竖版卡片），而非默认仿 RankingRow 行布局
            skeleton = { NovelFeedSkeleton(showBannerHeader = false) },
        ) { item, rank ->
            NovelCard(
                novel = item.toCardData(),
                rank = rank,
                onClick = { onOpenNovel(item.id) },
                onOpenCover = {
                    (item.image_urls?.square_medium ?: item.image_urls?.medium)?.let(
                        onOpenCover
                    )
                },
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
