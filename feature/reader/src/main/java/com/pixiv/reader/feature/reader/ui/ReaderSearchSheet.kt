package com.pixiv.reader.feature.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixiv.reader.feature.reader.R

/**
 * 阅读器全文搜索面板（底部弹层）。
 * 输入即搜（委托 VM 的全文搜索），列表展示命中上下文，支持上一条/下一条与点击跳转。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSearchSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    searchResults: List<Int>,
    searchIndex: Int,
    fullText: String?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.reader_search_placeholder)) },
                singleLine = true,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (searchIndex >= 0) {
                        stringResource(
                            R.string.reader_search_position,
                            searchIndex + 1,
                            searchResults.size
                        )
                    } else {
                        stringResource(R.string.reader_search_total, searchResults.size)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onPrev, enabled = searchIndex > 0) {
                    Icon(
                        Icons.Filled.ArrowUpward,
                        contentDescription = stringResource(R.string.reader_cd_prev)
                    )
                }
                IconButton(onClick = onNext, enabled = searchIndex in 0 until searchResults.size - 1) {
                    Icon(
                        Icons.Filled.ArrowDownward,
                        contentDescription = stringResource(R.string.reader_cd_next)
                    )
                }
            }
            if (searchResults.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                ) {
                    items(searchResults.take(100)) { offset ->
                        val ctx = searchSnippet(fullText, offset)
                        Text(
                            text = ctx,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(offset) }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 搜索匹配的上下文片段（前后各截取一段）。 */
private fun searchSnippet(fullText: String?, offset: Int): String {
    val text = fullText ?: return ""
    val start = (offset - 12).coerceAtLeast(0)
    val end = (offset + 40).coerceAtMost(text.length)
    val prefix = if (start > 0) "…" else ""
    return prefix + text.substring(start, end).replace('\n', ' ')
}
