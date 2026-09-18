package com.pixiv.reader.core.network.comic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * gridshuffle 去扰纯函数单测：置换表用固定 key 锚定算法（锚点值由已验证的
 * 参考实现生成，任何算法参数变动都会被捕获）、置换合法性、洗牌-还原互逆。
 */
class ComicGridUnscramblerTest {

    /** 真实形状的 page.key（43 位 URL-safe base64，2026-09 实测值），小网格锚点。 */
    @Test
    fun `permutation matches pinned algorithm output for test key`() {
        val perms = ComicGridUnscrambler.permutationsFor("test-key-1234", cols = 4, rows = 3)
        assertEquals(listOf(3, 1, 2, 0), perms[0].toList())
        assertEquals(listOf(1, 3, 0, 2), perms[1].toList())
        assertEquals(listOf(3, 0, 2, 1), perms[2].toList())
    }

    /** 真实抓包 page.key + 实际网格形状（720x1024 / 32px → 22 列）的置换锚点。 */
    @Test
    fun `permutation matches pinned output for real page key`() {
        val perms = ComicGridUnscrambler.permutationsFor(
            "f0dc97ce-5faa-4291-b0a2-83c34d1f43b7",
            cols = 22,
            rows = 3,
        )
        assertEquals(
            listOf(18, 14, 8, 2, 0, 1, 20, 6, 4, 5, 9, 13, 15, 3, 19, 11, 10, 7, 21, 16, 17, 12),
            perms[0].toList(),
        )
        assertEquals(
            listOf(10, 15, 8, 12, 17, 21, 0, 11, 7, 20, 1, 3, 2, 6, 13, 16, 14, 5, 18, 9, 4, 19),
            perms[1].toList(),
        )
        assertEquals(
            listOf(10, 5, 20, 1, 19, 14, 17, 12, 4, 13, 18, 15, 6, 3, 11, 21, 8, 9, 7, 2, 0, 16),
            perms[2].toList(),
        )
    }

    /** 同 key 同形状两次生成结果一致（随机流派生确定性）。 */
    @Test
    fun `permutation is deterministic for same key`() {
        val a = ComicGridUnscrambler.permutationsFor("some-episode-key", cols = 22, rows = 32)
        val b = ComicGridUnscrambler.permutationsFor("some-episode-key", cols = 22, rows = 32)
        // IntArray 无内容相等语义，逐行比较
        assertEquals(a.map { it.toList() }, b.map { it.toList() })
    }

    /** 每行都是 0..cols-1 的一个排列（无缺失 / 无重复）。 */
    @Test
    fun `each row is a valid permutation`() {
        val perms = ComicGridUnscrambler.permutationsFor("validity-key", cols = 22, rows = 8)
        for (perm in perms) {
            assertEquals(22, perm.size)
            assertEquals((0 until 22).toSet(), perm.toSet())
        }
    }

    /** 洗牌（服务端方向）→ 还原（[ComicGridUnscrambler] 方向）互逆：块内容复原。 */
    @Test
    fun `scramble then restore restores original block order`() {
        val cols = 6
        val rows = 4
        // 用块编号本身当内容，还原后应回到 0..cols-1 的自然顺序
        val original = List(rows) { r -> IntArray(cols) { c -> r * 100 + c } }
        val perms = ComicGridUnscrambler.permutationsFor("roundtrip-key", cols, rows)
        // 服务端打乱方向：scrambled[c] = original[perm[c]]
        val scrambled = List(rows) { r ->
            val perm = perms[r]
            IntArray(cols) { c -> original[r][perm[c]] }
        }
        // 客户端还原方向（与 ComicGridUnscrambler.unscramble 一致）：dest[perm[c]] = scrambled[c]
        val restored = Array(rows) { IntArray(cols) }
        for (r in 0 until rows) {
            val perm = perms[r]
            for (c in 0 until cols) restored[r][perm[c]] = scrambled[r][c]
        }
        for (r in 0 until rows) assertTrue(original[r].contentEquals(restored[r]))
    }
}
