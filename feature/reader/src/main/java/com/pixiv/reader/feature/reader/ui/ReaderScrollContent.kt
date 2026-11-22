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
 *
 * @param elements 排版行元素流
 * @param restoreCharOffset 首次恢复阅读进度的字符偏移
 * @param jumpToChar 目录或搜索跳转目标字符偏移
 * @param onScrollOffset 当前滚动首可见位置对应的字符偏移回调
 * @param onPageInfo 页码信息回调（滑动模式固定 0 to 1）
 * @param onOpenImage 点击图片全屏预览回调
 * @param onJumpHandled 跳转完成后清理外部跳转锚点的回调
 * @param modifier 外部修饰符
 * @return 无返回值
 */
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
    // 记录当前滚动锚点，字体/简繁重排后保持在同一字符位置附近。
    var anchorChar by remember { mutableIntStateOf(restoreCharOffset) }
    val density = LocalDensity.current

    // 首次定位到上次阅读位置
    LaunchedEffect(items, restoreCharOffset) {
        if (restored || items.isEmpty()) return@LaunchedEffect
        val index = items.indexOfFirst { it.anchorEnd > restoreCharOffset }
            .let { if (it >= 0) it else items.size - 1 }
        listState.scrollToItem(index.coerceAtLeast(0))
        anchorChar = restoreCharOffset
        restored = true
    }

    // 排版重排（如修改字号/行距/简繁切换）后恢复至当前字符锚点。
    LaunchedEffect(items) {
        if (!restored || items.isEmpty()) return@LaunchedEffect
        val index = items.indexOfFirst { it.anchorEnd > anchorChar }
            .let { if (it >= 0) it else items.size - 1 }
            .coerceAtLeast(0)
        if (listState.firstVisibleItemIndex != index) {
            listState.scrollToItem(index)
        }
    }

    // 目录/搜索跳转（待列表就绪后执行，完成后通知外层清空锚点以便重复跳同一偏移）
    LaunchedEffect(jumpToChar, items.isNotEmpty()) {
        val j = jumpToChar ?: return@LaunchedEffect
        if (items.isEmpty()) return@LaunchedEffect
        val index = items.indexOfFirst { it.anchorEnd > j }
            .let { if (it >= 0) it else items.size - 1 }
        listState.scrollToItem(index.coerceAtLeast(0))
        anchorChar = j
        currentOnJumpHandled()
    }

    // 滚动进度：使用最新 items 与回调，避免 elements 变更后仍读取首次组合的陈旧列表与闭包。
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
            // 行元素渲染与翻页模式共用同一实现（PageElementView）
            PageElementView(items[index].element, density, onOpenImage)
        }
    }
}
