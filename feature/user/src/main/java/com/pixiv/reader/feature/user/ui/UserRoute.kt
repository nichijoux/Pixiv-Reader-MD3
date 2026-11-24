package com.pixiv.reader.feature.user.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.PixivConstants
import com.pixiv.reader.core.comment.state.CommentListViewModel
import com.pixiv.reader.core.comment.state.CommentTarget
import com.pixiv.reader.core.comment.ui.CommentPane
import com.pixiv.reader.core.network.illust.IllustViewModel
import com.pixiv.reader.core.network.novel.NovelViewModel
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.detail.IllustDetailPane
import com.pixiv.reader.core.ui.component.detail.IllustDetailStrings
import com.pixiv.reader.core.ui.component.layout.ListDetailOverlay
import com.pixiv.reader.core.ui.component.layout.SegmentedPager
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
import kotlin.math.roundToInt

/**
 * 平板 Master-Detail：作品卡复用 core:ui [IllustDetailPane]，小说/系列 pane 经槽位由 app 组合根注入
 * （feature 之间禁止依赖，故不直接引用）。pane 间互跳用 paneHistory 压栈，返回键逐级还原、栈空才关闭。
 * 头部折叠由 NestedScrollConnection 接管：上滑先收简介+统计行，列表到顶后下滑先展开。
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
    // 空则复用 [onSearchTag]
    onNovelSearchTag: ((String) -> Unit)? = null,
    onOpenSeries: (Long) -> Unit,
    onOpenMangaSeries: (Long) -> Unit,
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
    val followState by viewModel.followState.collectAsStateWithLifecycle()
    val isFollowed = followState.isOn
    val isFollowing = followState.inFlight
    val blockState by viewModel.blockState.collectAsStateWithLifecycle()
    val isBlocked = blockState.isOn
    val isBlocking = blockState.inFlight
    val section by viewModel.section.collectAsStateWithLifecycle()
    val seriesInfos by viewModel.seriesInfos.collectAsStateWithLifecycle()

    // 三者互斥，关闭时全清
    var selectedIllustId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedNovelId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedSeriesId by rememberSaveable { mutableStateOf<Long?>(null) }
    // "N:小说"/"S:系列"/"I:作品" 编码：不压栈的话互跳链会被返回键一步清空
    var paneHistory by rememberSaveable { mutableStateOf(listOf<String>()) }
    val closePane = {
        selectedIllustId = null
        selectedNovelId = null
        selectedSeriesId = null
        paneHistory = emptyList()
    }
    // 调用方必须先压栈再切换目标
    val pushPaneHistory = {
        val current = selectedNovelId?.let { "N:$it" }
            ?: selectedIllustId?.let { "I:$it" }
            ?: selectedSeriesId?.let { "S:$it" }
        if (current != null) paneHistory = paneHistory + current
    }
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
    // 用户页为全屏路由（无 NavigationRail），故不减 rail 宽（排行页同款）
    val detailPaneEnabled = isDetailPaneEnabled(subtractRail = false)

    val sections = UserSection.entries
    // Pager 状态外提：统计格点击直接滚分区，SegmentedPager 复用同一状态
    val pagerState = rememberPagerState(
        initialPage = sections.indexOf(section).coerceAtLeast(0),
        pageCount = { sections.size },
    )
    val scope = rememberCoroutineScope()

    val notificationHostState = rememberNotificationHostState()
    UiMessageEffect(viewModel.message, notificationHostState)

    // ── 个人头部折叠（下滑收起简介 + 统计行；头像/关注/拉黑行与分区 Tab 常驻）──
    // headerMaxPx：可折叠区自然总高（内容测量回写）；headerCollapsePx：已收起高度（0..headerMaxPx）
    var headerMaxPx by remember { mutableStateOf(0) }
    var headerCollapsePx by remember { mutableStateOf(0f) }
    val headerNestedScroll = remember(headerMaxPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 上滑（dy < 0）：列表滚动前先把头部收完，消费对应位移
                if (available.y < 0f && headerCollapsePx < headerMaxPx) {
                    val previous = headerCollapsePx
                    headerCollapsePx = (headerCollapsePx - available.y).coerceAtMost(headerMaxPx.toFloat())
                    return Offset(0f, previous - headerCollapsePx)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // 下滑（dy > 0）：列表已滚到顶（内容未消费的位移）时先展开头部
                if (available.y > 0f && headerCollapsePx > 0f) {
                    val previous = headerCollapsePx
                    headerCollapsePx = (headerCollapsePx - available.y).coerceAtLeast(0f)
                    return Offset(0f, previous - headerCollapsePx)
                }
                return Offset.Zero
            }
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
        snackbarHost = {
            // 沉浸式底部：Scaffold 已不垫内容，通知条需自行避开系统导航栏（不会双重避让）
            NotificationHost(notificationHostState, modifier = Modifier.navigationBarsPadding())
        },
        modifier = Modifier.fillMaxSize(),
        // 沉浸式底部：系统导航栏区域留给列表/详情直通，由列表尾部 contentPadding 与 pane 内部 inset 自行避让
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        ListDetailOverlay(
            selected = selectedIllustId ?: selectedNovelId ?: selectedSeriesId,
            onClose = closePane,
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
                        else -> Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(headerNestedScroll),
                        ) {
                            val detail = checkNotNull(user)
                            UserHeaderProfileRow(
                                user = detail,
                                isFollowed = isFollowed,
                                isFollowing = isFollowing,
                                isBlocked = isBlocked,
                                isBlocking = isBlocking,
                                onToggleFollow = viewModel::toggleFollow,
                                onFollowPrivately = { viewModel.toggleFollow(PixivConstants.RESTRICT_PRIVATE) },
                                onToggleBlock = viewModel::toggleBlock,
                                onOpenAvatar = onOpenCover,
                            )
                            // 显示高度 = 总高 - 已折叠，内容整体上移并裁剪（简介先收，统计行最后消失）
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clipToBounds()
                                    .layout { measurable, constraints ->
                                        // 内容按自然高度测量（放开 maxHeight），裁剪由本容器负责
                                        val placeable = measurable.measure(
                                            constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
                                        )
                                        val visible = if (headerMaxPx == 0) {
                                            placeable.height
                                        } else {
                                            (headerMaxPx - headerCollapsePx.roundToInt()).coerceAtLeast(0)
                                        }
                                        layout(placeable.width, visible) {
                                            placeable.place(0, -headerCollapsePx.roundToInt())
                                        }
                                    },
                            ) {
                                Box(
                                    modifier = Modifier.onSizeChanged { size ->
                                        if (size.height > 0) headerMaxPx = size.height
                                    },
                                ) {
                                    UserHeaderDetails(
                                        user = detail,
                                        profile = profile,
                                        onScrollToSection = { sec ->
                                            scope.launch { pagerState.animateScrollToPage(sections.indexOf(sec)) }
                                        },
                                        onOpenUserBookmarks = onOpenUserBookmarks,
                                        onOpenUserFollowing = onOpenUserFollowing,
                                    )
                                }
                            }
                            // 选中态跟 Pager 落页，点击反向滚页，落页同步分区（未加载则加载该段）
                            SegmentedPager(
                                tabs = sections,
                                state = pagerState,
                                onSelect = viewModel::selectSection,
                                tabLabel = { it.labelRes },
                                pagerModifier = Modifier.weight(1f),
                                pageContent = { _, tab ->
                                    when (tab) {
                                        UserSection.ILLUST -> SectionIllust(
                                            paged = viewModel.illustPaged,
                                            // 平板（pane 启用）→ 右栏详情；手机 → 全屏路由
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
                                            onOpenSeries = { id ->
                                                if (detailPaneEnabled) selectedSeriesId = id else onOpenSeries(id)
                                            },
                                            onToggleFavorite = { id, fav -> viewModel.toggleNovelFavorite(id, fav) },
                                            onTagClick = onNovelSearchTag ?: onSearchTag,
                                            onRetry = viewModel::load,
                                            onLoadMore = viewModel::loadMore,
                                        )
                                        UserSection.SERIES -> SectionSeries(
                                            paged = viewModel.seriesPaged,
                                            infos = seriesInfos,
                                            mangaPaged = viewModel.mangaSeriesPaged,
                                            onOpenSeries = { id ->
                                                if (detailPaneEnabled) selectedSeriesId = id else onOpenSeries(id)
                                            },
                                            onOpenMangaSeries = onOpenMangaSeries,
                                            onRetry = viewModel::load,
                                            onLoadMore = viewModel::loadMore,
                                            onRetryManga = viewModel::retryMangaSeries,
                                            onLoadMoreManga = viewModel::loadMoreMangaSeries,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            },
            detailPane = {
                // pane 作用域 ViewModel（本 backstack entry 级，与首页/关注页 pane 同款）
                val illustDetailVm: IllustViewModel = hiltViewModel()
                val novelDetailVm: NovelViewModel = hiltViewModel()
                val commentVm: CommentListViewModel = hiltViewModel()
                when {
                    selectedNovelId != null -> novelDetailPane(
                        selectedNovelId,
                        novelDetailVm,
                        commentVm,
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
                        { id ->
                            if (detailPaneEnabled) {
                                pushPaneHistory()
                                selectedSeriesId = null
                                selectedNovelId = id
                            } else {
                                onOpenNovel(id)
                            }
                        },
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
                        comments = { onBackToDetail ->
                            CommentPane(
                                commentVm = commentVm,
                                onOpenUser = onOpenUser,
                                onBackToDetail = onBackToDetail,
                            )
                        },
                        onOpenComments = { selectedIllustId?.let { commentVm.switchTo(CommentTarget.ILLUST, it) } },
                        viewModel = illustDetailVm,
                    )
                }
            },
        )
    }
}
