package com.pixiv.reader.feature.user.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.core.network.comment.CommentListViewModel
import com.pixiv.reader.core.network.illust.IllustViewModel
import com.pixiv.reader.core.network.novel.NovelViewModel
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.detail.IllustDetailPane
import com.pixiv.reader.core.ui.component.detail.IllustDetailStrings
import com.pixiv.reader.core.ui.component.layout.ListDetailOverlay
import com.pixiv.reader.core.ui.component.layout.isDetailPaneEnabled
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.feature.user.R
import com.pixiv.reader.feature.user.state.UserSection
import com.pixiv.reader.feature.user.state.UserViewModel
import kotlinx.coroutines.launch

/**
 * 用户主页（P5 重设计）：详情统计 + 关注/取关/拉黑 + 4 分区（插画/漫画/小说/系列）。
 * 顶部 Tab 支持左右滑动切换（HorizontalPager），每段独立分页（PagedState 驻留 VM）。
 * 统计格可点击：插画/小说 → 滑动切段；收藏/关注 → 进入该用户的公开收藏/关注列表页。
 *
 * ## 平板 Master-Detail
 * 点作品/小说/系列卡 → 右侧详情 pane 滑入（[ListDetailOverlay]，Scaffold 内容区内、
 * TopAppBar 下方起步，状态栏由顶栏避让）：作品卡 → core:ui [IllustDetailPane]（直接复用）；
 * 小说卡 → [novelDetailPane] 槽位、系列卡 → [seriesDetailPane] 槽位（feature:novel 的
 * 小说详情/系列 pane 由 app 组合根注入，feature 间禁止依赖，故经槽位反转）。
 * 系列 pane 内分册点击由宿主分流：pane 启用 → 切换到小说详情 pane，否则全屏路由。
 * 未启用 pane（手机竖屏）时点击回退全屏路由跳转。
 *
 * @param onBack 返回
 * @param onOpenIllust 打开作品详情（pane 未启用时的全屏路由跳转）
 * @param onOpenNovel 打开小说详情（pane 未启用时的全屏路由跳转）
 * @param onOpenViewer 打开全屏查看器（pane 内图片点击；参数为作品 id + 页码）
 * @param onOpenCover 打开全屏大图（头部头像点击）
 * @param onOpenUser 打开用户主页
 * @param onSearchTag 标签搜索（跳发现页）
 * @param onOpenSeries 打开小说系列详情（全屏路由，系列不进 pane）
 * @param onOpenUserBookmarks 打开该用户公开收藏
 * @param onOpenUserFollowing 打开该用户关注列表
 * @param novelDetailPane 小说详情 pane 槽位（app 组合根注入；参数为选中小说 id、
 *   本页作用域的 [NovelViewModel] / [CommentListViewModel] 与「查看完整系列」回调
 *   （宿主分流 pane 内切换系列 pane / 全屏））
 * @param seriesDetailPane 小说系列 pane 槽位（app 组合根注入；参数为选中系列 id、
 *   分册点击回调（宿主分流 pane 内切换小说详情 / 全屏）与分册卡系列标题回调
 *   （宿主压栈后原地切换系列））
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserRoute(
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenViewer: (Long, Int) -> Unit,
    onOpenCover: (String) -> Unit,
    onOpenUser: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenUserBookmarks: () -> Unit,
    onOpenUserFollowing: () -> Unit,
    novelDetailPane: @Composable (
        selectedId: Long?,
        novelViewModel: NovelViewModel,
        commentViewModel: CommentListViewModel,
        onOpenSeries: (Long) -> Unit,
    ) -> Unit,
    seriesDetailPane: @Composable (
        selectedId: Long?,
        onOpenNovel: (Long) -> Unit,
        onOpenSeries: (Long) -> Unit,
    ) -> Unit,
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

    // 平板 pane 选中态：作品 / 小说 / 系列互斥（关闭时全清）
    var selectedIllustId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedNovelId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedSeriesId by rememberSaveable { mutableStateOf<Long?>(null) }
    // pane 间互跳返回栈（"N:小说"/"S:系列"/"I:作品"）：每次互跳压栈当前选中，
    // 返回键逐级还原上一个 pane，栈空才关闭 pane（防互跳链被返回键一步清空）
    var paneHistory by rememberSaveable { mutableStateOf(listOf<String>()) }
    val closePane = {
        selectedIllustId = null
        selectedNovelId = null
        selectedSeriesId = null
        paneHistory = emptyList()
    }
    // 互跳前压栈当前选中（调用方先 push 再切换目标）
    val pushPaneHistory = {
        val current = selectedNovelId?.let { "N:$it" }
            ?: selectedIllustId?.let { "I:$it" }
            ?: selectedSeriesId?.let { "S:$it" }
        if (current != null) paneHistory = paneHistory + current
    }
    // 返回键：栈非空逐级还原上一个选中（pane 间回退），栈空关闭 pane
    val popPaneOrClose = {
        val last = paneHistory.lastOrNull()
        if (last == null) {
            closePane()
        } else {
            paneHistory = paneHistory.dropLast(1)
            selectedNovelId = null
            selectedIllustId = null
            selectedSeriesId = null
            when (last.substringBefore(":")) {
                "N" -> selectedNovelId = last.substringAfter(":").toLongOrNull()
                "S" -> selectedSeriesId = last.substringAfter(":").toLongOrNull()
                "I" -> selectedIllustId = last.substringAfter(":").toLongOrNull()
            }
        }
    }
    // 用户页为全屏路由（无 NavigationRail），启用判定不减 rail 宽（排行页同款）
    val detailPaneEnabled = isDetailPaneEnabled(subtractRail = false)

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
    UiMessageEffect(viewModel.message, notificationHostState)

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
        snackbarHost = {
            // 沉浸式底部：通知条自行避让系统导航栏（Scaffold 已不垫内容，无双重避让）
            NotificationHost(notificationHostState, modifier = Modifier.navigationBarsPadding())
        },
        modifier = Modifier.fillMaxSize(),
        // 沉浸式底部：不再由 Scaffold 垫高内容（系统导航栏区域留给列表/详情直通，
        // 列表尾部 contentPadding 与 pane 内部 inset 自行避让，与小说/漫画 Tab 同款）
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        ListDetailOverlay(
            selected = selectedIllustId ?: selectedNovelId ?: selectedSeriesId,
            onClose = closePane,
            // 返回键沿 pane 互跳链逐级回退，栈空才关闭
            onBack = popPaneOrClose,
            // 消费已应用的 padding，pane 内 navigationBarsPadding 按剩余可见 inset 自适应
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            listContent = { listMax ->
                AdaptiveContentBox(maxWidth = listMax) {
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
                                        // 平板（pane 启用）→ 选中进右栏详情；手机 → 全屏路由跳转
                                        onOpenIllust = { id ->
                                            if (detailPaneEnabled) selectedIllustId = id else onOpenIllust(id)
                                        },
                                        onOpenUser = onOpenUser,
                                        onToggleFavorite = viewModel::toggleIllustFavorite,
                                        onRetry = viewModel::load,
                                        onLoadMore = viewModel::loadMore,
                                    )
                                    UserSection.MANGA -> SectionIllust(
                                        paged = viewModel.mangaPaged,
                                        onOpenIllust = { id ->
                                            if (detailPaneEnabled) selectedIllustId = id else onOpenIllust(id)
                                        },
                                        onOpenUser = onOpenUser,
                                        onToggleFavorite = viewModel::toggleIllustFavorite,
                                        onRetry = viewModel::load,
                                        onLoadMore = viewModel::loadMore,
                                    )
                                    UserSection.NOVEL -> SectionNovel(
                                        paged = viewModel.novelPaged,
                                        onOpenNovel = { id ->
                                            if (detailPaneEnabled) selectedNovelId = id else onOpenNovel(id)
                                        },
                                        onOpenUser = onOpenUser,
                                        // 系列 pane 启用 → 进右栏；否则全屏路由
                                        onOpenSeries = { id ->
                                            if (detailPaneEnabled) selectedSeriesId = id else onOpenSeries(id)
                                        },
                                        onToggleFavorite = { id, fav -> viewModel.toggleNovelFavorite(id, fav) },
                                        onTagClick = onSearchTag,
                                        onRetry = viewModel::load,
                                        onLoadMore = viewModel::loadMore,
                                    )
                                    UserSection.SERIES -> SectionSeries(
                                        paged = viewModel.seriesPaged,
                                        infos = seriesInfos,
                                        onOpenSeries = { id ->
                                            if (detailPaneEnabled) selectedSeriesId = id else onOpenSeries(id)
                                        },
                                        onRetry = viewModel::load,
                                        onLoadMore = viewModel::loadMore,
                                    )
                                    null -> EmptyBox("")
                                }
                            }
                        }
                    }
                }
            },
            detailPane = {
                // pane 作用域 ViewModel（本 backstack entry 级，与首页/关注页 pane 同款模式）
                val illustDetailVm: IllustViewModel = hiltViewModel()
                val novelDetailVm: NovelViewModel = hiltViewModel()
                val commentVm: CommentListViewModel = hiltViewModel()
                when {
                    selectedNovelId != null -> novelDetailPane(
                        selectedNovelId,
                        novelDetailVm,
                        commentVm,
                        // 「查看完整系列」：pane 启用 → 压栈当前选中、切换到系列 pane；否则全屏路由
                        { id ->
                            if (detailPaneEnabled) {
                                pushPaneHistory()
                                selectedNovelId = null
                                selectedSeriesId = id
                            } else {
                                onOpenSeries(id)
                            }
                        },
                    )
                    selectedSeriesId != null -> seriesDetailPane(
                        selectedSeriesId,
                        // 分册点击：pane 启用 → 压栈当前选中、切换到小说详情 pane；否则全屏路由
                        { id ->
                            if (detailPaneEnabled) {
                                pushPaneHistory()
                                selectedSeriesId = null
                                selectedNovelId = id
                            } else {
                                onOpenNovel(id)
                            }
                        },
                        // 分册卡系列标题：压栈当前选中、原地切换系列（返回键可回退）
                        { id ->
                            pushPaneHistory()
                            selectedSeriesId = id
                        },
                    )
                    selectedIllustId != null -> IllustDetailPane(
                        selectedId = selectedIllustId,
                        strings = IllustDetailStrings(
                            loadRetry = stringResource(R.string.user_illust_load_retry),
                            fullscreen = stringResource(R.string.user_illust_fullscreen),
                            statView = stringResource(R.string.user_illust_stat_view),
                            statBookmark = stringResource(R.string.user_illust_stat_bookmark),
                            statPages = stringResource(R.string.user_illust_stat_pages),
                            expand = stringResource(R.string.user_illust_expand),
                            collapse = stringResource(R.string.user_illust_collapse),
                            follow = stringResource(R.string.user_illust_follow),
                            followed = stringResource(R.string.user_illust_followed),
                            related = stringResource(R.string.user_illust_related),
                            bookmark = stringResource(R.string.user_illust_bookmark),
                            bookmarked = stringResource(R.string.user_illust_bookmarked),
                            download = stringResource(R.string.user_illust_download),
                            comments = stringResource(R.string.user_illust_comments),
                        ),
                        placeholder = stringResource(R.string.user_pane_placeholder),
                        onOpenUser = onOpenUser,
                        onOpenViewer = onOpenViewer,
                        commentVm = commentVm,
                        viewModel = illustDetailVm,
                    )
                }
            },
        )
    }
}
