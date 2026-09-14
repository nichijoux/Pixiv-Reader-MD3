package com.pixiv.reader.feature.reader.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.pixiv.reader.feature.reader.state.PageElement

/**
 * 滑动模式列表项：行元素 + 字符锚点区间（用于进度恢复与目录/搜索跳转）。
 *
 * 行是元素（legado 语义）——每个 [PageElement.TextLine] 是独立列表项，
 * 段落只是排版输入（换行由 [com.pixiv.reader.feature.reader.state.ReaderLineEngine] 完成）。
 */
internal data class ScrollItem(
    val key: Long,
    val element: PageElement,
    val anchorStart: Int,
    val anchorEnd: Int,
)

/**
 * 由行元素流构建滑动列表项：
 * - 文本行锚点 = 自身字符区间（行级，进度/跳转粒度比段落细）
 * - 段距空隙/图片无字符区间，锚点取上一个文本行结束位置（排版游标）
 */
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

/**
 * 滑动阅读模式：LazyColumn 逐行渲染（行是元素，段落只是排版输入）。
 * 元素流由 [com.pixiv.reader.feature.reader.state.rememberReaderElements] 提供，
 * 与分页模式共用同一排版引擎，换行/样式/锚点完全一致。
 */
@Composable
internal fun ScrollReaderContent(
    elements: List<PageElement>,
    restoreCharOffset: Int,
    jumpToChar: Int?,
    onScrollOffset: (Int) -> Unit,
    onPageInfo: (Int, Int) -> Unit,
    onOpenImage: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val items = remember(elements) { buildScrollItems(elements) }
    val listState = rememberLazyListState()
    var restored by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    // 首次定位到上次阅读位置
    LaunchedEffect(items, restoreCharOffset) {
        if (restored || items.isEmpty()) return@LaunchedEffect
        val index = items.indexOfFirst { it.anchorEnd > restoreCharOffset }
            .let { if (it >= 0) it else items.size - 1 }
        listState.scrollToItem(index.coerceAtLeast(0))
        restored = true
    }

    // 目录/搜索跳转
    LaunchedEffect(jumpToChar) {
        val j = jumpToChar ?: return@LaunchedEffect
        if (items.isEmpty()) return@LaunchedEffect
        val index = items.indexOfFirst { it.anchorEnd > j }
            .let { if (it >= 0) it else items.size - 1 }
        listState.scrollToItem(index.coerceAtLeast(0))
    }

    // 滚动进度：首可见元素的字符偏移 + 元素内滚动比例（行级锚点）
    LaunchedEffect(listState) {
        snapshotFlow {
            val index = listState.firstVisibleItemIndex
            val offsetPx = listState.firstVisibleItemScrollOffset
            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            Triple(index, offsetPx, item?.size ?: 0)
        }.collect { (index, offsetPx, itemSize) ->
            val item = items.getOrNull(index) ?: return@collect
            val span = (item.anchorEnd - item.anchorStart).coerceAtLeast(0)
            val fraction =
                if (itemSize > 0) (offsetPx.toFloat() / itemSize).coerceIn(0f, 1f) else 0f
            onScrollOffset(item.anchorStart + (span * fraction).toInt())
        }
    }

    LaunchedEffect(Unit) { onPageInfo(0, 1) }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = PAGE_H_PADDING, vertical = PAGE_V_PADDING),
    ) {
        items(count = items.size, key = { items[it].key }) { index ->
            // 行元素渲染与翻页模式共用同一实现（PageElementView）
            PageElementView(items[index].element, density, onOpenImage)
        }
    }
}
