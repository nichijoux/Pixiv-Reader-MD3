package com.pixiv.reader.feature.novel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.common.format.formatCountForNovel
import com.pixiv.reader.core.ui.component.input.PillSegmentedRow
import com.pixiv.reader.feature.novel.R
import com.pixiv.reader.feature.novel.data.NovelExportFormat
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Sizes

// ── 下载底部弹窗 ─────────────────────────────────────────────────────────────

enum class NovelDownloadScope { SINGLE, SERIES, PARTIAL }

/**
 * 范围标签与默认选中由此决定，与分册数据是否已加载解耦：Detail「单个文件 / 整个系列」（[hasSeries]
 * 控制是否显示切换），Series「整个系列 / 选取部分」，后者 partialChapters 异步到达不影响标签与默认值。
 */
sealed interface DownloadSheetConfig {
    data class Detail(val hasSeries: Boolean) : DownloadSheetConfig
    data class Series(val partialChapters: List<Novel>) : DownloadSheetConfig
}

/** 限高内部滚动，避免章节过多把导出格式挤出屏幕。 */
private val PARTIAL_LIST_MAX_HEIGHT = 280.dp

private data class DownloadFormatInfo(
    val format: NovelExportFormat,
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
)

private val DOWNLOAD_FORMATS = listOf(
    DownloadFormatInfo(
        NovelExportFormat.TXT,
        R.string.novel_download_txt_current,
        R.string.novel_download_txt_current_desc,
        Icons.Filled.Description
    ),
    DownloadFormatInfo(
        NovelExportFormat.EPUB,
        R.string.novel_download_epub_current,
        R.string.novel_download_epub_current_desc,
        Icons.AutoMirrored.Filled.MenuBook
    ),
    DownloadFormatInfo(
        NovelExportFormat.PDF,
        R.string.novel_download_pdf_current,
        R.string.novel_download_pdf_current_desc,
        Icons.Filled.PictureAsPdf
    ),
    DownloadFormatInfo(
        NovelExportFormat.MARKDOWN,
        R.string.novel_download_markdown_current,
        R.string.novel_download_markdown_current_desc,
        Icons.AutoMirrored.Filled.Notes
    ),
    DownloadFormatInfo(
        NovelExportFormat.DOCX,
        R.string.novel_download_docx_current,
        R.string.novel_download_docx_current_desc,
        Icons.AutoMirrored.Filled.Article
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadSheet(
    config: DownloadSheetConfig,
    onFormat: (NovelExportFormat, NovelDownloadScope, List<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isSeriesPage = config is DownloadSheetConfig.Series
    val partialChapters = (config as? DownloadSheetConfig.Series)?.partialChapters.orEmpty()
    // 默认值由页面类型决定，与分册数据加载状态无关
    val defaultScope = if (isSeriesPage) NovelDownloadScope.SERIES else NovelDownloadScope.SINGLE
    var scope by rememberSaveable { mutableStateOf(defaultScope) }
    var selectedIds by rememberSaveable(partialChapters) {
        mutableStateOf(partialChapters.map { it.id }.toSet())
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = Spacing.lg),
        ) {
            Text(
                text = stringResource(R.string.novel_download_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.lgPlus, vertical = Spacing.xs),
            )
            if (isSeriesPage || (config as? DownloadSheetConfig.Detail)?.hasSeries == true) {
                // 系列页「整个系列 / 选取部分」；详情页「单个文件 / 整个系列」
                val options = if (isSeriesPage) {
                    listOf(
                        NovelDownloadScope.SERIES to R.string.novel_download_scope_series,
                        NovelDownloadScope.PARTIAL to R.string.novel_download_scope_partial,
                    )
                } else {
                    listOf(
                        NovelDownloadScope.SINGLE to R.string.novel_download_scope_single,
                        NovelDownloadScope.SERIES to R.string.novel_download_scope_series,
                    )
                }
                PillSegmentedRow(
                    options = options,
                    selected = scope,
                    onSelect = { scope = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lgPlus, vertical = Spacing.xs),
                )
            }
            // 限高可滚动，避免章节太多把下方导出格式挤出屏幕
            if (isSeriesPage && scope == NovelDownloadScope.PARTIAL) {
                Text(
                    text = stringResource(
                        R.string.novel_download_select_hint,
                        selectedIds.size,
                        partialChapters.size
                    ),
                    style = novelMetaStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lgPlus, vertical = Spacing.xs),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PARTIAL_LIST_MAX_HEIGHT),
                ) {
                    items(partialChapters, key = { it.id }) { novel ->
                        PartialChapterRow(
                            novel = novel,
                            checked = novel.id in selectedIds,
                            onCheckedChange = { checked ->
                                selectedIds =
                                    if (checked) selectedIds + novel.id else selectedIds - novel.id
                            },
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            DialogGroupTitle(stringResource(R.string.novel_download_group_export))
            DOWNLOAD_FORMATS.forEach { info ->
                DownloadOption(
                    icon = info.icon,
                    title = stringResource(info.titleRes),
                    subtitle = stringResource(info.descRes),
                    // 未选中任何分册时禁用
                    enabled = !(scope == NovelDownloadScope.PARTIAL && selectedIds.isEmpty()),
                    onClick = {
                        when (scope) {
                            NovelDownloadScope.SINGLE -> onFormat(
                                info.format,
                                NovelDownloadScope.SINGLE,
                                emptyList()
                            )

                            NovelDownloadScope.SERIES -> onFormat(
                                info.format,
                                NovelDownloadScope.SERIES,
                                emptyList()
                            )

                            NovelDownloadScope.PARTIAL -> onFormat(
                                info.format,
                                NovelDownloadScope.PARTIAL,
                                selectedIds.toList()
                            )
                        }
                    },
                )
            }
        }
    }
}

/** 详情全屏页 / 右栏 pane 共用：范围切换 + 导出格式列表，结果经 [onExport] 转发 VM。 */
@Composable
internal fun NovelDetailDownloadSheet(
    visible: Boolean,
    seriesId: Long?,
    onExport: (formatName: String, series: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    DownloadSheet(
        // 真实系列 id 必为正，过滤 pixiv 的 Series(id=0) 空对象误判
        config = DownloadSheetConfig.Detail(seriesId?.let { it > 0L } == true),
        onFormat = { format: NovelExportFormat, scope: NovelDownloadScope, _: List<Long> ->
            onExport(format.name, scope == NovelDownloadScope.SERIES)
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun PartialChapterRow(
    novel: Novel,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.card)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = Spacing.lgPlus, vertical = Spacing.xsPlus),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(Sizes.s20),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.smPlus),
        ) {
            Text(
                text = novel.title.orEmpty(),
                style = novelOptionTitleStyle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if ((novel.text_length ?: 0) > 0) {
                Text(
                    text = stringResource(
                        R.string.novel_chapter_word,
                        formatCountForNovel(novel.text_length ?: 0)
                    ),
                    style = novelSmallLabelStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun DialogGroupTitle(text: String) {
    Text(
        text = text,
        style = novelMetaStyle(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xxs, start = Spacing.lgPlus, end = Spacing.lgPlus),
    )
}

@Composable
private fun DownloadOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.card)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.lgPlus, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(Sizes.s24),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.mdPlus),
        ) {
            Text(
                text = title,
                style = novelOptionTitleStyle(),
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            )
            Text(
                text = subtitle,
                style = novelSmallLabelStyle(),
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
        }
    }
}
