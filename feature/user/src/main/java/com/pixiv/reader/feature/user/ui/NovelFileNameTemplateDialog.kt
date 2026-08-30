package com.pixiv.reader.feature.user.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.common.format.NovelFileNameTemplate
import com.pixiv.reader.core.common.format.renderNovelFileName
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.feature.user.R

/**
 * 小说下载命名模板编辑对话框：单本下载 / 系列导出两套模板分别配置。
 *
 * 顶部范围切换（单本/系列）决定当前编辑的模板与预览口径；占位符 chips 点击追加；
 * 占位符语义说明默认折叠（chips 已直观列出可用占位符，避免常驻提示挤占弹窗），
 * 点「占位符说明」按需展开；保存时两套一起落盘；恢复默认将两套重置为各自默认值。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NovelFileNameTemplateDialog(
    initialSingle: String,
    initialSeries: String,
    onSave: (single: String, series: String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    // 0 = 单本，1 = 系列
    var activeScope by rememberSaveable { mutableIntStateOf(0) }
    var singleTemplate by rememberSaveable { mutableStateOf(initialSingle) }
    var seriesTemplate by rememberSaveable { mutableStateOf(initialSeries) }
    // 占位符说明展开状态（默认收起）
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val editingSeries = activeScope == 1
    val activeTemplate = if (editingSeries) seriesTemplate else singleTemplate

    fun appendToken(token: String) {
        if (editingSeries) seriesTemplate += token else singleTemplate += token
    }

    // 预览与真实渲染同一函数、同一回退默认：
    // 单本不传系列（{series} 无系列直接留空）；系列传示例系列名。
    val preview = if (editingSeries) {
        renderNovelFileName(
            template = seriesTemplate,
            title = stringResource(R.string.me_file_name_preview_title),
            author = stringResource(R.string.me_file_name_preview_author),
            id = 123456L,
            seriesTitle = stringResource(R.string.me_file_name_preview_series),
            publishDate = "2024-05-01",
            favoriteCount = 8888,
            fallbackTemplate = NovelFileNameTemplate.DEFAULT_SERIES,
        )
    } else {
        renderNovelFileName(
            template = singleTemplate,
            title = stringResource(R.string.me_file_name_preview_title),
            author = stringResource(R.string.me_file_name_preview_author),
            id = 123456L,
            seriesTitle = null,
            publishDate = "2024-05-01",
            favoriteCount = 8888,
            fallbackTemplate = NovelFileNameTemplate.DEFAULT_SINGLE,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.me_novel_file_name_template)) },
        text = {
            Column {
                // 范围切换：单本下载 / 系列导出
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !editingSeries,
                        onClick = { activeScope = 0 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) {
                        Text(stringResource(R.string.me_file_name_scope_single))
                    }
                    SegmentedButton(
                        selected = editingSeries,
                        onClick = { activeScope = 1 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) {
                        Text(stringResource(R.string.me_file_name_scope_series))
                    }
                }
                OutlinedTextField(
                    value = activeTemplate,
                    onValueChange = { value ->
                        if (editingSeries) seriesTemplate = value else singleTemplate = value
                    },
                    singleLine = true,
                    label = {
                        Text(
                            stringResource(
                                if (editingSeries) R.string.me_file_name_scope_series
                                else R.string.me_file_name_scope_single
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                )
                // 帮助开关（按需展示）：点击展开/收起占位符语义说明
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm)
                        .clickable { showHelp = !showHelp },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Icon(
                        imageVector = if (showHelp) Icons.Filled.ExpandLess else Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Sizes.s16),
                    )
                    Text(
                        text = stringResource(R.string.me_file_name_help_toggle),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                AnimatedVisibility(visible = showHelp) {
                    Text(
                        text = stringResource(R.string.me_file_name_placeholder_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
                // 占位符插入按钮（浅色容器 + 全圆胶囊，点击追加到当前编辑模板末尾）
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
                ) {
                    NovelFileNameTemplate.ALL.forEach { token ->
                        SuggestionChip(
                            onClick = { appendToken(token) },
                            label = { Text(token) },
                            shape = AppShapes.pill,
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                labelColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                }
                // 预览
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text(
                        text = stringResource(R.string.me_file_name_preview),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "$preview.txt",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { onSave(singleTemplate, seriesTemplate) }) {
                Text(stringResource(R.string.me_save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onReset) {
                Text(stringResource(R.string.me_reset_default))
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.me_cancel))
            }
        },
    )
}
