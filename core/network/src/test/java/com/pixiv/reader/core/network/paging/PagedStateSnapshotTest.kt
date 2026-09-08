package com.pixiv.reader.core.network.paging

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PagedState 快照预填（restoreSnapshot / isStale）与分页、代次机制的交互单测。 */
class PagedStateSnapshotTest {

    private data class PageableImpl(val list: List<String>, val next: String?) :
        com.pixiv.api.Pageable<String> {
        override val items: List<String> get() = list
        override val nextPageUrl: String? get() = next
    }

    @Test
    fun `restoreSnapshot 预填内容并标记 stale`() = runTest {
        val state = PagedState<String>()
        state.restoreSnapshot(listOf("a", "b"), "next://1")
        assertEquals(listOf("a", "b"), state.items.value)
        assertTrue(state.isStale.value)
        assertTrue(state.hasMore.value)
    }

    @Test
    fun `loadInitial 成功后替换内容并清除 stale`() = runTest {
        val state = PagedState<String>()
        state.restoreSnapshot(listOf("old"), "next://1")
        assertTrue(state.isStale.value)
        state.loadInitial(
            fetch = { PageableImpl(listOf("new1", "new2"), null) },
            fetchNext = { error("不应调用") },
        )
        assertEquals(listOf("new1", "new2"), state.items.value)
        assertFalse(state.isStale.value)
        assertFalse(state.hasMore.value)
        assertNull(state.error.value)
    }

    @Test
    fun `loadInitial 失败时保留快照内容供离线浏览`() = runTest {
        val state = PagedState<String>()
        state.restoreSnapshot(listOf("cached"), null)
        state.loadInitial(
            fetch = { error("network down") },
            fetchNext = { error("不应调用") },
        )
        assertEquals(listOf("cached"), state.items.value)
        assertTrue(state.isStale.value)
        assertTrue(state.error.value != null)
    }

    @Test
    fun `已有内容时 restoreSnapshot 忽略`() = runTest {
        val state = PagedState<String>()
        state.restoreSnapshot(listOf("first"), null)
        state.restoreSnapshot(listOf("second"), null)
        assertEquals(listOf("first"), state.items.value)
    }

    @Test
    fun `空快照不标记 stale`() = runTest {
        val state = PagedState<String>()
        state.restoreSnapshot(emptyList(), null)
        assertFalse(state.isStale.value)
    }

    @Test
    fun `reset 清除 stale 标记与内容`() = runTest {
        val state = PagedState<String>()
        state.restoreSnapshot(listOf("cached"), "next://1")
        assertTrue(state.isStale.value)
        state.reset()
        assertTrue(state.items.value.isEmpty())
        assertFalse(state.isStale.value)
        assertFalse(state.isLoading.value)
    }

    @Test
    fun `快照游标可供触底加载续传`() = runTest {
        val state = PagedState<String>()
        state.restoreSnapshot(listOf("s1"), "next://1")
        state.loadMore()
        // restoreSnapshot 只预填 items/游标，未记录 fetchNext lambda → loadMore 无 fetcher 直接忽略
        assertEquals(listOf("s1"), state.items.value)
        state.loadInitial(
            fetch = { PageableImpl(listOf("p1", "p2"), "next://2") },
            fetchNext = { PageableImpl(listOf("p3"), null) },
        )
        state.loadMore()
        assertEquals(listOf("p1", "p2", "p3"), state.items.value)
    }
}
