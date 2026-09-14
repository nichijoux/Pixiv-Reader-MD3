package com.pixiv.reader.core.ui.component.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Master-Detail 右栏未选中占位：整块居中的一行次级色提示文案
 * （小说详情 / 小说系列 / FANBOX 帖子等 pane 共用）。
 *
 * @param text 占位提示文案
 * @param modifier 外部 Modifier（默认撑满父容器）
 * @return 无返回值（渲染 Composable）
 */
@Composable
fun PanePlaceholder(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
