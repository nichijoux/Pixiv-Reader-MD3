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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.pixiv.reader.feature.novel.R
import com.pixiv.reader.feature.novel.data.NovelExportFormat

// ── 下载底部弹窗 ─────────────────────────────────────────────────────────────

/** 下载范围：单本 / 整个系列 / 系列部分分册。 */
enum class NovelDownloadScope { SINGLE, SERIES, PARTIAL }

/**
 * 下载弹窗配置：决定范围标签与默认选中，与分册数据是否已加载解耦。
 * - [Detail]：小说详情页。「单个文件 / 整个系列」（[hasSeries] 控制是否显示范围切换），默认单个文件
 * - [Series]：小说系列页。「整个系列 / 选取部分」，默认整个系列；
 *   [partialChapters] 异步到达只影响多选列表内容，不影响标签与默认选中。
 */
sealed interface DownloadSheetConfig {
    data class Detail(val hasSeries: Boolean) : DownloadSheetConfig
    data class Series(val partialChapters: List<Novel>) : DownloadSheetConfig
}

/** 部分下载分册多选列表最大高度（内部滚动，避免章节过多把导出格式挤出屏幕）。 */
private val PARTIAL_LIST_MAX_HEIGHT = 280.dp

/** 下载格式行信息（标题、副标题、图标）。 */
private data class DownloadFormatInfo(
    val format: NovelExportFormat,
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
)

/** 五种导出格式。 */
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

/**
 * 下载选择底部弹窗：从底部弹出。
 * 范围标签与默认选中由 [config] 决定（见 [DownloadSheetConfig]）；
 * 下方单一格式列表；点击格式行按当前选中范围导出。
 */
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
    // 范围默认值由页面类型决定（与分册数据加载状态无关）：系列页「整个系列」，详情页「单个文件」
    val defaultScope = if (isSeriesPage) NovelDownloadScope.SERIES else NovelDownloadScope.SINGLE
    var scope by rememberSaveable { mutableStateOf(defaultScope) }
    // 部分下载选中分册（默认全选）
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
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.novel_download_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            if (isSeriesPage || (config as? DownloadSheetConfig.Detail)?.hasSeries == true) {
                // 范围选择器：系列页「整个系列 / 选取部分」；详情页「单个文件 / 整个系列」
                val labels = if (isSeriesPage) {
                    listOf(
                        R.string.novel_download_scope_series to NovelDownloadScope.SERIES,
                        R.string.novel_download_scope_partial to NovelDownloadScope.PARTIAL,
                    )
                } else {
                    listOf(
                        R.string.novel_download_scope_single to NovelDownloadScope.SINGLE,
                        R.string.novel_download_scope_series to NovelDownloadScope.SERIES,
                    )
                }
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    labels.forEachIndexed { index, (labelRes, s) ->
                        SegmentedButton(
                            selected = scope == s,
                            onClick = { scope = s },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = labels.size
                            ),
                        ) {
                            Text(stringResource(labelRes))
                        }
                    }
                }
            }
            // 部分下载：分册多选列表（限高可滚动，避免章节太多把下方导出格式挤出屏幕）
            if (isSeriesPage && scope == NovelDownloadScope.PARTIAL) {
                Text(
                    text = stringResource(
                        R.string.novel_download_select_hint,
                        selectedIds.size,
                        partialChapters.size
                    ),
                    style = novelMetaStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
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
                    // 部分下载且未选中任何分册：禁用（点击不触发导出）
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

/** 部分下载分册选择行：checkbox + 标题 + 字数。 */
@Composable
private fun PartialChapterRow(
    novel: Novel,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(20.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
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
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp, start = 20.dp, end = 20.dp),
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
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
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
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
