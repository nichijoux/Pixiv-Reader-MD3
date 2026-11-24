package com.pixiv.reader.core.ui.component.list

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadMorePrefetchTest {

    @Test
    fun `空列表或无效可见索引不触发预加载`() {
        assertFalse(shouldPrefetchNextPage(lastVisibleIndex = -1, itemCount = 30, prefetchDistance = 6))
        assertFalse(shouldPrefetchNextPage(lastVisibleIndex = 0, itemCount = 0, prefetchDistance = 6))
    }

    @Test
    fun `距尾部达到阈值即触发预加载`() {
        // itemCount=30, lastContentIndex=29, prefetchDistance=6 -> threshold=23
        assertFalse(shouldPrefetchNextPage(lastVisibleIndex = 22, itemCount = 30, prefetchDistance = 6))
        assertTrue(shouldPrefetchNextPage(lastVisibleIndex = 23, itemCount = 30, prefetchDistance = 6))
        assertTrue(shouldPrefetchNextPage(lastVisibleIndex = 29, itemCount = 30, prefetchDistance = 6))
    }

    @Test
    fun `存在 header 时索引按 headerCount 偏移`() {
        // headerCount=1, itemCount=20 -> 内容区间 1..20, prefetchDistance=5 -> threshold=15
        assertFalse(shouldPrefetchNextPage(lastVisibleIndex = 14, itemCount = 20, prefetchDistance = 5, headerCount = 1))
        assertTrue(shouldPrefetchNextPage(lastVisibleIndex = 15, itemCount = 20, prefetchDistance = 5, headerCount = 1))
    }

    @Test
    fun `条目数小于预加载距离时任意内容可见即触发`() {
        assertFalse(shouldPrefetchNextPage(lastVisibleIndex = 0, itemCount = 3, prefetchDistance = 8, headerCount = 1))
        assertTrue(shouldPrefetchNextPage(lastVisibleIndex = 1, itemCount = 3, prefetchDistance = 8, headerCount = 1))
    }
}
