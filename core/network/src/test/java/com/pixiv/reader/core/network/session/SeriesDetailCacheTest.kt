package com.pixiv.reader.core.network.session

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 并发去重与缓存命中回归：旧实现（eager async + 无条件 remove）会重复发请求并误删胜者表项。 */
class SeriesDetailCacheTest {

    @Test
    fun `并发调用同一 seriesId 只发一次请求且结果一致`() = runBlocking {
        val cache = SeriesDetailCache()
        val calls = AtomicInteger(0)
        val expected = SeriesDetailInfo(coverUrl = "cover", caption = "caption", isConcluded = false)

        val results = (1..5).map {
            async {
                cache.getOrFetch(42L) {
                    calls.incrementAndGet()
                    // 拉长请求窗口，确保 5 个调用方都进入并发竞争区间
                    delay(200)
                    expected
                }
            }
        }.awaitAll()

        assertEquals("并发去重后应只发一次网络请求", 1, calls.get())
        results.forEach { assertEquals(expected, it) }
    }

    @Test
    fun `命中缓存后不再执行 fetcher`() = runBlocking {
        val cache = SeriesDetailCache()
        val calls = AtomicInteger(0)
        val expected = SeriesDetailInfo(coverUrl = "c", caption = null, isConcluded = true)

        val first = cache.getOrFetch(7L) {
            calls.incrementAndGet()
            expected
        }
        val second = cache.getOrFetch(7L) {
            calls.incrementAndGet()
            expected
        }

        assertEquals(expected, first)
        assertEquals(expected, second)
        assertEquals("缓存命中不应再请求", 1, calls.get())
        assertTrue("同步查询也应命中缓存", cache.get(7L) === expected)
    }
}
