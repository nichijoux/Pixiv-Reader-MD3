package com.pixiv.reader.feature.discover.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.TrendingTag
import com.pixiv.reader.core.database.entity.SearchHistoryEntity
import com.pixiv.reader.core.ui.component.input.ConfirmDialog
import com.pixiv.reader.feature.discover.R
import com.pixiv.reader.feature.discover.state.DiscoverViewModel

/** 初始态：搜索历史 + 热门标签（历史可清空/单删）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IdlePanel(
    hotTags: List<TrendingTag>,
    history: List<SearchHistoryEntity>,
    viewModel: DiscoverViewModel,
) {
    var confirmClear by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SearchHistoryEntity?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
        ) {
            if (history.isNotEmpty()) {
                item(key = "history_title") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.search_history_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .weight(1f),
                        )
                        TextButton(onClick = {
                            confirmClear = true
                        }) {
                            Text(
                                stringResource(R.string.search_history_clear),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                // 历史胶囊：点击搜索、长按删除单条
                item(key = "history_chips") {
                    FlowRow(
                        modifier = Modifier.padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        history.forEach { item ->
                            HistoryChip(
                                text = item.keyword,
                                onClick = { viewModel.onQueryChange(item.keyword); viewModel.search() },
                                onLongClick = { pendingDelete = item },
                            )
                        }
                    }
                }
                item {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            item(key = "hot_title") {
                Text(
                    text = stringResource(R.string.search_hot_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
            items(hotTags.take(6).withIndex().toList(), key = { it.index }) { (index, tag) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { tag.tag?.let { viewModel.onQueryChange(it); viewModel.search() } }
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (index < 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(24.dp),
                    )
                    Text(
                        text = tag.translated_name ?: tag.tag.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // 清空搜索历史确认
        if (confirmClear) {
            ConfirmDialog(
                title = stringResource(R.string.search_history_clear_title),
                message = stringResource(R.string.search_history_clear_message),
                confirmText = stringResource(R.string.search_history_clear),
                onConfirm = {
                    viewModel.clearHistory()
                    confirmClear = false
                },
                onDismiss = { confirmClear = false },
            )
        }
        // 单条搜索历史删除确认（长按历史胶囊）
        pendingDelete?.let { entity ->
            ConfirmDialog(
                title = stringResource(R.string.search_history_delete_title),
                message = stringResource(R.string.search_history_delete_message, entity.keyword),
                confirmText = stringResource(com.pixiv.reader.core.ui.R.string.common_delete),
                onConfirm = {
                    viewModel.removeHistory(entity)
                    pendingDelete = null
                },
                onDismiss = { pendingDelete = null },
            )
        }
    }
}

/** 搜索历史胶囊：单击搜索、长按删除。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HistoryChip(
    text: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
