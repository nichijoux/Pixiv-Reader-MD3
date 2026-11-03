package com.pixiv.reader.core.comment.state

import com.pixiv.api.model.Comment
import org.junit.Assert.assertEquals
import org.junit.Test

class CommentSortTest {

    /** 构造带 ISO 时间戳的评论（id 用于断言顺序）。 */
    private fun comment(id: Long, date: String?) = Comment(id = id, date = date)

    @Test
    fun `最新在前按时间降序`() {
        val list = listOf(
            comment(1, "2026-11-01T10:00:00+09:00"),
            comment(2, "2026-11-03T10:00:00+09:00"),
            comment(3, "2026-11-02T10:00:00+09:00"),
        )
        val sorted = sortCommentsForDisplay(list, CommentSortOrder.NEWEST_FIRST)
        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.id })
    }

    @Test
    fun `最早在前按时间升序`() {
        val list = listOf(
            comment(1, "2026-11-01T10:00:00+09:00"),
            comment(2, "2026-11-03T10:00:00+09:00"),
            comment(3, "2026-11-02T10:00:00+09:00"),
        )
        val sorted = sortCommentsForDisplay(list, CommentSortOrder.OLDEST_FIRST)
        assertEquals(listOf(1L, 3L, 2L), sorted.map { it.id })
    }

    @Test
    fun `缺失日期空串兜底不崩溃`() {
        val list = listOf(
            comment(1, null),
            comment(2, "2026-11-02T10:00:00+09:00"),
            comment(3, "2026-11-01T10:00:00+09:00"),
        )
        // 降序：空串最小，排最后
        assertEquals(
            listOf(2L, 3L, 1L),
            sortCommentsForDisplay(list, CommentSortOrder.NEWEST_FIRST).map { it.id },
        )
        // 升序：空串最小，排最前
        assertEquals(
            listOf(1L, 3L, 2L),
            sortCommentsForDisplay(list, CommentSortOrder.OLDEST_FIRST).map { it.id },
        )
    }

    @Test
    fun `空列表排序安全`() {
        assertEquals(
            emptyList<Comment>(),
            sortCommentsForDisplay(emptyList(), CommentSortOrder.NEWEST_FIRST),
        )
        assertEquals(
            emptyList<Comment>(),
            sortCommentsForDisplay(emptyList(), CommentSortOrder.OLDEST_FIRST),
        )
    }
}
