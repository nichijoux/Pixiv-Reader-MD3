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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.pixiv.reader.core.ui.component.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.ConfirmDialog
import com.pixiv.reader.core.ui.component.EmptyBox
import com.pixiv.reader.core.ui.component.IllustCard
import com.pixiv.reader.core.ui.component.NovelCard
import com.pixiv.reader.core.ui.component.NovelCardData
import com.pixiv.reader.core.ui.theme.AppShapes
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
 * @param onOpenCover 打开小说封面全屏大图
 * @param onOpenLocalReader 打开本地文件阅读（local_reader）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsRoute(
    onBack: () -> Unit,
    onOpenIllust: (Long) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onOpenCover: (String) -> Unit,
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
                            onOpenCover = onOpenCover,
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
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
    ) {
        items(entries, key = { it.targetId }) { entry ->
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
                        modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                        onRetry = { onRetry(entry) },
                    )
                }
                DeleteOverlay(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
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
    onOpenCover: (String) -> Unit,
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(entries, key = { it.targetId }) { entry ->
            Column {
                Box {
                    val card = entry.toDownloadNovelCard(context)
                    NovelCard(
                        novel = card,
                        onClick = { onOpen(entry) },
                        onOpenCover = { card.coverUrl?.let(onOpenCover) },
                        onOpenAuthor = {},
                        onToggleFavorite = {},
                        onTagClick = {},
                        // 下载卡片：隐藏封面角标收藏数（字数保留），封面右上角展示下载类型胶囊
                        showFavoriteCount = false,
                        coverBadge = { DownloadFormatBadge(entry.format) },
                    )
                    if (entry.status == "failed") {
                        RetryOverlay(
                            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                            onRetry = { onRetry(entry) },
                        )
                    }
                    DeleteOverlay(
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
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
        "downloading" -> Column(modifier = modifier.fillMaxWidth().padding(top = 6.dp)) {
            LinearProgressIndicator(
                progress = { entry.progress.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.downloads_downloading, entry.progress.coerceIn(0, 100)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        "failed" -> Text(
            text = stringResource(R.string.downloads_failed),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier.padding(top = 4.dp),
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
            .size(28.dp)
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
            .size(28.dp)
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

private fun DownloadEntryEntity.toDownloadIllust(): Illust = Illust(
    id = targetId,
    title = title,
    image_urls = ImageUrls(medium = coverUrl),
    width = width,
    height = height,
)

private fun DownloadEntryEntity.toDownloadNovelCard(context: Context): NovelCardData = NovelCardData(
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

/** 下载类型胶囊（封面右上角浮层）：图标 + 格式文字，深色半透明底 + 白色内容（浅色封面上可读）。 */
@Composable
private fun DownloadFormatBadge(format: String) {
    val info = formatInfo(format) ?: return
    Row(
        modifier = Modifier
            .clip(AppShapes.small)
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
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
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** 导出格式 → 图标 + 文字（与详情页下载弹窗一致）。 */
private data class FormatInfo(
    val icon: ImageVector,
    val labelRes: Int,
)

private fun formatInfo(format: String): FormatInfo? = when (format) {
    "TXT" -> FormatInfo(Icons.Filled.Description, R.string.downloads_format_txt)
    "EPUB" -> FormatInfo(Icons.Filled.MenuBook, R.string.downloads_format_epub)
    "PDF" -> FormatInfo(Icons.Filled.PictureAsPdf, R.string.downloads_format_pdf)
    "MARKDOWN" -> FormatInfo(Icons.Filled.Notes, R.string.downloads_format_markdown)
    "DOCX" -> FormatInfo(Icons.Filled.Article, R.string.downloads_format_docx)
    else -> null
}

/** 应用内可解析阅读的本地文件扩展名（txt/epub/md）。 */
private fun isParsableLocalFile(entry: DownloadEntryEntity): Boolean {
    val ext = entry.localPath?.substringAfterLast('.', "")?.lowercase() ?: return false
    return ext == "txt" || ext == "epub" || ext == "md"
}

/** 需系统应用打开的本地文件扩展名（pdf/docx）。 */
private fun isSystemOpenFile(entry: DownloadEntryEntity): Boolean {
    val ext = entry.localPath?.substringAfterLast('.', "")?.lowercase() ?: return false
    return ext == "pdf" || ext == "docx"
}

/** 通过 ACTION_VIEW 交给系统应用打开 pdf/docx（SAF content uri 直传 / 私有路径走 FileProvider；找不到应用时静默失败）。 */
private fun openWithSystemApp(context: Context, entry: DownloadEntryEntity) {
    val path = entry.localPath ?: return
    val mime = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(path.substringAfterLast('.', "").lowercase()) ?: "*/*"
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
