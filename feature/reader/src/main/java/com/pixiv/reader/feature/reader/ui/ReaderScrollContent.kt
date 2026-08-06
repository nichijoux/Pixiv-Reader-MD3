package com.pixiv.reader.feature.reader.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.novel.NovelBlock
import com.pixiv.reader.core.novel.NovelDocument

/** 滑动模式列表项：块 + 字符锚点区间（用于进度恢复与目录/搜索跳转）。 */
internal data class ScrollItem(
    val key: Long,
    val block: NovelBlock,
    val anchorStart: Int,
    val anchorEnd: Int,
)

internal fun buildScrollItems(document: NovelDocument): List<ScrollItem> {
    val result = mutableListOf<ScrollItem>()
    var cursor = 0
    document.blocks.forEachIndexed { index, block ->
        when (block) {
            is NovelBlock.Paragraph, is NovelBlock.Heading, is NovelBlock.Quote -> {
                result.add(ScrollItem(index.toLong(), block, block.startChar, block.endChar))
                cursor = block.endChar
            }

            is NovelBlock.Image, is NovelBlock.Separator -> {
                result.add(ScrollItem(index.toLong(), block, cursor, cursor))
            }
        }
    }
    return result
}

/** 滑动阅读模式：LazyColumn 逐块渲染，支持恢复位置 / 目录搜索跳转 / 滚动进度上报。 */
@Composable
internal fun ScrollReaderContent(
    document: NovelDocument,
    baseStyle: TextStyle,
    imageHeight: Dp,
    restoreCharOffset: Int,
    jumpToChar: Int?,
    onScrollOffset: (Int) -> Unit,
    onPageInfo: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember(document) { buildScrollItems(document) }
    val listState = rememberLazyListState()
    var restored by remember { mutableStateOf(false) }

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

    // 滚动进度：首可见块的字符偏移 + 块内滚动比例
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
            when (val block = items[index].block) {
                is NovelBlock.Paragraph -> Text(
                    text = block.text,
                    style = baseStyle.copy(textAlign = TextAlign.Justify),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )

                is NovelBlock.Heading -> Text(
                    text = block.text,
                    style = baseStyle.copy(
                        fontSize = baseStyle.fontSize * 1.25f,
                        lineHeight = baseStyle.lineHeight * 1.15f,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 6.dp),
                )

                is NovelBlock.Quote -> Text(
                    text = block.text,
                    style = baseStyle.copy(
                        color = baseStyle.color.copy(alpha = 0.72f),
                        textAlign = TextAlign.Justify
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                )

                is NovelBlock.Separator -> Text(
                    text = block.symbol,
                    style = baseStyle.copy(textAlign = TextAlign.Center),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )

                is NovelBlock.Image -> ReaderImageBlock(block.url, block.caption, imageHeight)
            }
        }
    }
}
