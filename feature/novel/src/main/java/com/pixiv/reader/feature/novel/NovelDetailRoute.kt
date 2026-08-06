package com.pixiv.reader.feature.novel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.ModeComment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.common.MAX_CONTENT_WIDTH_DP
import com.pixiv.reader.core.common.formatCount
import com.pixiv.reader.core.common.formatCountForNovel
import com.pixiv.reader.core.database.entity.ReadingProgressEntity
import com.pixiv.reader.core.novel.htmlToPlainText
import com.pixiv.reader.core.ui.component.EmptyBox
import com.pixiv.reader.core.ui.component.ErrorBox
import com.pixiv.reader.core.ui.component.LoadingBox
import com.pixiv.reader.core.ui.component.NotificationHost
import com.pixiv.reader.core.ui.component.PixivImage
import com.pixiv.reader.core.ui.component.UserAvatar
import com.pixiv.reader.core.ui.component.rememberNotificationHostState
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing

// ── 常量（对齐 design/novel-detail-ui.html） ─────────────────────────────────

/** 手机端 banner 高度。 */
private val NOVEL_BANNER_HEIGHT = 280.dp
/** 平板端 banner 高度（HTML 平板 360px）。 */
private val NOVEL_BANNER_TABLET_HEIGHT = 360.dp
/** banner 底部渐变高度。 */
private val NOVEL_BANNER_GRADIENT_HEIGHT = 110.dp
/** 平板判断阈值（screenWidthDp ≥ 该值走双栏布局）。 */
private const val TABLET_WIDTH_DP = 600
/** 手机端系列目录滚动区最大高度（占屏高比例，避免随分册数量增高）。 */
private const val NOVEL_TOC_MAX_HEIGHT_FRACTION = 0.4f
/** 平板左栏系列目录宽度。 */
private val NOVEL_TOC_PANEL_WIDTH = 264.dp

/**
 * 小说详情（第六十四轮完全重写，对齐 design/novel-detail-ui.html）：
 * 沉浸式封面 banner（仅作背景、无视差）+ 标题 / 作者 / 发布时间 / 统计 / 标签 / 简介（首行缩进 + 展开全文）+
 * 阅读 / 收藏 / 追更 / 下载 / 评论（竖排卡片按钮）+ 系列目录（手机限高滚动 / 平板左栏固定、滚动互不影响）+ 查看完整系列。
 * 评论区走通用页 `comments/novel/{id}`（feature:comments）。
 */
@Composable
fun NovelDetailRoute(
    novelId: Long,
    onBack: () -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenReader: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenComments: (Long) -> Unit,
    viewModel: NovelViewModel = hiltViewModel(),
) {
    val novel by viewModel.novel.collectAsStateWithLifecycle()
    val seriesNovels by viewModel.seriesNovels.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val isBookmarking by viewModel.isBookmarking.collectAsStateWithLifecycle()
    val isWatchlisted by viewModel.isWatchlisted.collectAsStateWithLifecycle()
    val isWatchlisting by viewModel.isWatchlisting.collectAsStateWithLifecycle()
    val downloading by viewModel.downloading.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    var showDownloadDialog by rememberSaveable { mutableStateOf(false) }

    val notificationHostState = rememberNotificationHostState()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.message.collect { msg ->
            notificationHostState.show(context.getString(msg.res, *msg.args.toTypedArray()))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        when {
            isLoading && novel == null -> LoadingBox()
            error != null && novel == null -> ErrorBox(
                message = error?.let { stringResource(it.res, *it.args.toTypedArray()) }.orEmpty(),
                onRetry = viewModel::load,
            )

            novel == null -> EmptyBox(stringResource(R.string.novel_not_found))
            else -> {
                val detail = checkNotNull(novel)
                NovelDetailContent(
                    detail = detail,
                    seriesNovels = seriesNovels,
                    progress = progress,
                    isBookmarked = isBookmarked,
                    isBookmarking = isBookmarking,
                    isWatchlisted = isWatchlisted,
                    isWatchlisting = isWatchlisting,
                    downloading = downloading,
                    downloadProgress = downloadProgress,
                    onBack = onBack,
                    onOpenNovel = onOpenNovel,
                    onOpenReader = onOpenReader,
                    onOpenUser = onOpenUser,
                    onOpenSeries = onOpenSeries,
                    onOpenComments = onOpenComments,
                    onBookmark = viewModel::toggleBookmark,
                    onWatchlist = viewModel::toggleWatchlist,
                    onDownload = { showDownloadDialog = true },
                )
            }
        }
        val dialogNovel = novel
        if (showDownloadDialog && dialogNovel != null) {
            DownloadDialog(
                hasSeries = dialogNovel.series?.id != null,
                onTxtCurrent = {
                    viewModel.exportNovel(NovelExportFormat.TXT)
                    showDownloadDialog = false
                },
                onEpubCurrent = {
                    viewModel.exportNovel(NovelExportFormat.EPUB)
                    showDownloadDialog = false
                },
                onTxtSeries = {
                    viewModel.exportSeries(NovelExportFormat.TXT)
                    showDownloadDialog = false
                },
                onEpubSeries = {
                    viewModel.exportSeries(NovelExportFormat.EPUB)
                    showDownloadDialog = false
                },
                onOfflineCurrent = {
                    viewModel.downloadOfflineCurrent()
                    showDownloadDialog = false
                },
                onOfflineSeries = {
                    viewModel.downloadOfflineSeries()
                    showDownloadDialog = false
                },
                onDismiss = { showDownloadDialog = false },
            )
        }
        NotificationHost(
            state = notificationHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 平板 + 手机双布局分发：平板且有系列走双栏（目录固定），否则单列。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NovelDetailContent(
    detail: Novel,
    seriesNovels: List<Novel>,
    progress: ReadingProgressEntity?,
    isBookmarked: Boolean,
    isBookmarking: Boolean,
    isWatchlisted: Boolean,
    isWatchlisting: Boolean,
    downloading: Boolean,
    downloadProgress: String?,
    onBack: () -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenReader: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenComments: (Long) -> Unit,
    onBookmark: () -> Unit,
    onWatchlist: () -> Unit,
    onDownload: () -> Unit,
) {
    val isTablet = LocalConfiguration.current.screenWidthDp >= TABLET_WIDTH_DP
    val seriesId = detail.series?.id
    if (isTablet && seriesNovels.isNotEmpty()) {
        // 平板双栏：banner 随正文滚动，左目录固定（sticky 等效），滚动互不影响
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "banner") {
                    NovelBanner(detail = detail, height = NOVEL_BANNER_TABLET_HEIGHT)
                }
                item(key = "info_actions") {
                    // 右侧正文：避开左目录（264dp + 间距）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = NOVEL_TOC_PANEL_WIDTH + Spacing.lg),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            NovelHeader(detail, onOpenUser = onOpenUser, expandableIntro = false)
                            NovelActions(
                                novel = detail,
                                progress = progress,
                                isBookmarked = isBookmarked,
                                isBookmarking = isBookmarking,
                                isWatchlisted = isWatchlisted,
                                isWatchlisting = isWatchlisting,
                                downloading = downloading,
                                downloadProgress = downloadProgress,
                                onBookmark = onBookmark,
                                onWatchlist = onWatchlist,
                                onDownload = onDownload,
                                onRead = { onOpenReader(detail.id) },
                                onComments = { onOpenComments(detail.id) },
                            )
                        }
                    }
                }
                item(key = "bottom_space") { Spacer(Modifier.height(24.dp)) }
            }
            // 左侧目录浮层：从 banner 底部开始固定（不随正文滚、不影响 banner）
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = NOVEL_BANNER_TABLET_HEIGHT, start = Spacing.lg),
            ) {
                NovelTocPanel(
                    seriesNovels = seriesNovels,
                    currentId = detail.id,
                    seriesId = seriesId,
                    onOpenNovel = onOpenNovel,
                    onOpenSeries = onOpenSeries,
                    modifier = Modifier
                        .width(NOVEL_TOC_PANEL_WIDTH)
                        .fillMaxHeight(),
                )
            }
            // 平板返回按钮：右上角
            FloatingBackButton(onBack = onBack, modifier = Modifier.align(Alignment.TopEnd))
        }
    } else {
        PhoneNovelDetail(
            detail = detail,
            seriesNovels = seriesNovels,
            progress = progress,
            isBookmarked = isBookmarked,
            isBookmarking = isBookmarking,
            isWatchlisted = isWatchlisted,
            isWatchlisting = isWatchlisting,
            downloading = downloading,
            downloadProgress = downloadProgress,
            onBack = onBack,
            onOpenNovel = onOpenNovel,
            onOpenReader = onOpenReader,
            onOpenUser = onOpenUser,
            onOpenSeries = onOpenSeries,
            onOpenComments = onOpenComments,
            onBookmark = onBookmark,
            onWatchlist = onWatchlist,
            onDownload = onDownload,
        )
    }
}

/** 手机单列详情：banner 随滚 + 标题信息 + 操作 + 系列目录（限高内部滚动）。 */
@Composable
private fun PhoneNovelDetail(
    detail: Novel,
    seriesNovels: List<Novel>,
    progress: ReadingProgressEntity?,
    isBookmarked: Boolean,
    isBookmarking: Boolean,
    isWatchlisted: Boolean,
    isWatchlisting: Boolean,
    downloading: Boolean,
    downloadProgress: String?,
    onBack: () -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenReader: (Long) -> Unit,
    onOpenUser: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    onOpenComments: (Long) -> Unit,
    onBookmark: () -> Unit,
    onWatchlist: () -> Unit,
    onDownload: () -> Unit,
) {
    val tocMaxHeight = (LocalConfiguration.current.screenHeightDp * NOVEL_TOC_MAX_HEIGHT_FRACTION).dp
    val seriesId = detail.series?.id

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "banner") {
                NovelBanner(detail = detail, height = NOVEL_BANNER_HEIGHT)
            }
            item(key = "info_actions") {
                NovelCenteredBox {
                    Column {
                        NovelHeader(detail, onOpenUser = onOpenUser, expandableIntro = true)
                        NovelActions(
                            novel = detail,
                            progress = progress,
                            isBookmarked = isBookmarked,
                            isBookmarking = isBookmarking,
                            isWatchlisted = isWatchlisted,
                            isWatchlisting = isWatchlisting,
                            downloading = downloading,
                            downloadProgress = downloadProgress,
                            onBookmark = onBookmark,
                            onWatchlist = onWatchlist,
                            onDownload = onDownload,
                            onRead = { onOpenReader(detail.id) },
                            onComments = { onOpenComments(detail.id) },
                        )
                    }
                }
            }
            // 系列目录（有系列才渲染）：限高内部滚动，不随分册数量增高
            if (seriesNovels.isNotEmpty()) {
                item(key = "series_toc") {
                    NovelCenteredBox {
                        NovelTocScroll(
                            seriesNovels = seriesNovels,
                            currentId = detail.id,
                            seriesId = seriesId,
                            onOpenNovel = onOpenNovel,
                            onOpenSeries = onOpenSeries,
                            maxHeight = tocMaxHeight,
                        )
                    }
                }
            }
            item(key = "bottom_space") { Spacer(Modifier.height(24.dp)) }
        }
        // 手机返回按钮：左上角
        FloatingBackButton(onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
    }
}

/** 沉浸式封面 banner：仅作背景（非完整展示），无视差，底部渐变过渡到 surface。 */
@Composable
private fun NovelBanner(detail: Novel, height: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        PixivImage(
            url = detail.image_urls?.medium ?: detail.image_urls?.square_medium,
            contentDescription = detail.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        // 底部渐变过渡到正文背景
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(NOVEL_BANNER_GRADIENT_HEIGHT)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            1f to MaterialTheme.colorScheme.surface,
                        ),
                    ),
                ),
        )
    }
}

/** 悬浮返回按钮（沉浸式：半透明圆底；手机左上 / 平板右上由调用方 modifier 决定）。 */
@Composable
private fun FloatingBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onBack,
        modifier = modifier
            .statusBarsPadding()
            .padding(4.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f)),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.novel_cd_back),
            tint = Color.White,
        )
    }
}

/** 标题信息卡：标题 / 作者 / 发布时间 / 统计（均分撑满整行）/ 标签 / 简介（首行缩进 + 展开全文）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NovelHeader(
    novel: Novel,
    onOpenUser: (Long) -> Unit,
    expandableIntro: Boolean,
) {
    Column(modifier = Modifier.padding(Spacing.lg)) {
        // 标题：21sp Bold（HTML 21px/700）
        Text(
            text = novel.title.orEmpty(),
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
        )
        // 作者行：头像 + 昵称 + chevron ›
        Row(
            modifier = Modifier
                .padding(top = Spacing.md)
                .clickable { novel.user?.id?.let(onOpenUser) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            UserAvatar(
                name = novel.user?.name,
                avatarUrl = novel.user?.profile_image_urls?.best(),
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = novel.user?.name.orEmpty(),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 发布时间
        val publishDate = novel.create_date?.take(10)
        if (!publishDate.isNullOrBlank()) {
            Row(
                modifier = Modifier.padding(top = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.novel_publish_date, publishDate),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        // 统计：三块均分撑满整行（icon + 值 15sp Bold + 标签 11sp）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            NovelStatBlock(
                icon = Icons.Filled.MenuBook,
                value = formatCountForNovel(novel.text_length ?: 0),
                label = stringResource(R.string.novel_stat_word),
                modifier = Modifier.weight(1f),
            )
            NovelStatBlock(
                icon = Icons.Filled.FavoriteBorder,
                value = formatCount((novel.total_bookmarks ?: 0).toLong()),
                label = stringResource(R.string.novel_stat_bookmark),
                modifier = Modifier.weight(1f),
            )
            NovelStatBlock(
                icon = Icons.Filled.Visibility,
                value = formatCount((novel.total_view ?: 0).toLong()),
                label = stringResource(R.string.novel_stat_view),
                modifier = Modifier.weight(1f),
            )
        }
        // 标签胶囊
        val tags = novel.tags.orEmpty().take(8)
        if (tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tags.forEach { tag ->
                    Text(
                        text = "#${tag.displayName ?: tag.name.orEmpty()}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(AppShapes.pill)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
        // 简介：13.5sp + 首行缩进两格；手机 6 行截断 + 展开全文，平板完整显示
        val caption = novel.caption
        if (!caption.isNullOrBlank()) {
            var expanded by rememberSaveable { mutableStateOf(false) }
            val clamped = expandableIntro && !expanded
            Text(
                text = htmlToPlainText(caption),
                fontSize = 13.5.sp,
                lineHeight = 24.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (clamped) 6 else Int.MAX_VALUE,
                overflow = if (clamped) TextOverflow.Ellipsis else TextOverflow.Clip,
                style = MaterialTheme.typography.bodyMedium.copy(
                    textIndent = TextIndent(firstLine = 2.em),
                ),
                modifier = Modifier.padding(top = 14.dp),
            )
            if (expandableIntro) {
                Text(
                    text = if (expanded) stringResource(R.string.novel_intro_collapse) else stringResource(R.string.novel_intro_expand),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clickable { expanded = !expanded },
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** 统计块：图标 + 数值 + 标签（weight(1f) 均分整行）。 */
@Composable
private fun NovelStatBlock(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 操作区：阅读主按钮（AutoStories 图标）+ 4 个竖排卡片按钮（icon 上 / label 下）。 */
@Composable
private fun NovelActions(
    novel: Novel,
    progress: ReadingProgressEntity?,
    isBookmarked: Boolean,
    isBookmarking: Boolean,
    isWatchlisted: Boolean,
    isWatchlisting: Boolean,
    downloading: Boolean,
    downloadProgress: String?,
    onBookmark: () -> Unit,
    onWatchlist: () -> Unit,
    onDownload: () -> Unit,
    onRead: () -> Unit,
    onComments: () -> Unit,
) {
    Column(modifier = Modifier.padding(Spacing.lg)) {
        val readLabel = if (progress != null && (progress.percentage ?: 0) > 0) {
            stringResource(R.string.novel_continue_reading, progress.percentage)
        } else {
            stringResource(R.string.novel_start_reading)
        }
        Button(onClick = onRead, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.Filled.AutoStories, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = readLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            VerticalActionButton(
                icon = if (isBookmarked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                label = if (isBookmarked) stringResource(R.string.novel_bookmarked) else stringResource(R.string.novel_bookmark),
                active = isBookmarked,
                enabled = !isBookmarking,
                onClick = onBookmark,
                modifier = Modifier.weight(1f),
            )
            VerticalActionButton(
                icon = if (isWatchlisted) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                label = if (isWatchlisted) stringResource(R.string.novel_watchlisted) else stringResource(R.string.novel_watch),
                active = isWatchlisted,
                enabled = !isWatchlisting && novel.series?.id != null,
                onClick = onWatchlist,
                modifier = Modifier.weight(1f),
            )
            VerticalActionButton(
                icon = Icons.Filled.Download,
                label = stringResource(R.string.novel_download),
                active = false,
                enabled = !downloading,
                onClick = onDownload,
                modifier = Modifier.weight(1f),
            )
            VerticalActionButton(
                icon = Icons.Filled.ModeComment,
                label = stringResource(R.string.novel_comment_button),
                active = false,
                enabled = true,
                onClick = onComments,
                modifier = Modifier.weight(1f),
            )
        }
        if (downloading && !downloadProgress.isNullOrBlank()) {
            Text(
                text = downloadProgress,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** 竖排卡片按钮（HTML `.abtn`）：icon 上 / label 下，52dp 高，圆角 12，激活态 primaryContainer。 */
@Composable
private fun VerticalActionButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(52.dp)
            .clip(AppShapes.card)
            .border(
                width = 1.dp,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = AppShapes.card,
            )
            .background(
                if (active) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** 系列目录行（HTML `.trow`）：序号徽标 + 标题 + 字数/收藏 + 当前章胶囊。 */
@Composable
private fun ChapterRow(
    novel: Novel,
    index: Int,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 序号徽标（HTML `.tidx`：28dp、圆角 9、当前章主色）
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(
                    if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondaryContainer,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (index + 1).toString().padStart(2, '0'),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCurrent) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Text(
                text = novel.title.orEmpty(),
                fontSize = 13.sp,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.novel_chapter_word, formatCountForNovel(novel.text_length ?: 0)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.novel_chapter_bookmark, formatCount((novel.total_bookmarks ?: 0).toLong())),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isCurrent) {
            Text(
                text = stringResource(R.string.novel_chapter_current),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(AppShapes.pill)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** 系列目录标题（HTML `.sectitle`）：MenuBook 图标 + 15sp Bold + 数量胶囊。 */
@Composable
private fun TocTitle(count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.novel_toc_section, count),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 7.dp),
        )
    }
}

/** 系列目录（手机端单列）：标题 + 限高内部滚动列表 + 查看完整系列（不随分册数量增高）。 */
@Composable
private fun NovelTocScroll(
    seriesNovels: List<Novel>,
    currentId: Long,
    seriesId: Long?,
    onOpenNovel: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        TocTitle(
            count = seriesNovels.size,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .clip(AppShapes.card)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShapes.card)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .verticalScroll(rememberScrollState()),
        ) {
            seriesNovels.forEachIndexed { index, chapter ->
                ChapterRow(
                    novel = chapter,
                    index = index,
                    isCurrent = chapter.id == currentId,
                    onClick = { onOpenNovel(chapter.id) },
                )
            }
        }
        SeriesMoreRow(seriesId, onOpenSeries)
    }
}

/** 系列目录（平板左栏卡片）：固定于 banner 下方（sticky 等效），列表内部滚动。 */
@Composable
private fun NovelTocPanel(
    seriesNovels: List<Novel>,
    currentId: Long,
    seriesId: Long?,
    onOpenNovel: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(AppShapes.card)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, AppShapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = 4.dp),
    ) {
        TocTitle(
            count = seriesNovels.size,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            seriesNovels.forEachIndexed { index, chapter ->
                ChapterRow(
                    novel = chapter,
                    index = index,
                    isCurrent = chapter.id == currentId,
                    onClick = { onOpenNovel(chapter.id) },
                )
            }
        }
        SeriesMoreRow(seriesId, onOpenSeries)
    }
}

/** 「查看完整系列 ›」行（HTML `.tocmore`，无系列 id 时不渲染）。 */
@Composable
private fun SeriesMoreRow(
    seriesId: Long?,
    onOpenSeries: (Long) -> Unit,
) {
    if (seriesId == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenSeries(seriesId) }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.novel_series_view_all),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 平板适配：详情正文内容限宽居中（banner 保持全宽沉浸）。 */
@Composable
private fun NovelCenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = MAX_CONTENT_WIDTH_DP.dp),
        ) {
            content()
        }
    }
}

// ── 下载对话框（原样迁移） ───────────────────────────────────────────────────

/** 下载选择对话框：导出文件（TXT/EPUB）+ 离线阅读（缓存到应用）。 */
@Composable
private fun DownloadDialog(
    hasSeries: Boolean,
    onTxtCurrent: () -> Unit,
    onEpubCurrent: () -> Unit,
    onTxtSeries: () -> Unit,
    onEpubSeries: () -> Unit,
    onOfflineCurrent: () -> Unit,
    onOfflineSeries: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.novel_download_title)) },
        text = {
            Column {
                DialogGroupTitle(stringResource(R.string.novel_download_group_export))
                DownloadOption(
                    title = stringResource(R.string.novel_download_txt_current),
                    subtitle = stringResource(R.string.novel_download_txt_current_desc),
                    onClick = onTxtCurrent,
                )
                DownloadOption(
                    title = stringResource(R.string.novel_download_epub_current),
                    subtitle = stringResource(R.string.novel_download_epub_current_desc),
                    onClick = onEpubCurrent,
                )
                if (hasSeries) {
                    DownloadOption(
                        title = stringResource(R.string.novel_download_txt_series),
                        subtitle = stringResource(R.string.novel_download_txt_series_desc),
                        onClick = onTxtSeries,
                    )
                    DownloadOption(
                        title = stringResource(R.string.novel_download_epub_series),
                        subtitle = stringResource(R.string.novel_download_epub_series_desc),
                        onClick = onEpubSeries,
                    )
                }
                DialogGroupTitle(stringResource(R.string.novel_download_group_offline))
                DownloadOption(
                    title = stringResource(R.string.novel_download_offline_current),
                    subtitle = stringResource(R.string.novel_download_offline_current_desc),
                    onClick = onOfflineCurrent,
                )
                if (hasSeries) {
                    DownloadOption(
                        title = stringResource(R.string.novel_download_offline_series),
                        subtitle = stringResource(R.string.novel_download_offline_series_desc),
                        onClick = onOfflineSeries,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}

@Composable
private fun DialogGroupTitle(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun DownloadOption(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
