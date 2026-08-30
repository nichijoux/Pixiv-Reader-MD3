package com.pixiv.reader.feature.user.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.feature.user.state.DownloadFilter
import com.pixiv.reader.feature.user.state.DownloadsViewModel
import com.pixiv.reader.feature.user.R
import com.pixiv.api.model.ImageUrls
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.input.ConfirmDialog
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.card.IllustCard
import com.pixiv.reader.core.ui.component.card.NovelCard
import com.pixiv.reader.core.ui.component.card.NovelCardData
import com.google.gson.Gson
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes
import java.io.File
import kotlinx.coroutines.launch

/**
 * 下载管理：TabRow（插画/小说）+ HorizontalPager 滑动切换。
 * 插画用 `IllustCard`（宽高完整显示）、小说用 `NovelCard`；每项右上角删除按钮。
 * 小说本地文件点击：txt/epub/md → 解析本地文件本地阅读；pdf/docx → 系统应用打开。
 *
 * @param onBack 返回
 * @param onOpenIllust 打开插画详情
 * @param onOpenNovel 打开小说详情
 * @param onOpenLocalReader 打开本地文件阅读（local_reader）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsRoute(
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenLocalReader: (Long) -> Unit,
    onRetry: (DownloadEntryEntity) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val filter by viewModel.filterFlow.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { DownloadFilter.entries.size })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // 待删除确认的下载条目（非 null 时弹出确认框）
    var pendingDelete by remember { mutableStateOf<DownloadEntryEntity?>(null) }

    // 滑动切页 → 同步筛选
    LaunchedEffect(pagerState.currentPage) {
        val page = pagerState.currentPage
        if (page in DownloadFilter.entries.indices) {
            viewModel.selectFilter(DownloadFilter.entries[page])
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads_title)) },
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
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        AdaptiveContentBox(modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(
                    selectedTabIndex = filter.ordinal.coerceAtMost(DownloadFilter.entries.size - 1),
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    DownloadFilter.entries.forEachIndexed { index, f ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(stringResource(f.labelRes)) },
                        )
                    }
                }
                HorizontalPager(state = pagerState) { page ->
                    when (DownloadFilter.entries.getOrNull(page)) {
                        DownloadFilter.ILLUST -> IllustDownloadList(
                            entries = entries.filter { it.targetType == "illust" },
                            onOpenIllust = onOpenIllust,
                            onRetry = onRetry,
                            onDelete = { pendingDelete = it },
                        )
                        DownloadFilter.NOVEL -> NovelDownloadList(
                            entries = entries.filter { it.targetType == "novel" },
                            context = context,
                            onOpen = { entry ->
                                when {
                                    isParsableLocalFile(entry) ->
                                        viewModel.openLocal(entry) { onOpenLocalReader(entry.targetId) }
                                    isSystemOpenFile(entry) -> openWithSystemApp(context, entry)
                                    else -> onOpenNovel(entry.targetId)
                                }
                            },
                            onRetry = onRetry,
                            onDelete = { pendingDelete = it },
                        )
                        null -> {}
                    }
                }
            }
        }
    }

    // 删除下载确认（删除文件 + 索引，不可撤销）
    pendingDelete?.let { entry ->
        ConfirmDialog(
            title = stringResource(R.string.downloads_delete_title),
            message = stringResource(R.string.downloads_delete_message, entry.title.orEmpty()),
            confirmText = stringResource(com.pixiv.reader.core.ui.R.string.common_delete),
            onConfirm = {
                viewModel.delete(entry)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

// ── 插画：IllustCard 瀑布流 ─────────────────────────────────────────────────

@Composable
private fun IllustDownloadList(
    entries: List<DownloadEntryEntity>,
    onOpenIllust: (Long) -> Unit,
    onRetry: (DownloadEntryEntity) -> Unit,
    onDelete: (DownloadEntryEntity) -> Unit,
) {
    if (entries.isEmpty()) {
        EmptyBox(stringResource(R.string.downloads_empty_illust))
        return
    }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(140.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalItemSpacing = 8.dp,
    ) {
        items(entries, key = { "${it.targetType}_${it.targetId}_${it.format}" }) { entry ->
            Box {
                IllustCard(
                    illust = entry.toDownloadIllust(),
                    onClick = { onOpenIllust(entry.targetId) },
                    // 下载中/失败：标题栏显示进度条（failed 停住最后进度 + 红色标记）；done 恢复标题
                    progress = if (entry.status == "done") null else entry.progress.coerceIn(0, 100) / 100f,
                    failed = entry.status == "failed",
                )
                if (entry.status == "failed") {
                    RetryOverlay(
                        modifier = Modifier.align(Alignment.TopStart).padding(Spacing.xs),
                        onRetry = { onRetry(entry) },
                    )
                }
                DeleteOverlay(
                    modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xs),
                    onDelete = { onDelete(entry) },
                )
            }
        }
    }
}

// ── 小说 / 离线：NovelCard ───────────────────────────────────────────────────

@Composable
private fun NovelDownloadList(
    entries: List<DownloadEntryEntity>,
    context: Context,
    onOpen: (DownloadEntryEntity) -> Unit,
    onRetry: (DownloadEntryEntity) -> Unit,
    onDelete: (DownloadEntryEntity) -> Unit,
) {
    if (entries.isEmpty()) {
        EmptyBox(stringResource(R.string.downloads_empty))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.smPlus),
    ) {
        items(entries, key = { "${it.targetType}_${it.targetId}_${it.format}_${it.scopeKey}" }) { entry ->
            Column {
                Box {
                    val card = entry.toDownloadNovelCard(context)
                    NovelCard(
                        novel = card,
                        onClick = { onOpen(entry) },
                        onOpenAuthor = {},
                        onToggleFavorite = {},
                        onTagClick = {},
                        // 下载卡片：隐藏封面角标收藏数（字数保留），封面右上角展示下载类型胶囊
                        showFavoriteCount = false,
                        coverBadge = { DownloadFormatBadge(entry.format) },
                    )
                    if (entry.status == "failed") {
                        RetryOverlay(
                            modifier = Modifier.align(Alignment.TopStart).padding(Spacing.xs),
                            onRetry = { onRetry(entry) },
                        )
                    }
                    DeleteOverlay(
                        modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xs),
                        onDelete = { onDelete(entry) },
                    )
                }
                // 下载中 / 失败状态行（done 不显示）
                DownloadStatusRow(entry)
            }
        }
    }
}

/** 下载状态行：downloading 显示进度条 + 百分比；failed 显示红色失败标记；done 不显示。 */
@Composable
private fun DownloadStatusRow(
    entry: DownloadEntryEntity,
    modifier: Modifier = Modifier,
) {
    when (entry.status) {
        "downloading" -> Column(modifier = modifier.fillMaxWidth().padding(top = Spacing.xsPlus)) {
            LinearProgressIndicator(
                progress = { entry.progress.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.downloads_downloading, entry.progress.coerceIn(0, 100)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        "failed" -> Text(
            text = stringResource(R.string.downloads_failed),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier.padding(top = Spacing.xs),
        )
        else -> {}
    }
}

/** 右上角圆形删除按钮。 */
@Composable
private fun DeleteOverlay(
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
) {
    IconButton(
        onClick = onDelete,
        modifier = modifier
            .size(Sizes.s28)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f)),
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = stringResource(R.string.cd_delete),
            tint = Color.White,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** 右上角圆形重试按钮（failed 条目点击重新触发下载，断点续传）。 */
@Composable
private fun RetryOverlay(
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
) {
    IconButton(
        onClick = onRetry,
        modifier = modifier
            .size(Sizes.s28)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f)),
    ) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = stringResource(R.string.downloads_retry),
            tint = Color.White,
            modifier = Modifier.size(14.dp),
        )
    }
}

// ── 数据转换 ────────────────────────────────────────────────────────────────

private fun DownloadEntryEntity.toDownloadIllust(): Illust {
    // 优先解析完整 payloadJson（含宽高，避免固定高度裁剪中间）；旧条目回退结构字段
    val parsed = payloadJson?.let {
        runCatching { org.json.JSONObject(it) }.getOrNull()
    }
    if (parsed != null) {
        return Illust(
            id = parsed.optLong("id", targetId),
            title = parsed.optString("title").ifEmpty { title.orEmpty() },
            image_urls = ImageUrls(medium = parsed.optString("coverUrl").ifEmpty { coverUrl.orEmpty() }),
            width = parsed.optInt("width") ?: 0,
            height = parsed.optInt("height") ?: 0,
            total_bookmarks = parsed.optInt("bookmarks").takeIf { it != 0 },
            page_count = parsed.optInt("pageCount") ?: 0,
            is_bookmarked = if (parsed.has("isBookmarked")) parsed.optBoolean("isBookmarked") else null,
        )
    }
    return Illust(
        id = targetId,
        title = title,
        image_urls = ImageUrls(medium = coverUrl),
        width = width,
        height = height,
    )
}

private fun DownloadEntryEntity.toDownloadNovelCard(context: Context): NovelCardData {
    // 优先解析完整 payloadJson（新记录）；旧条目/失败回退结构字段。
    // Gson 对 Kotlin data class 用 UnsafeAllocator 绕过构造器：JSON 缺失的非空字段
    // 会被置为 null 且不抛异常——必须字段级补默认值，否则 NovelCard 渲染 NPE 闪退
    val parsed = payloadJson?.let {
        runCatching { Gson().fromJson(it, NovelCardData::class.java) }.getOrNull()
    }
    if (parsed != null) {
        return NovelCardData(
            id = if (parsed.id != 0L) parsed.id else targetId,
            title = parsed.title?.takeIf { it.isNotBlank() }
                ?: (title ?: context.getString(R.string.untitled)),
            coverUrl = parsed.coverUrl ?: coverUrl,
            authorId = parsed.authorId,
            authorName = parsed.authorName ?: authorName ?: "",
            authorAvatarUrl = parsed.authorAvatarUrl ?: authorAvatarUrl,
            publishDate = parsed.publishDate ?: publishDate,
            seriesTitle = parsed.seriesTitle ?: seriesTitle,
            seriesId = parsed.seriesId ?: seriesId,
            favoriteCount = parsed.favoriteCount,
            wordCount = parsed.wordCount,
            tags = parsed.tags.orEmpty(),
            isFavorite = parsed.isFavorite,
        )
    }
    return NovelCardData(
        id = targetId,
        title = title ?: context.getString(R.string.untitled),
        coverUrl = coverUrl,
        authorId = 0,
        authorName = authorName ?: "",
        authorAvatarUrl = authorAvatarUrl,
        publishDate = publishDate,
        seriesTitle = seriesTitle,
        seriesId = seriesId,
        favoriteCount = favoriteCount,
        wordCount = wordCount,
    )
}

/** 下载类型胶囊（封面右上角浮层）：图标 + 格式文字，深色半透明底 + 白色内容（浅色封面上可读）。 */
@Composable
private fun DownloadFormatBadge(format: String) {
    val info = formatInfo(format) ?: return
    Row(
        modifier = Modifier
            .clip(AppShapes.small)
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = info.icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringResource(info.labelRes),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

/** 导出格式 → 图标 + 文字（与详情页下载弹窗一致）。 */
private data class FormatInfo(
    val icon: ImageVector,
    val labelRes: Int,
)

private fun formatInfo(format: String): FormatInfo? = when (format) {
    DownloadEntryEntity.FORMAT_TXT -> FormatInfo(Icons.Filled.Description, R.string.downloads_format_txt)
    DownloadEntryEntity.FORMAT_EPUB -> FormatInfo(Icons.Filled.MenuBook, R.string.downloads_format_epub)
    DownloadEntryEntity.FORMAT_PDF -> FormatInfo(Icons.Filled.PictureAsPdf, R.string.downloads_format_pdf)
    DownloadEntryEntity.FORMAT_MARKDOWN -> FormatInfo(Icons.Filled.Notes, R.string.downloads_format_markdown)
    DownloadEntryEntity.FORMAT_DOCX -> FormatInfo(Icons.Filled.Article, R.string.downloads_format_docx)
    else -> null
}

/** 应用内可解析阅读的本地文件格式（txt/epub/md）。 */
private fun isParsableLocalFile(entry: DownloadEntryEntity): Boolean {
    // MediaStore uri（content://media/...）不含文件名，不能靠扩展名判断，用索引 format 字段
    return entry.format == DownloadEntryEntity.FORMAT_TXT || entry.format == DownloadEntryEntity.FORMAT_EPUB || entry.format == DownloadEntryEntity.FORMAT_MARKDOWN
}

/** 需系统应用打开的本地文件格式（pdf/docx）。 */
private fun isSystemOpenFile(entry: DownloadEntryEntity): Boolean {
    return entry.format == DownloadEntryEntity.FORMAT_PDF || entry.format == DownloadEntryEntity.FORMAT_DOCX
}

/** 通过 ACTION_VIEW 交给系统应用打开 pdf/docx（SAF/MediaStore content uri 直传 / 私有路径走 FileProvider；找不到应用时静默失败）。 */
private fun openWithSystemApp(context: Context, entry: DownloadEntryEntity) {
    val path = entry.localPath ?: return
    // MediaStore uri（content://media/...）不含文件名，mime 用索引 format 字段推断
    val mime = when (entry.format) {
        DownloadEntryEntity.FORMAT_PDF -> "application/pdf"
        DownloadEntryEntity.FORMAT_DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        else -> MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(path.substringAfterLast('.', "").lowercase()) ?: "*/*"
    }
    val intent = Intent(Intent.ACTION_VIEW)
    if (path.startsWith("content://")) {
        intent.setDataAndType(Uri.parse(path), mime)
    } else {
        val file = File(path)
        if (!file.exists()) return
        intent.setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file), mime)
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(intent) }
        .onFailure { /* 无可用应用：静默忽略 */ }
}
