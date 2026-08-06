package com.pixiv.reader.feature.novel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import com.pixiv.reader.feature.novel.R
import com.pixiv.reader.feature.novel.data.NovelExportFormat

// ── 下载底部弹窗 ─────────────────────────────────────────────────────────────

/** 下载格式行信息（标题、副标题、图标）。 */
private data class DownloadFormatInfo(
    val format: NovelExportFormat,
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
)

/** 五种导出格式。 */
private val DOWNLOAD_FORMATS = listOf(
    DownloadFormatInfo(NovelExportFormat.TXT, R.string.novel_download_txt_current, R.string.novel_download_txt_current_desc, Icons.Filled.Description),
    DownloadFormatInfo(NovelExportFormat.EPUB, R.string.novel_download_epub_current, R.string.novel_download_epub_current_desc, Icons.Filled.MenuBook),
    DownloadFormatInfo(NovelExportFormat.PDF, R.string.novel_download_pdf_current, R.string.novel_download_pdf_current_desc, Icons.Filled.PictureAsPdf),
    DownloadFormatInfo(NovelExportFormat.MARKDOWN, R.string.novel_download_markdown_current, R.string.novel_download_markdown_current_desc, Icons.Filled.Notes),
    DownloadFormatInfo(NovelExportFormat.DOCX, R.string.novel_download_docx_current, R.string.novel_download_docx_current_desc, Icons.Filled.Article),
)

/**
 * 下载选择底部弹窗：从底部弹出。
 * 顶部范围选择器（单个文件 / 整个系列，无系列时不显示），下方单一格式列表；
 * 点击格式行按当前选中范围导出。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadSheet(
    hasSeries: Boolean,
    onFormat: (NovelExportFormat, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 是否导出整个系列（false=单个文件）；无系列时恒 false
    var scopeSeries by rememberSaveable { mutableStateOf(false) }
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
            if (hasSeries) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    SegmentedButton(
                        selected = !scopeSeries,
                        onClick = { scopeSeries = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) {
                        Text(stringResource(R.string.novel_download_scope_single))
                    }
                    SegmentedButton(
                        selected = scopeSeries,
                        onClick = { scopeSeries = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) {
                        Text(stringResource(R.string.novel_download_scope_series))
                    }
                }
            }
            DialogGroupTitle(stringResource(R.string.novel_download_group_export))
            DOWNLOAD_FORMATS.forEach { info ->
                DownloadOption(
                    icon = info.icon,
                    title = stringResource(info.titleRes),
                    subtitle = stringResource(info.descRes),
                    onClick = { onFormat(info.format, scopeSeries) },
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
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
            )
            Text(
                text = subtitle,
                style = novelSmallLabelStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
