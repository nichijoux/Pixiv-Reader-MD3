package com.pixiv.reader.feature.reader.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.pixiv.reader.feature.reader.state.PageElement

internal data class ScrollItem(
    val key: Long,
    val element: PageElement,
    val anchorStart: Int,
    val anchorEnd: Int,
)

internal fun buildScrollItems(elements: List<PageElement>): List<ScrollItem> {
    var cursor = 0
    return elements.mapIndexed { index, element ->
        when (element) {
            is PageElement.TextLine -> {
                cursor = element.endChar
                ScrollItem(index.toLong(), element, element.startChar, element.endChar)
            }

            else -> ScrollItem(index.toLong(), element, cursor, cursor)
        }
    }
}

@Composable
internal fun ScrollReaderContent(
    elements: List<PageElement>,
    restoreCharOffset: Int,
    jumpToChar: Int?,
    onScrollOffset: (Int) -> Unit,
    onPageInfo: (Int, Int) -> Unit,
    onOpenImage: (String) -> Unit = {},
    onJumpHandled: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val items = remember(elements) { buildScrollItems(elements) }
    val currentItems by rememberUpdatedState(items)
    val currentOnScrollOffset by rememberUpdatedState(onScrollOffset)
    val currentOnJumpHandled by rememberUpdatedState(onJumpHandled)
    val listState = rememberLazyListState()
    var restored by remember { mutableStateOf(false) }
    var anchorChar by remember { mutableIntStateOf(restoreCharOffset) }
    val density = LocalDensity.current

    LaunchedEffect(items, restoreCharOffset) {
        if (restored || items.isEmpty()) return@LaunchedEffect
        val index = items.indexOfFirst { it.anchorEnd > restoreCharOffset }
            .let { if (it >= 0) it else items.size - 1 }
        listState.scrollToItem(index.coerceAtLeast(0))
        anchorChar = restoreCharOffset
        restored = true
    }

    // 排版重排后按当前字符锚点恢复位置
    LaunchedEffect(items) {
        if (!restored || items.isEmpty()) return@LaunchedEffect
        val index = items.indexOfFirst { it.anchorEnd > anchorChar }
            .let { if (it >= 0) it else items.size - 1 }
            .coerceAtLeast(0)
        if (listState.firstVisibleItemIndex != index) {
            listState.scrollToItem(index)
        }
    }

    // 跳转后清空外部锚点，保证重复跳同一偏移仍生效
    LaunchedEffect(jumpToChar, items.isNotEmpty()) {
        val j = jumpToChar ?: return@LaunchedEffect
        if (items.isEmpty()) return@LaunchedEffect
        val index = items.indexOfFirst { it.anchorEnd > j }
            .let { if (it >= 0) it else items.size - 1 }
        listState.scrollToItem(index.coerceAtLeast(0))
        anchorChar = j
        currentOnJumpHandled()
    }

    // 经 rememberUpdatedState 读取最新列表与回调，避免 LaunchedEffect(listState) 捕获陈旧闭包
    LaunchedEffect(listState) {
        snapshotFlow {
            val index = listState.firstVisibleItemIndex
            val offsetPx = listState.firstVisibleItemScrollOffset
            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            Triple(index, offsetPx, item?.size ?: 0)
        }.collect { (index, offsetPx, itemSize) ->
            val item = currentItems.getOrNull(index) ?: return@collect
            val span = (item.anchorEnd - item.anchorStart).coerceAtLeast(0)
            val fraction =
                if (itemSize > 0) (offsetPx.toFloat() / itemSize).coerceIn(0f, 1f) else 0f
            val charPos = item.anchorStart + (span * fraction).toInt()
            anchorChar = charPos
            currentOnScrollOffset(charPos)
        }
    }

    LaunchedEffect(Unit) { onPageInfo(0, 1) }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = PAGE_H_PADDING, vertical = PAGE_V_PADDING),
    ) {
        items(count = items.size, key = { items[it].key }) { index ->
            PageElementView(items[index].element, density, onOpenImage)
        }
    }
}
