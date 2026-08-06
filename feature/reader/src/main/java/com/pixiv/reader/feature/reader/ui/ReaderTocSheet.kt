package com.pixiv.reader.feature.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixiv.reader.feature.reader.R
import com.pixiv.reader.feature.reader.state.ReaderTocItem

/**
 * 阅读器目录面板（底部弹层）。
 * 目录项：当前小说（novelId=-1）点按页内跳转；系列内其他小说点按打开对应阅读器。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderTocSheet(
    toc: List<ReaderTocItem>,
    tocLoading: Boolean,
    currentNovelId: Long,
    onJumpToChar: (Int) -> Unit,
    onOpenNovel: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.reader_panel_toc),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        when {
            tocLoading -> Text(
                text = stringResource(R.string.reader_toc_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )

            toc.isEmpty() -> Text(
                text = stringResource(R.string.reader_toc_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp),
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(toc, key = { it.novelId }) { item ->
                    val isCurrent = item.novelId == currentNovelId || item.novelId == -1L
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrent) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isCurrent) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else {
                                    Color.Transparent
                                },
                            )
                            .clickable {
                                if (isCurrent) {
                                    onJumpToChar(item.charOffset)
                                } else {
                                    onOpenNovel(item.novelId)
                                }
                                onDismiss()
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        }
    }
}
