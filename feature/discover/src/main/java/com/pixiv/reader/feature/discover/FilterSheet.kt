package com.pixiv.reader.feature.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** V3 高级筛选底部面板 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterBottomSheet(
    filters: SearchFilters,
    onDismiss: () -> Unit,
    onApply: (SearchFilters) -> Unit,
) {
    var draft by remember { mutableStateOf(filters) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "高级筛选",
                style = MaterialTheme.typography.titleLarge,
            )

            // 排序
            FilterRow("排序") {
                FilterChip(selected = draft.sort == "date_desc", onClick = { draft = draft.copy(sort = "date_desc") }, label = { Text("最新") })
                FilterChip(selected = draft.sort == "bookmark", onClick = { draft = draft.copy(sort = "bookmark") }, label = { Text("收藏多") })
            }
            // 匹配方式
            FilterRow("匹配方式") {
                FilterChip(selected = draft.searchTarget == "partial_match_for_tags", onClick = { draft = draft.copy(searchTarget = "partial_match_for_tags") }, label = { Text("标签") })
                FilterChip(selected = draft.searchTarget == "title_and_caption", onClick = { draft = draft.copy(searchTarget = "title_and_caption") }, label = { Text("标题简介") })
            }
            // 时间范围
            Text("时间范围", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = draft.startDate.orEmpty(),
                    onValueChange = { draft = draft.copy(startDate = it.ifBlank { null }) },
                    modifier = Modifier.weight(1f),
                    label = { Text("开始") },
                    placeholder = { Text("2026-01-01") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
                OutlinedTextField(
                    value = draft.endDate.orEmpty(),
                    onValueChange = { draft = draft.copy(endDate = it.ifBlank { null }) },
                    modifier = Modifier.weight(1f),
                    label = { Text("结束") },
                    placeholder = { Text("2026-08-01") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
            }
            // 收藏数
            OutlinedTextField(
                value = draft.bookmarkNumMin?.toString().orEmpty(),
                onValueChange = { draft = draft.copy(bookmarkNumMin = it.toIntOrNull()) },
                label = { Text("最低收藏数") },
                placeholder = { Text("不限") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            // AI
            FilterRow("AI 作品") {
                FilterChip(selected = draft.aiType == 0, onClick = { draft = draft.copy(aiType = 0) }, label = { Text("全部") })
                FilterChip(selected = draft.aiType == 1, onClick = { draft = draft.copy(aiType = 1) }, label = { Text("仅人绘") })
                FilterChip(selected = draft.aiType == 2, onClick = { draft = draft.copy(aiType = 2) }, label = { Text("仅 AI") })
            }

            Button(onClick = { onApply(draft) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("应用筛选")
            }
        }
    }
}

@Composable
private fun FilterRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
        }
    }
}
