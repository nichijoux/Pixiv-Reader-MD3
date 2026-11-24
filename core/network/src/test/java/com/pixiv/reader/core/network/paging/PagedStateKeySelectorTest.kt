package com.pixiv.reader.core.network.paging

import com.pixiv.api.Pageable
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PagedStateKeySelectorTest {

    private data class Item(val id: Long?, val text: String)

    private data class Page(
        override val items: List<Item>,
        override val nextPageUrl: String? = null,
    ) : Pageable<Item>

    @Test
    fun `keeps first keyed item and all unkeyed items across pages`() = runTest {
        val first = Item(1L, "first")
        val second = Item(2L, "second")
        val unkeyed = Item(null, "unkeyed")
        val state = PagedState<Item>(keySelector = Item::id)

        // 首次出现的有键项应在页内、跨页冲突时都优先。
        state.loadInitial(
            fetch = { Page(listOf(first, unkeyed, first.copy(text = "duplicate")), "next") },
            fetchNext = { Page(listOf(first.copy(text = "updated"), second, second, unkeyed)) },
        )
        assertEquals(listOf(first, unkeyed), state.items.value)

        state.loadMore()
        assertEquals(listOf(first, unkeyed, second, unkeyed), state.items.value)
    }

    @Test
    fun `deduplicates old snapshots and replaces them on refresh`() = runTest {
        val cached = Item(1L, "cached")
        val updated = cached.copy(text = "updated")
        val state = PagedState<Item>(keySelector = Item::id)

        // 老快照仍可能含重复键，刷新前就必须可安全展示。
        state.restoreSnapshot(listOf(cached, updated), null)
        assertEquals(listOf(cached), state.items.value)

        state.loadInitial(fetch = { Page(listOf(updated)) }, fetchNext = { Page(emptyList()) })
        assertEquals(listOf(updated), state.items.value)
    }

    @Test
    fun `clears loadMore error and succeeds on retry`() = runTest {
        val first = Item(1L, "first")
        val second = Item(2L, "second")
        var attempts = 0
        val state = PagedState<Item>(keySelector = Item::id)

        state.loadInitial(
            fetch = { Page(listOf(first), "next") },
            fetchNext = {
                attempts++
                if (attempts == 1) error("network timeout")
                Page(listOf(second), null)
            },
        )

        state.loadMore()
        assertEquals("network timeout", state.error.value)
        assertEquals(true, state.hasMore.value)
        assertEquals(listOf(first), state.items.value)

        // 重试 loadMore 必须先清掉错误标志并在成功后追加第二页。
        state.loadMore()
        assertEquals(null, state.error.value)
        assertEquals(false, state.hasMore.value)
        assertEquals(listOf(first, second), state.items.value)
    }
}
