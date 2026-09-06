package com.pixiv.reader.core.ui.component.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 触底加载项（LazyColumn item 尾部）：进入可视区自动触发一次 [onLoadMore]，
 * 加载中显示 56dp 高的 Expressive `LoadingIndicator` 占位（无加载时保持占位高度，避免列表尾部跳动）。
 *
 * 替代此前在 10+ 个列表页复制的 `item(key="load_more"){LaunchedEffect+Box}` 样板。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadMoreItem(
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onLoadMore() }
    Box(
        modifier = modifier.fillMaxWidth().height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoadingMore) {
            LoadingIndicator()
        }
    }
}
