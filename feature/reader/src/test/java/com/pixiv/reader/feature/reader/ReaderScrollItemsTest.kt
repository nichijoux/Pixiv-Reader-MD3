package com.pixiv.reader.feature.reader

import androidx.compose.ui.text.TextStyle
import com.pixiv.reader.feature.reader.state.PageElement
import com.pixiv.reader.feature.reader.ui.buildScrollItems
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 滑动模式行级锚点契约：行是元素（legado 语义）——
 * 文本行锚点 = 自身字符区间，段距空隙/图片等无字符区间元素锚点 = 上一个文本行结束位置（排版游标）。
 * 进度恢复 / 目录 / 搜索跳转均按行级锚点定位，粒度细于段落。
 */
class ReaderScrollItemsTest {

    private fun line(text: String, start: Int, end: Int) =
        PageElement.TextLine(
            text = text,
            style = TextStyle(),
            startChar = start,
            endChar = end,
            heightPx = 1,
        )

    @Test
    fun `文本行锚点取自身字符区间并推进游标`() {
        val items = buildScrollItems(
            listOf(line("第一行", 0, 4), line("第二行", 4, 10)),
        )
        assertEquals(2, items.size)
        assertEquals(0, items[0].anchorStart)
        assertEquals(4, items[0].anchorEnd)
        assertEquals(4, items[1].anchorStart)
        assertEquals(10, items[1].anchorEnd)
    }

    @Test
    fun `段距空隙锚点取上一个文本行结束位置`() {
        val items = buildScrollItems(
            listOf(
                line("段落", 0, 6),
                PageElement.Gap(10),
                line("后续", 6, 12),
            ),
        )
        assertEquals(6, items[1].anchorStart)
        assertEquals(6, items[1].anchorEnd)
        assertEquals(6, items[2].anchorStart)
    }

    @Test
    fun `图片锚点取排版游标且不推进`() {
        val items = buildScrollItems(
            listOf(
                line("正文", 0, 4),
                PageElement.Image("https://example.com/a.png", null, 100),
                line("图后", 4, 8),
            ),
        )
        assertEquals(4, items[1].anchorStart)
        assertEquals(4, items[1].anchorEnd)
        // 图片不推进游标：后一个文本行锚点仍从自身字符区间开始
        assertEquals(4, items[2].anchorStart)
        assertEquals(8, items[2].anchorEnd)
    }

    @Test
    fun `分隔线行锚点为游标自身且不占用区间`() {
        // 引擎产出：分隔线 TextLine 的 startChar == endChar == 排版游标（分隔线不在全文里）
        val items = buildScrollItems(
            listOf(
                line("段一", 0, 5),
                line("——————", 5, 5),
                line("段二", 5, 9),
            ),
        )
        assertEquals(5, items[1].anchorStart)
        assertEquals(5, items[1].anchorEnd)
        assertEquals(5, items[2].anchorStart)
    }

    @Test
    fun `开头的空隙锚点为0`() {
        val items = buildScrollItems(
            listOf(PageElement.Gap(10), line("正文", 0, 4)),
        )
        assertEquals(0, items[0].anchorStart)
        assertEquals(0, items[0].anchorEnd)
    }
}
