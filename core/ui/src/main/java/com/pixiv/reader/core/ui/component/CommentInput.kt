package com.pixiv.reader.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.R

/**
 * 评论输入框（插画/小说详情评论区共用）。
 *
 * ## UI 设计方式
 * 横向 `Row`：`OutlinedTextField`（单行输入，`weight(1f)` 占满）+ 发送 `IconButton`
 * （`AutoMirrored.Send` 主色图标）。紧凑的一行式输入条，贴合评论区底部。
 *
 * @param draft 当前输入内容（由外部 ViewModel 持有状态）
 * @param onDraftChange 输入内容变化回调
 * @param onPost 发送评论回调（外部校验非空并调 API，成功后清空 draft）
 * @param modifier 外部传入的 Modifier
 */
@Composable
fun CommentInput(
    draft: String,
    onDraftChange: (String) -> Unit,
    onPost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.comment_placeholder)) },
            singleLine = true,
        )
        IconButton(onClick = onPost) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.comment_send), tint = MaterialTheme.colorScheme.primary)
        }
    }
}
