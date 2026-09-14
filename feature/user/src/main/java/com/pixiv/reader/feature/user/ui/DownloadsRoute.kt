package com.pixiv.reader.feature.user.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pixiv.reader.feature.user.state.DownloadFilter
import com.pixiv.reader.feature.user.state.DownloadsViewModel
import com.pixiv.reader.feature.user.R
import com.pixiv.reader.feature.user.data.restoreIllust
import com.pixiv.reader.feature.user.data.restoreNovelCardData
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.database.entity.DownloadStatus
import com.pixiv.reader.core.database.entity.ExportFormat
import com.pixiv.reader.core.database.entity.ExportOpenMethod
import com.pixiv.reader.core.ui.component.card.DownloadBadge
import com.pixiv.reader.core.ui.component.layout.AdaptiveContentBox
import com.pixiv.reader.core.ui.component.layout.BackTopAppBar
import com.pixiv.reader.core.ui.component.layout.SegmentedPager
import com.pixiv.reader.core.ui.component.input.ConfirmDialog
import com.pixiv.reader.core.ui.component.feedback.EmptyBox
import com.pixiv.reader.core.ui.component.card.IllustCard
import com.pixiv.reader.core.ui.component.card.NovelCard
import com.google.gson.Gson
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes
import java.io.File

/**
 * 下载管理：分段控件（作品 / 小说）+ HorizontalPager 滑动切换。
 * 作品（插画/漫画/动图）用 `IllustCard`（宽高完整显示）、小说用 `NovelCard`；每项右上角删除按钮。
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
    val context = LocalContext.current
    // 待删除确认的下载条目（非 null 时弹出确认框）
    var pendingDelete by remember { mutableStateOf<DownloadEntryEntity?>(null) }

    Scaffold(
        topBar = {
            BackTopAppBar(title = stringResource(R.string.downloads_title), onBack = onBack)
        },
        modifier = Modifier.fillMaxSize(),
    ) { padding ->
        AdaptiveContentBox(modifier = Modifier.padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 类型分段（作品 / 小说）+ 滑动切换：SegmentedPager 双向同步
                // （选中态跟 Pager 落页，点击反向滚页；落页回调 VM 同步筛选）
                SegmentedPager(
                    tabs = DownloadFilter.entries,
                    onSelect = viewModel::selectFilter,
                    tabLabel = { it.labelRes },
                    pageContent = { _, tab ->
                        when (tab) {
                            DownloadFilter.ILLUST -> IllustDownloadList(
                                // 插画 + 动图（ugoira MP4/ZIP 导出）共用瀑布流卡片
                                entries = entries.filter { it.targetType == "illust" || it.targetType == "ugoira" },
                                onOpenIllust = onOpenIllust,
                                onOpenFile = { entry -> openWithSystemApp(context, entry) },
                                onRetry = onRetry,
                                onDelete = { pendingDelete = it },
                            )
                            DownloadFilter.NOVEL -> NovelDownloadList(
                                entries = entries.filter { it.targetType == "novel" },
                                context = context,
                                onOpen = { entry ->
                                    // 打开方式由导出格式内聚（IN_APP=App 内解析阅读，SYSTEM=系统应用）
                                    when (ExportFormat.from(entry.format)?.openMethod) {
                                        ExportOpenMethod.IN_APP ->
                                            viewModel.openLocal(entry) { onOpenLocalReader(entry.targetId) }
                                        ExportOpenMethod.SYSTEM -> openWithSystemApp(context, entry)
                                        else -> onOpenNovel(entry.targetId)
                                    }
                                },
                                onRetry = onRetry,
                                onDelete = { pendingDelete = it },
                            )
                        }
                    },
                )
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

/**
 * 插画 / 动图下载瀑布流（[IllustCard]）。
 * 动图（targetType=ugoira）已完成条目点击直接用系统应用打开产物（MP4 播放 / ZIP 解压分享），
 * 未完成 / 插画条目点击进入作品详情。
 */
@Composable
private fun IllustDownloadList(
    entries: List<DownloadEntryEntity>,
    onOpenIllust: (Long) -> Unit,
    onOpenFile: (DownloadEntryEntity) -> Unit,
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
            Column {
                Box {
                    IllustCard(
                        illust = restoreIllust(
                            entry.payloadJson,
                            entry.targetId,
                            entry.title,
                            entry.coverUrl,
                            entry.width,
                            entry.height,
                        ),
                        onClick = {
                            if (entry.targetType == "ugoira" && entry.status == DownloadEntryEntity.STATUS_DONE) onOpenFile(entry)
                            else onOpenIllust(entry.targetId)
                        },
                        // 下载中/失败：标题栏显示进度条（failed 停住最后进度 + 红色标记）；待同步不显示进度；done 恢复标题
                        download = when (DownloadStatus.from(entry.status)) {
                            DownloadStatus.DOWNLOADING -> DownloadBadge.Running(entry.progress.coerceIn(0, 100) / 100f)
                            DownloadStatus.FAILED -> DownloadBadge.Failed
                            else -> DownloadBadge.None
                        },
                    )
                    if (entry.status == DownloadEntryEntity.STATUS_FAILED) {
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
                // 待同步：卡片下方排队提示（断网排队，联网自动开始）
                if (entry.status == DownloadEntryEntity.STATUS_PENDING) {
                    Text(
                        text = stringResource(R.string.downloads_status_pending),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.xsPlus),
                    )
                }
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
                    val card = restoreNovelCardData(
                        entry.payloadJson,
                        Gson(),
                        entry.targetId,
                        entry.title ?: context.getString(R.string.untitled),
                        entry.coverUrl,
                        // 快照缺键回填实体旧字段（旧条目 payloadJson 为 null 时的结构字段快照）
                        fallbackAuthorName = entry.authorName,
                        fallbackAuthorAvatarUrl = entry.authorAvatarUrl,
                        fallbackPublishDate = entry.publishDate,
                        fallbackSeriesTitle = entry.seriesTitle,
                        fallbackSeriesId = entry.seriesId,
                        fallbackFavoriteCount = entry.favoriteCount,
                        fallbackWordCount = entry.wordCount,
                    )
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
                    if (entry.status == DownloadEntryEntity.STATUS_FAILED) {
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

/** 下载状态行：downloading 显示进度条 + 百分比；待同步显示排队提示；failed 显示红色失败标记；done 不显示。 */
@Composable
private fun DownloadStatusRow(
    entry: DownloadEntryEntity,
    modifier: Modifier = Modifier,
) {
    when (DownloadStatus.from(entry.status)) {
        DownloadStatus.PENDING -> Text(
            text = stringResource(R.string.downloads_status_pending),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(top = Spacing.xs),
        )
        DownloadStatus.DOWNLOADING -> Column(modifier = modifier.fillMaxWidth().padding(top = Spacing.xsPlus)) {
            LinearWavyProgressIndicator(
                progress = { entry.progress.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.downloads_downloading, entry.progress.coerceIn(0, 100)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
        DownloadStatus.FAILED -> Text(
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
    OverlayCircleButton(
        icon = Icons.Filled.Close,
        contentDescription = stringResource(R.string.cd_delete),
        onClick = onDelete,
        modifier = modifier,
    )
}

/** 右上角圆形重试按钮（failed 条目点击重新触发下载，断点续传）。 */
@Composable
private fun RetryOverlay(
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
) {
    OverlayCircleButton(
        icon = Icons.Filled.Refresh,
        contentDescription = stringResource(R.string.downloads_retry),
        onClick = onRetry,
        modifier = modifier,
    )
}

/**
 * 封面角落圆形浮钮（28dp 半透明黑底白图标，下载卡专用）。
 * 保持 28dp 小尺寸（FilledTonalIconButton 最小 40dp 会撑破封面角标布局），
 * 按压时 spring 微缩提供 Expressive 触感。
 *
 * @param icon 图标
 * @param contentDescription 无障碍描述
 * @param onClick 点击回调
 * @param modifier 外部传入的 Modifier（定位用）
 * @return 无返回值
 */
@Composable
private fun OverlayCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "overlayPressScale",
    )
    IconButton(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(Sizes.s28)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f)),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(14.dp),
        )
    }
}

// ── 数据转换（见 SnapshotRestore.kt） ────────────────────────────────────────

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

private fun formatInfo(format: String): FormatInfo? = when (ExportFormat.from(format)) {
    ExportFormat.TXT -> FormatInfo(Icons.Filled.Description, R.string.downloads_format_txt)
    ExportFormat.EPUB -> FormatInfo(Icons.AutoMirrored.Filled.MenuBook, R.string.downloads_format_epub)
    ExportFormat.PDF -> FormatInfo(Icons.Filled.PictureAsPdf, R.string.downloads_format_pdf)
    ExportFormat.MARKDOWN -> FormatInfo(Icons.AutoMirrored.Filled.Notes, R.string.downloads_format_markdown)
    ExportFormat.DOCX -> FormatInfo(Icons.AutoMirrored.Filled.Article, R.string.downloads_format_docx)
    ExportFormat.MP4 -> FormatInfo(Icons.Filled.Videocam, R.string.downloads_format_mp4)
    ExportFormat.ZIP -> FormatInfo(Icons.Filled.FolderZip, R.string.downloads_format_zip)
    null -> null
}

/** 通过 ACTION_VIEW 交给系统应用打开 pdf/docx/MP4/ZIP（SAF/MediaStore content uri 直传 / 私有路径走 FileProvider；找不到应用时静默失败）。 */
private fun openWithSystemApp(context: Context, entry: DownloadEntryEntity) {
    val path = entry.localPath ?: return
    // MediaStore uri（content://media/...）不含文件名，mime 用索引 format 字段推断
    val mime = ExportFormat.from(entry.format)?.mime
        ?: MimeTypeMap.getSingleton()
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
