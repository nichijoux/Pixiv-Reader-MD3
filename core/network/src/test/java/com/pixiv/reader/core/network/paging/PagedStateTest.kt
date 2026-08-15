package com.pixiv.reader.core.network.paging

import com.pixiv.api.Pageable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
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

@OptIn(ExperimentalCoroutinesApi::class)
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
        assertFalse(state.isLoading.value)
    }

    @Test
    fun `reset during in-flight load allows immediate reload and discards stale result`() = runTest {
        val state = PagedState<Int>()
        // 首载 fetch 挂起（模拟慢请求，isLoading 已置位）
        val releaseFirst = CompletableDeferred<Unit>()
        val firstLoad = launch {
            state.loadInitial(
                fetch = {
                    releaseFirst.await()
                    FakePage(listOf(1, 2))
                },
                fetchNext = { _ -> FakePage(emptyList()) },
            )
        }
        runCurrent() // 推进到 fetch 挂起
        assertTrue(state.isLoading.value)

        // 下拉刷新语义：首载未完成时 reset + 重新 loadInitial 必须真正重拉（不被 isLoading 幂等忽略）
        state.reset()
        assertFalse(state.isLoading.value)
        state.loadInitial(
            fetch = { FakePage(listOf(7, 8, 9)) },
            fetchNext = { _ -> FakePage(emptyList()) },
        )
        assertEquals(listOf(7, 8, 9), state.items.value)
        assertFalse(state.isLoading.value)

        // 放行旧请求：过期代次结果必须丢弃，不能覆盖新数据
        releaseFirst.complete(Unit)
        runCurrent()
        assertEquals(listOf(7, 8, 9), state.items.value)
        firstLoad.join()
    }

    @Test
    fun `reset during in-flight loadMore discards stale append and unblocks new loadMore`() = runTest {
        val state = PagedState<Int>()
        // 第 2 页 fetch 挂起（模拟慢请求，isLoadingMore 已置位）
        val releaseSecond = CompletableDeferred<Unit>()
        state.loadInitial(
            fetch = { FakePage(listOf(1, 2), nextPageUrl = "u1") },
            fetchNext = { _ ->
                releaseSecond.await()
                FakePage(listOf(3, 4))
            },
        )
        val stale = launch { state.loadMore() }
        runCurrent() // 推进到 fetch 挂起
        assertTrue(state.isLoadingMore.value)

        // 新搜索语义：reset 作废旧代次并清掉旧 loadMore 标志
        state.reset()
        assertFalse(state.isLoadingMore.value)
        state.loadInitial(
            fetch = { FakePage(listOf(7, 8, 9), nextPageUrl = "u2") },
            fetchNext = { _ -> FakePage(listOf(5, 6)) },
        )
        assertEquals(listOf(7, 8, 9), state.items.value)

        // 放行旧代次 loadMore：过期结果必须丢弃，不能混入新列表
        releaseSecond.complete(Unit)
        runCurrent()
        assertEquals(listOf(7, 8, 9), state.items.value)

        // 新代次 loadMore 不被旧请求卡死，正常追加
        state.loadMore()
        assertEquals(listOf(7, 8, 9, 5, 6), state.items.value)
        assertFalse(state.isLoadingMore.value)
        stale.join()
    }
}
