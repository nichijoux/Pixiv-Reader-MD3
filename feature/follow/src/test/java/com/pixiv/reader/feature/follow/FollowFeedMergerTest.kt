package com.pixiv.reader.feature.follow

import com.pixiv.api.model.Illust
import com.pixiv.api.model.Novel
import com.pixiv.api.model.User
import com.pixiv.reader.feature.follow.data.FollowFeedItem
import com.pixiv.reader.feature.follow.data.FollowFeedMerger
import com.pixiv.reader.feature.follow.data.FollowType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowFeedMergerTest {

    private fun illust(id: Long, date: String?, userId: Long = 1L) = Illust(
        id = id,
        title = "illust-$id",
        create_date = date,
        user = User(id = userId, name = "u$userId"),
    )

    private fun novel(id: Long, date: String?, userId: Long = 1L) = Novel(
        id = id,
        title = "novel-$id",
        create_date = date,
        user = User(id = userId, name = "u$userId"),
    )

    // ── merge：混合排序 ──

    @Test
    fun `merge 混合双流按时间倒序`() {
        val illusts = listOf(illust(1, "2026-08-12T09:00:00+09:00"))
        val novels = listOf(
            novel(2, "2026-08-12T10:00:00+09:00"),
            novel(3, "2026-08-11T08:00:00+09:00"),
        )
        val merged = FollowFeedMerger.merge(illusts, novels)
        // 小说(10:00) → 插画(09:00) → 小说(08:00)
        assertEquals(
            listOf("novel-2", "illust-1", "novel-3"),
            merged.map { it.title() },
        )
    }

    @Test
    fun `merge 空时间排最后且稳定`() {
        val merged = FollowFeedMerger.merge(
            illusts = listOf(illust(1, null), illust(2, "2026-08-12T08:00:00+09:00")),
            novels = listOf(novel(3, null)),
        )
        assertEquals(
            listOf("illust-2", "illust-1", "novel-3"),
            merged.map { it.title() },
        )
    }

    @Test
    fun `merge 空输入返回空列表`() {
        assertTrue(FollowFeedMerger.merge(emptyList(), emptyList()).isEmpty())
    }

    // ── filter：类型 ──

    @Test
    fun `filter NOVEL 只留小说`() {
        val items = listOf<FollowFeedItem>(
            FollowFeedItem.IllustItem(illust(1, "2026-08-12T09:00:00+09:00")),
            FollowFeedItem.NovelItem(novel(2, "2026-08-12T10:00:00+09:00")),
        )
        val filtered = FollowFeedMerger.filter(items, FollowType.NOVEL, userId = null)
        assertEquals(listOf("novel-2"), filtered.map { it.title() })
    }

    @Test
    fun `filter ILLUST 只留插画`() {
        val items = listOf<FollowFeedItem>(
            FollowFeedItem.IllustItem(illust(1, "2026-08-12T09:00:00+09:00")),
            FollowFeedItem.NovelItem(novel(2, "2026-08-12T10:00:00+09:00")),
        )
        val filtered = FollowFeedMerger.filter(items, FollowType.ILLUST, userId = null)
        assertEquals(listOf("illust-1"), filtered.map { it.title() })
    }

    @Test
    fun `filter ALL 不过滤类型`() {
        val items = listOf<FollowFeedItem>(
            FollowFeedItem.IllustItem(illust(1, "2026-08-12T09:00:00+09:00")),
            FollowFeedItem.NovelItem(novel(2, "2026-08-12T10:00:00+09:00")),
        )
        assertEquals(2, FollowFeedMerger.filter(items, FollowType.ALL, null).size)
    }

    // ── filter：用户 ──

    @Test
    fun `filter 按用户过滤且跨类型`() {
        val items = listOf<FollowFeedItem>(
            FollowFeedItem.IllustItem(illust(1, "2026-08-12T09:00:00+09:00", userId = 1L)),
            FollowFeedItem.IllustItem(illust(2, "2026-08-12T08:00:00+09:00", userId = 2L)),
            FollowFeedItem.NovelItem(novel(3, "2026-08-12T10:00:00+09:00", userId = 1L)),
        )
        val filtered = FollowFeedMerger.filter(items, FollowType.ALL, userId = 1L)
        // filter 保持输入顺序（排序由 merge 负责）
        assertEquals(listOf("illust-1", "novel-3"), filtered.map { it.title() })
    }

    @Test
    fun `filter 类型加用户组合过滤`() {
        val items = listOf<FollowFeedItem>(
            FollowFeedItem.IllustItem(illust(1, "2026-08-12T09:00:00+09:00", userId = 1L)),
            FollowFeedItem.NovelItem(novel(3, "2026-08-12T10:00:00+09:00", userId = 1L)),
            FollowFeedItem.NovelItem(novel(4, "2026-08-12T11:00:00+09:00", userId = 2L)),
        )
        val filtered = FollowFeedMerger.filter(items, FollowType.NOVEL, userId = 1L)
        assertEquals(listOf("novel-3"), filtered.map { it.title() })
    }

    @Test
    fun `filter userId null 不过滤用户`() {
        val items = listOf<FollowFeedItem>(
            FollowFeedItem.IllustItem(illust(1, "2026-08-12T09:00:00+09:00", userId = 1L)),
            FollowFeedItem.IllustItem(illust(2, "2026-08-12T08:00:00+09:00", userId = 2L)),
        )
        assertEquals(2, FollowFeedMerger.filter(items, FollowType.ALL, null).size)
    }

    // ── filter：空输入 ──

    @Test
    fun `filter 空输入返回空`() {
        assertTrue(FollowFeedMerger.filter(emptyList(), FollowType.ALL, null).isEmpty())
    }

    private fun FollowFeedItem.title(): String = when (this) {
        is FollowFeedItem.IllustItem -> illust.title.orEmpty()
        is FollowFeedItem.NovelItem -> novel.title.orEmpty()
    }
}
