package com.pixiv.reader.core.network.paging

import com.example.pixivapi.Pageable
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private data class FakePage<T>(
    override val items: List<T>,
    override val nextPageUrl: String? = null,
) : Pageable<T>

class PagedStateTest {

    @Test
    fun `loadInitial populates items and hasMore from next_url`() = runTest {
        val state = PagedState<Int>()
        val first = FakePage(listOf(1, 2, 3), nextPageUrl = "next://1")
        val second = FakePage(listOf(4, 5, 6), nextPageUrl = null)

        state.loadInitial(fetch = { first }, fetchNext = { url ->
            assertEquals("next://1", url)
            second
        })

        assertEquals(listOf(1, 2, 3), state.items.value)
        assertTrue(state.hasMore.value)
        assertNull(state.error.value)
        assertFalse(state.isLoading.value)
    }

    @Test
    fun `loadMore appends items until next_url is null`() = runTest {
        val state = PagedState<Int>()
        val pages = listOf(
            FakePage(listOf(1, 2), nextPageUrl = "u1"),
            FakePage(listOf(3, 4), nextPageUrl = "u2"),
            FakePage(listOf(5), nextPageUrl = null),
        )
        var calls = 0

        state.loadInitial(
            fetch = { pages[0] },
            fetchNext = { _ -> pages[++calls] },
        )
        state.loadMore()
        state.loadMore()

        assertEquals(listOf(1, 2, 3, 4, 5), state.items.value)
        assertFalse(state.hasMore.value)
        // 没有 next_url 时 loadMore 不再请求
        calls = 100
        state.loadMore()
        assertEquals(100, calls)
    }

    @Test
    fun `error is captured and hasMore disabled`() = runTest {
        val state = PagedState<Int>()

        state.loadInitial(
            fetch = { throw RuntimeException("boom") },
            fetchNext = { _ -> FakePage(emptyList()) },
        )

        assertEquals(emptyList<Int>(), state.items.value)
        assertEquals("boom", state.error.value)
        assertFalse(state.hasMore.value)
        assertFalse(state.isLoading.value)
    }

    @Test
    fun `reset clears items and re-enables hasMore`() = runTest {
        val state = PagedState<Int>()
        state.loadInitial(
            fetch = { FakePage(listOf(1, 2), nextPageUrl = "u1") },
            fetchNext = { _ -> FakePage(emptyList()) },
        )
        state.reset()
        assertEquals(emptyList<Int>(), state.items.value)
        assertTrue(state.hasMore.value)
        assertNull(state.error.value)
    }
}
