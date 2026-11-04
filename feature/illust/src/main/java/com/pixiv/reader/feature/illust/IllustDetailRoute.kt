package com.pixiv.reader.feature.illust

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.Illust
import com.pixiv.api.PixivConstants
import com.pixiv.reader.core.network.illust.IllustViewModel
import com.pixiv.reader.core.common.ui.WindowSizeClass
import com.pixiv.reader.core.network.model.IllustPageInfo
import com.pixiv.reader.core.ui.R as CoreUiR
import com.pixiv.reader.core.ui.component.bookmark.BookmarkEditSheet
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.detail.HeroSeed
import com.pixiv.reader.core.ui.component.detail.HeroSeedStore
import com.pixiv.reader.core.ui.component.detail.IllustDetailContent
import com.pixiv.reader.core.ui.component.detail.IllustDetailSkeleton
import com.pixiv.reader.core.ui.component.detail.IllustDetailStrings
import com.pixiv.reader.core.ui.component.detail.IllustInfoSection
import com.pixiv.reader.core.ui.component.detail.IllustInfoSkeleton
import com.pixiv.reader.core.ui.component.detail.IllustPagePager
import com.pixiv.reader.core.ui.component.detail.IllustRelatedSection
import com.pixiv.reader.core.ui.component.detail.illustCoverKey
import com.pixiv.reader.core.ui.component.input.VerticalActionButton
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.layout.currentWindowSizeClass
import com.pixiv.reader.core.ui.theme.FavoriteRed
import com.pixiv.reader.core.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IllustDetailRoute(
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenViewer: (Long, Int) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenComments: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    viewModel: IllustViewModel = hiltViewModel(),
) {
    val illust by viewModel.illust.collectAsStateWithLifecycle()
    // hero 占位用的作品 id：取路由参数（SavedStateHandle 初始化即有值，数据未到时也能定位 key）
    val routeIllustId by viewModel.illustIdFlow.collectAsStateWithLifecycle()
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val ugoiraFrames by viewModel.ugoiraFrames.collectAsStateWithLifecycle()
    val ugoiraProgress by viewModel.ugoiraProgress.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    // 三对开关状态机：单流收集，派生展示态/进行中两个只读值供下游使用
    val bookmarkState by viewModel.bookmarkState.collectAsStateWithLifecycle()
    val isBookmarked = bookmarkState.isOn
    val isBookmarking = bookmarkState.inFlight
    val authorFollowState by viewModel.authorFollowState.collectAsStateWithLifecycle()
    val isAuthorFollowed = authorFollowState.isOn
    val isAuthorFollowing = authorFollowState.inFlight
    // 收藏编辑器状态（公开/私密 + 标签弹层）
    val editorOpen by viewModel.bookmarkEditor.isOpen.collectAsStateWithLifecycle()
    val editorRestrict by viewModel.bookmarkEditor.restrict.collectAsStateWithLifecycle()
    val editorSavedTags by viewModel.bookmarkEditor.savedTags.collectAsStateWithLifecycle()
    val editorAllTags by viewModel.bookmarkEditor.allTags.collectAsStateWithLifecycle()
    val editorTagsLoading by viewModel.bookmarkEditor.tagsLoading.collectAsStateWithLifecycle()
    val editorSaving by viewModel.isEditorSaving.collectAsStateWithLifecycle()
    // 已收藏且为私密时，底部收藏按钮显示「私密收藏」文案标识
    val isPrivateBookmark = isBookmarked && editorRestrict == PixivConstants.RESTRICT_PRIVATE
    // 所属漫画系列追更态（illust.series 非空才显示追更按钮）
    val seriesId = illust?.series?.id?.takeIf { it > 0L }
    val seriesWatchlistState by viewModel.seriesWatchlistState.collectAsStateWithLifecycle()
    val isSeriesWatchlisted = seriesWatchlistState.isOn
    val isSeriesWatchlisting = seriesWatchlistState.inFlight

    var currentPage by remember { mutableIntStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    val notificationHostState = rememberNotificationHostState()
    UiMessageEffect(viewModel.message, notificationHostState)
    // hero 种子兜底清理：详情页销毁时丢弃未消费的种子（详情命中缓存 / 占位分支未组合的情况），
    // 防过期封面被后续无关打开复用
    val heroKey = illustCoverKey(routeIllustId)
    // hero 种子在 Route 层消费一次，经参数下发手机/平板两条链路：
    // 上下游各自 consume 会互相抢（取出即删，第二个拿到 null）
    val heroSeed = remember(heroKey) { HeroSeedStore.consume(heroKey) }
    DisposableEffect(heroKey) {
        onDispose { HeroSeedStore.clear(heroKey) }
    }
    // 下沉详情块文案（feature:illust 侧提供，core:ui 不直接引用本模块 R）
    val illustDetailStrings = IllustDetailStrings(
        loadRetry = stringResource(R.string.illust_load_retry),
        fullscreen = stringResource(R.string.illust_action_fullscreen),
        statView = stringResource(R.string.illust_stat_view),
        statBookmark = stringResource(R.string.illust_stat_bookmark),
        statPages = stringResource(R.string.illust_stat_pages),
        expand = stringResource(R.string.illust_expand),
        collapse = stringResource(R.string.illust_collapse),
        follow = stringResource(R.string.illust_follow),
        followed = stringResource(R.string.illust_followed),
        related = stringResource(R.string.illust_section_related),
        bookmark = stringResource(R.string.illust_menu_bookmark),
        bookmarked = stringResource(R.string.illust_bookmarked),
        download = stringResource(R.string.illust_cd_download),
        comments = stringResource(R.string.illust_cd_comments),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.illust_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.illust_cd_back)
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.illust_cd_more)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.illust_menu_download_original)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Download,
                                        contentDescription = null
                                    )
                                },
                                onClick = { menuExpanded = false; viewModel.download() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(if (isBookmarked) R.string.illust_menu_unbookmark else R.string.illust_menu_bookmark)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isBookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        contentDescription = null,
                                    )
                                },
                                onClick = { menuExpanded = false; viewModel.toggleBookmark() },
                            )
                            // 收藏设置：公开/私密 + 标签（弹层保存）
                            DropdownMenuItem(
                                text = { Text(stringResource(CoreUiR.string.bookmark_edit_title)) },
                                leadingIcon = {
                                    Icon(Icons.Filled.Label, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.bookmarkEditor.open()
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            val pageToOpen = if (illust?.isGif() == true) 0 else currentPage
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = Spacing.md, vertical = Spacing.smPlus),
                horizontalArrangement = Arrangement.spacedBy(Spacing.smPlus),
            ) {
                VerticalActionButton(
                    icon = Icons.Filled.Fullscreen,
                    label = stringResource(R.string.illust_action_fullscreen),
                    active = false,
                    enabled = true,
                    onClick = { onOpenViewer(illust?.id ?: 0L, pageToOpen) },
                    modifier = Modifier.weight(1f),
                )
                VerticalActionButton(
                    icon = if (isBookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    label = stringResource(
                        when {
                            isBookmarked && isPrivateBookmark -> CoreUiR.string.bookmark_private_cd
                            isBookmarked -> R.string.illust_bookmarked
                            else -> R.string.illust_menu_bookmark
                        }
                    ),
                    active = isBookmarked,
                    enabled = !isBookmarking,
                    onClick = viewModel::toggleBookmark,
                    modifier = Modifier.weight(1f),
                    // 收藏激活用红心（保留 App 收藏色习惯）
                    activeIconTint = FavoriteRed,
                )
                // 漫画系列追更按钮（作品属于漫画系列时显示，与小说详情页追更同语义）
                if (seriesId != null) {
                    VerticalActionButton(
                        icon = if (isSeriesWatchlisted) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                        label = stringResource(
                            if (isSeriesWatchlisted) R.string.illust_watchlisted else R.string.illust_watchlist
                        ),
                        active = isSeriesWatchlisted,
                        enabled = !isSeriesWatchlisting,
                        onClick = viewModel::toggleSeriesWatchlist,
                        modifier = Modifier.weight(1f),
                    )
                }
                VerticalActionButton(
                    icon = Icons.Filled.Download,
                    label = stringResource(R.string.illust_cd_download),
                    active = false,
                    enabled = true,
                    onClick = viewModel::download,
                    modifier = Modifier.weight(1f),
                )
                VerticalActionButton(
                    icon = Icons.Filled.ModeComment,
                    label = stringResource(R.string.illust_cd_comments),
                    active = false,
                    enabled = true,
                    onClick = { onOpenComments(illust?.id ?: 0L) },
                    modifier = Modifier.weight(1f),
                )
            }
        },
        snackbarHost = { NotificationHost(notificationHostState) },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        val size = currentWindowSizeClass()
        val twoPane = size != WindowSizeClass.Compact

        if (twoPane) {
            // 平板双栏：加载中/失败也要有明确状态，避免显示空骨架
            val hasPagerContent = pages.isNotEmpty() || heroSeed != null
            when {
                error != null && illust == null -> ErrorBox(
                    message = error?.let { stringResource(it.res, *it.args.toTypedArray()) },
                    onRetry = viewModel::load,
                    modifier = Modifier.padding(padding),
                )
                // 深链进入（无 hero 种子且页面数据未到）：完整骨架兜底
                !hasPagerContent -> Box(modifier = Modifier.padding(padding)) {
                    IllustDetailSkeleton()
                }

                else -> TwoPaneContent(
                    modifier = Modifier.padding(padding),
                    illust = illust,
                    pages = pages,
                    heroSeed = heroSeed,
                    heroIllustId = routeIllustId.takeIf { it > 0L },
                    onPageChange = { currentPage = it },
                    onOpenViewer = { onOpenViewer(illust?.id ?: 0L, it) },
                    onOpenUser = onOpenUser,
                    onOpenIllust = onOpenIllust,
                    onSearchTag = onSearchTag,
                    isAuthorFollowed = isAuthorFollowed,
                    isAuthorFollowing = isAuthorFollowing,
                    onToggleFollowAuthor = viewModel::toggleFollowAuthor,
                    isBookmarked = isBookmarked,
                    isBookmarking = isBookmarking,
                    onOpenComments = onOpenComments,
                    strings = illustDetailStrings,
                    viewModel = viewModel,
                )
            }
        } else {
            // 手机单列
            AdaptiveContentBox(modifier = Modifier.padding(padding)) {
                // hero 元素全程唯一：有种子时 pager 以占位封面承载 hero（占位态不渲染页码等），
                // 数据到达后同一容器原地转真实 pager，不再有 skeleton+seed → pager 的硬切
                val hasPagerContent = pages.isNotEmpty() || heroSeed != null
                when {
                    error != null && illust == null -> ErrorBox(message = error?.let {
                        stringResource(
                            it.res,
                            *it.args.toTypedArray()
                        )
                    }, onRetry = viewModel::load)

                    // 深链进入（无 hero 种子且页面数据未到）：完整骨架兜底
                    !hasPagerContent -> IllustDetailSkeleton()

                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item(key = "pager") {
                            IllustPagePager(
                                pages = pages,
                                ugoiraFrames = ugoiraFrames,
                                ugoiraProgress = ugoiraProgress,
                                onPageChange = { currentPage = it },
                                onOpenViewer = { onOpenViewer(illust?.id ?: 0L, it) },
                                strings = illustDetailStrings,
                                // hero：大图容器与列表卡片封面同 key
                                illustId = illust?.id ?: routeIllustId.takeIf { it > 0L },
                                // 垫底 medium：与卡片封面/hero 占位同源，原图未就绪时先顶上
                                placeholderUrl = illust?.image_urls?.medium,
                                // 首页兜底比例取 illust 宽高（与卡片封面同源）：pages 宽高
                                // 未就绪时首帧高度即与 hero 占位一致，避免接棒后两连跳
                                fallbackRatio = illust
                                    ?.takeIf { it.width > 0 && it.height > 0 }
                                    ?.let { it.width.toFloat() / it.height.toFloat() },
                                // 数据到达前以种子封面占位（hero 元素不重建）
                                seed = heroSeed,
                            )
                        }
                        item(key = "info") {
                            if (illust == null) {
                                IllustInfoSkeleton()
                            } else {
                                IllustInfoSection(
                                    illust = illust,
                                    strings = illustDetailStrings,
                                    onOpenUser = onOpenUser,
                                    onSearchTag = onSearchTag,
                                    onOpenViewer = { onOpenViewer(illust?.id ?: 0L, it) },
                                    isAuthorFollowed = isAuthorFollowed,
                                    isAuthorFollowing = isAuthorFollowing,
                                    onToggleFollowAuthor = viewModel::toggleFollowAuthor,
                                    isBookmarked = isBookmarked,
                                    isBookmarking = isBookmarking,
                                    onToggleBookmark = viewModel::toggleBookmark,
                                    onDownload = viewModel::download,
                                    onOpenComments = { onOpenComments(illust?.id ?: 0L) },
                                    expandableIntro = true,
                                    // 手机详情页操作走底部固定栏，标签下不再重复
                                    showActionRow = false,
                                )
                            }
                        }
                        item(key = "related") {
                            val relatedItems by viewModel.relatedPaged.items.collectAsStateWithLifecycle()
                            // 数据未到达前不渲染相关作品区（避免占位阶段空标题露出）
                            if (illust != null || relatedItems.isNotEmpty()) {
                                IllustRelatedSection(
                                    items = relatedItems,
                                    strings = illustDetailStrings,
                                    onOpenIllust = onOpenIllust,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 收藏编辑弹层：公开/私密 + 标签多选，保存走 viewModel.saveBookmarkEditor
    BookmarkEditSheet(
        visible = editorOpen,
        restrict = editorRestrict,
        savedTags = editorSavedTags,
        allTags = editorAllTags,
        tagsLoading = editorTagsLoading,
        saving = editorSaving,
        onDismiss = viewModel.bookmarkEditor::close,
        onRestrictChange = viewModel.bookmarkEditor::setRestrict,
        onToggleTag = viewModel.bookmarkEditor::toggleTag,
        onCreateTag = viewModel.bookmarkEditor::createTag,
        onConfirm = viewModel::saveBookmarkEditor,
    )
}

/**
 * 平板布局：主内容单列（图 + 作者 + 相关），评论走通用评论页。
 *
 * @param modifier 外部 Modifier（携带 Scaffold innerPadding）
 * @param illust 作品数据；null 表示页面数据未到（信息区由骨架占位）
 * @param pages 各 P 图片元数据
 * @param heroSeed hero 种子（pages 未就绪时 pager 的封面占位，保证 hero 元素唯一）
 * @param heroIllustId hero 共享元素 key 的回退作品 id（路由参数，illust 未到达时定位 key）
 * @param onPageChange 当前页变化回调
 * @param onOpenViewer 全屏入口回调
 * @param onOpenUser 打开作者主页回调
 * @param onOpenIllust 简介内打开其他插画回调
 * @param onSearchTag 点击标签搜索回调
 * @param isAuthorFollowed 作者是否已关注
 * @param isAuthorFollowing 关注操作是否进行中
 * @param onToggleFollowAuthor 切换作者关注回调
 * @param isBookmarked 是否已收藏
 * @param isBookmarking 收藏操作是否进行中
 * @param onOpenComments 打开评论回调
 * @param strings 详情页文案
 * @param viewModel 详情 ViewModel（相关作品 / 动图 / 操作复用）
 * @return 无返回值（渲染 Composable）
 */
@Composable
private fun TwoPaneContent(
    modifier: Modifier,
    illust: Illust?,
    pages: List<IllustPageInfo>,
    heroSeed: HeroSeed?,
    heroIllustId: Long?,
    onPageChange: (Int) -> Unit,
    onOpenViewer: (Int) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenIllust: (Long) -> Unit,
    onSearchTag: (String) -> Unit,
    isAuthorFollowed: Boolean,
    isAuthorFollowing: Boolean,
    onToggleFollowAuthor: () -> Unit,
    isBookmarked: Boolean,
    isBookmarking: Boolean,
    onOpenComments: (Long) -> Unit,
    strings: IllustDetailStrings,
    viewModel: IllustViewModel,
) {
    val ugoiraFrames by viewModel.ugoiraFrames.collectAsStateWithLifecycle()
    val ugoiraProgress by viewModel.ugoiraProgress.collectAsStateWithLifecycle()
    val relatedItems by viewModel.relatedPaged.items.collectAsStateWithLifecycle()
    IllustDetailContent(
        illust = illust,
        pages = pages,
        ugoiraFrames = ugoiraFrames,
        ugoiraProgress = ugoiraProgress,
        relatedItems = relatedItems,
        strings = strings,
        onPageChange = onPageChange,
        onOpenViewer = onOpenViewer,
        onOpenUser = onOpenUser,
        onOpenIllust = onOpenIllust,
        onSearchTag = onSearchTag,
        isAuthorFollowed = isAuthorFollowed,
        isAuthorFollowing = isAuthorFollowing,
        onToggleFollowAuthor = onToggleFollowAuthor,
        isBookmarked = isBookmarked,
        isBookmarking = isBookmarking,
        onToggleBookmark = viewModel::toggleBookmark,
        onDownload = viewModel::download,
        onOpenComments = { onOpenComments(illust?.id ?: 0L) },
        expandableIntro = false,
        // 详情页平板分支同样有底部固定栏，标签下不重复操作行
        showActionRow = false,
        modifier = modifier,
        seed = heroSeed,
        heroIllustId = heroIllustId,
    )
}

