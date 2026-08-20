package com.pixiv.reader.feature.user.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.common.format.NovelFileNameTemplate
import com.pixiv.reader.core.common.format.renderNovelFileName
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.feature.user.R

/**
 * 小说下载命名模板编辑对话框。
 *
 * 占位符 chips 点击插入到光标处；下方实时预览（示例数据渲染）；支持恢复默认。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NovelFileNameTemplateDialog(
    initialTemplate: String,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var template by remember(initialTemplate) { mutableStateOf(initialTemplate) }
    // 预览用示例数据（与真实渲染同一函数，占位符语义一致）
    val preview = renderNovelFileName(
        template = template,
        title = stringResource(R.string.me_file_name_preview_title),
        author = stringResource(R.string.me_file_name_preview_author),
        id = 123456L,
        seriesTitle = stringResource(R.string.me_file_name_preview_series),
        publishDate = "2024-05-01",
        favoriteCount = 8888,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.me_novel_file_name_template)) },
        text = {
            Column {
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.me_file_name_template_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.me_file_name_placeholder_desc),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                // 占位符插入按钮（浅色容器 + 全圆胶囊，点击追加到模板末尾）
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    NovelFileNameTemplate.ALL.forEach { token ->
                        SuggestionChip(
                            onClick = { template += token },
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
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            FilledTonalButton(onClick = { onSave(template) }) {
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
