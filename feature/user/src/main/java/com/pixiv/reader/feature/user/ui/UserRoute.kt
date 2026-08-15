package com.pixiv.reader.feature.user.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.ui.component.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.EmptyBox
import com.pixiv.reader.core.ui.component.ErrorBox
import com.pixiv.reader.core.ui.component.NotificationHost
import com.pixiv.reader.core.ui.component.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.toNotificationType
import com.pixiv.reader.feature.user.R
import com.pixiv.reader.feature.user.state.UserSection
import com.pixiv.reader.feature.user.state.UserViewModel
import kotlinx.coroutines.launch

/**
 * 用户主页（P5 重设计）：详情统计 + 关注/取关/拉黑 + 4 分区（插画/漫画/小说/系列）。
 * 顶部 Tab 支持左右滑动切换（HorizontalPager），每段独立分页（PagedState 驻留 VM）。
 * 统计格可点击：插画/小说 → 滑动切段；收藏/关注 → 进入该用户的公开收藏/关注列表页。
 *
 * @param onBack 返回
 * @param onOpenIllust 打开作品详情
 * @param onOpenNovel 打开小说详情
 * @param onOpenCover 打开全屏大图（小说封面 / 头部头像共用）
 * @param onOpenUser 打开用户主页
 * @param onSearchTag 标签搜索（跳发现页）
 * @param onOpenSeries 打开小说系列详情
 * @param onOpenUserBookmarks 打开该用户公开收藏
 * @param onOpenUserFollowing 打开该用户关注列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserRoute(
    userId: Long,
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
    onOpenUser: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenUserBookmarks: () -> Unit,
    onOpenUserFollowing: () -> Unit,
    viewModel: UserViewModel = hiltViewModel(),
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isFollowed by viewModel.isFollowed.collectAsStateWithLifecycle()
    val isFollowing by viewModel.isFollowing.collectAsStateWithLifecycle()
    val isBlocked by viewModel.isBlocked.collectAsStateWithLifecycle()
    val isBlocking by viewModel.isBlocking.collectAsStateWithLifecycle()
    val section by viewModel.section.collectAsStateWithLifecycle()
    val seriesInfos by viewModel.seriesInfos.collectAsStateWithLifecycle()

    val sections = UserSection.entries
    val pagerState = rememberPagerState(
        initialPage = sections.indexOf(section).coerceAtLeast(0),
        pageCount = { sections.size },
    )
    val scope = rememberCoroutineScope()

    // 滑动切页 → 同步分区（未加载则加载该段）
    LaunchedEffect(pagerState.currentPage) {
        val page = pagerState.currentPage
        if (page in sections.indices) {
            viewModel.selectSection(sections[page])
        }
    }

    val notificationHostState = rememberNotificationHostState()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.message.collect { msg ->
            notificationHostState.show(context.getString(msg.res, *msg.args.toTypedArray()), type = msg.type.toNotificationType())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(user?.name ?: stringResource(R.string.user_title_default), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { NotificationHost(notificationHostState) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        AdaptiveContentBox(modifier = Modifier.padding(padding)) {
            when {
                isLoading && user == null -> UserProfileSkeleton()
                error != null && user == null -> error!!.let { msg ->
                    ErrorBox(message = stringResource(msg.res, *msg.args.toTypedArray()), onRetry = viewModel::load)
                }
                user == null -> EmptyBox(stringResource(R.string.user_not_found))
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    val detail = checkNotNull(user)
                    UserHeader(
                        user = detail,
                        profile = profile,
                        isFollowed = isFollowed,
                        isFollowing = isFollowing,
                        isBlocked = isBlocked,
                        isBlocking = isBlocking,
                        onToggleFollow = viewModel::toggleFollow,
                        onToggleBlock = viewModel::toggleBlock,
                        onScrollToSection = { sec ->
                            scope.launch { pagerState.animateScrollToPage(sections.indexOf(sec)) }
                        },
                        onOpenUserBookmarks = onOpenUserBookmarks,
                        onOpenUserFollowing = onOpenUserFollowing,
                        onOpenAvatar = onOpenCover,
                    )
                    // 分区 Tab：PrimaryTabRow 均分占满（手机/平板一致，4 个短标签均放得下）
                    PrimaryTabRow(
                        selectedTabIndex = pagerState.currentPage.coerceIn(0, (sections.size - 1).coerceAtLeast(0)),
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        for (index in sections.indices) {
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                text = { Text(stringResource(sections[index].labelRes)) },
                            )
                        }
                    }
                    // 分区内容（Pager 每页只 collect 自己段的状态）
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f),
                    ) { page ->
                        when (sections.getOrNull(page)) {
                            UserSection.ILLUST -> SectionIllust(
                                paged = viewModel.illustPaged,
                                onOpenIllust = onOpenIllust,
                                onOpenUser = onOpenUser,
                                onToggleFavorite = viewModel::toggleIllustFavorite,
                                onRetry = viewModel::load,
                                onLoadMore = viewModel::loadMore,
                            )
                            UserSection.MANGA -> SectionIllust(
                                paged = viewModel.mangaPaged,
                                onOpenIllust = onOpenIllust,
                                onOpenUser = onOpenUser,
                                onToggleFavorite = viewModel::toggleIllustFavorite,
                                onRetry = viewModel::load,
                                onLoadMore = viewModel::loadMore,
                            )
                            UserSection.NOVEL -> SectionNovel(
                                paged = viewModel.novelPaged,
                                onOpenNovel = onOpenNovel,
                                onOpenCover = onOpenCover,
                                onOpenUser = onOpenUser,
                                onOpenSeries = onOpenSeries,
                                onToggleFavorite = { id, fav -> viewModel.toggleNovelFavorite(id, fav) },
                                onTagClick = onSearchTag,
                                onRetry = viewModel::load,
                                onLoadMore = viewModel::loadMore,
                            )
                            UserSection.SERIES -> SectionSeries(
                                paged = viewModel.seriesPaged,
                                infos = seriesInfos,
                                onOpenSeries = onOpenSeries,
                                onRetry = viewModel::load,
                                onLoadMore = viewModel::loadMore,
                            )
                            null -> EmptyBox("")
                        }
                    }
                }
            }
        }
    }
}
