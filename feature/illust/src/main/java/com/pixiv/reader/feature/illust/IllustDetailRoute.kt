package com.pixiv.reader.feature.illust

import android.annotation.SuppressLint
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.common.format.formatCount
import com.pixiv.reader.core.network.illust.IllustViewModel
import com.pixiv.reader.core.common.ui.WindowSizeClass
import com.pixiv.reader.core.network.model.IllustPageInfo
import com.pixiv.reader.core.network.ugoira.UgoiraFrame
import com.pixiv.reader.core.ui.component.card.UserAvatar
import com.pixiv.reader.core.ui.component.feedback.ErrorBox
import com.pixiv.reader.core.ui.component.feedback.LoadingBox
import com.pixiv.reader.core.ui.component.feedback.NotificationHost
import com.pixiv.reader.core.ui.component.feedback.UiMessageEffect
import com.pixiv.reader.core.ui.component.feedback.rememberNotificationHostState
import com.pixiv.reader.core.ui.component.detail.IllustDetailContent
import com.pixiv.reader.core.ui.component.detail.IllustDetailStrings
import com.pixiv.reader.core.ui.component.detail.IllustInfoSection
import com.pixiv.reader.core.ui.component.detail.IllustPagePager
import com.pixiv.reader.core.ui.component.detail.IllustRelatedSection
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.component.image.UgoiraPlayer
import com.pixiv.reader.core.ui.component.input.VerticalActionButton
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.layout.currentWindowSizeClass
import com.pixiv.reader.core.ui.theme.AppShapes
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
    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val ugoiraFrames by viewModel.ugoiraFrames.collectAsStateWithLifecycle()
    val ugoiraProgress by viewModel.ugoiraProgress.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val isBookmarking by viewModel.isBookmarking.collectAsStateWithLifecycle()
    val isAuthorFollowed by viewModel.isAuthorFollowed.collectAsStateWithLifecycle()
    val isAuthorFollowing by viewModel.isAuthorFollowing.collectAsStateWithLifecycle()

    var currentPage by remember { mutableIntStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    val notificationHostState = rememberNotificationHostState()
    UiMessageEffect(viewModel.message, notificationHostState)
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
                    label = stringResource(if (isBookmarked) R.string.illust_bookmarked else R.string.illust_menu_bookmark),
                    active = isBookmarked,
                    enabled = !isBookmarking,
                    onClick = viewModel::toggleBookmark,
                    modifier = Modifier.weight(1f),
                    // 收藏激活用红心（保留 App 收藏色习惯）
                    activeIconTint = FavoriteRed,
                )
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
            when {
                isLoading && illust == null -> LoadingBox(modifier = Modifier.padding(padding))
                error != null && illust == null -> ErrorBox(
                    message = error?.let { stringResource(it.res, *it.args.toTypedArray()) },
                    onRetry = viewModel::load,
                    modifier = Modifier.padding(padding),
                )

                else -> TwoPaneContent(
                    modifier = Modifier.padding(padding),
                    illust = illust,
                    pages = pages,
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
                when {
                    isLoading && illust == null -> LoadingBox()
                    error != null && illust == null -> ErrorBox(message = error?.let {
                        stringResource(
                            it.res,
                            *it.args.toTypedArray()
                        )
                    }, onRetry = viewModel::load)

                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (pages.isNotEmpty()) {
                            item(key = "pager") {
                                IllustPagePager(
                                    pages = pages,
                                    ugoiraFrames = ugoiraFrames,
                                    ugoiraProgress = ugoiraProgress,
                                    onPageChange = { currentPage = it },
                                    onOpenViewer = { onOpenViewer(illust?.id ?: 0L, it) },
                                    strings = illustDetailStrings,
                                )
                            }
                        }
                        item(key = "info") {
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
                        item(key = "related") {
                            val relatedItems by viewModel.relatedPaged.items.collectAsStateWithLifecycle()
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

/** 平板布局：主内容单列（图 + 作者 + 相关），评论走通用评论页。 */
@Composable
private fun TwoPaneContent(
    modifier: Modifier,
    illust: Illust?,
    pages: List<IllustPageInfo>,
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
    )
}

