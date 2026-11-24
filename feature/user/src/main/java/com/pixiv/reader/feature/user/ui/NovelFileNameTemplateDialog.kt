package com.pixiv.reader.feature.user.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.pixiv.reader.core.common.format.NovelFileNameTemplate
import com.pixiv.reader.core.common.format.renderNovelFileName
import com.pixiv.reader.core.ui.component.input.PillSegmentedRow
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.feature.user.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val editingSeries = activeScope == 1
    val activeTemplate = if (editingSeries) seriesTemplate else singleTemplate

    fun appendToken(token: String) {
        if (editingSeries) seriesTemplate += token else singleTemplate += token
    }

    // 预览必须与真实导出走同一函数、同一回退默认，否则用户看到的文件名与实际不符。
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl)
                // sheet 容器不自动垫底：内容尾部避开系统手势条
                .navigationBarsPadding()
                .padding(bottom = Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.me_novel_file_name_template),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            PillSegmentedRow(
                options = listOf(
                    false to R.string.me_file_name_scope_single,
                    true to R.string.me_file_name_scope_series,
                ),
                selected = editingSeries,
                onSelect = { series -> activeScope = if (series) 1 else 0 },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.lg),
            )
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onReset) {
                    Text(stringResource(R.string.me_reset_default))
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.me_cancel),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = { onSave(singleTemplate, seriesTemplate) }) {
                    Text(
                        text = stringResource(R.string.me_save),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
