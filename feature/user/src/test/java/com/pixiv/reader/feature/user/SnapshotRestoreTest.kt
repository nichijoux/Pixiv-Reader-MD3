package com.pixiv.reader.feature.user

import com.google.gson.Gson
import com.pixiv.reader.feature.user.data.restoreNovelCardData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotRestoreTest {

    private val gson = Gson()

    @Test
    fun `restoreNovelCardData 缺失计数回退兜底值并恢复 serverMuted`() {
        val jsonWithZeroAndMuted = """
            {
              "id": 1001,
              "title": "标题",
              "favoriteCount": 0,
              "wordCount": 0,
              "serverMuted": true
            }
        """.trimIndent()

        val restoredMuted = restoreNovelCardData(
            payloadJson = jsonWithZeroAndMuted,
            gson = gson,
            fallbackId = 1001L,
            fallbackTitle = "兜底标题",
            fallbackCoverUrl = null,
            fallbackFavoriteCount = 88,
            fallbackWordCount = 3200,
        )
        assertEquals(88, restoredMuted.favoriteCount)
        assertEquals(3200, restoredMuted.wordCount)
        assertTrue(restoredMuted.serverMuted)

        val jsonWithPositiveAndMissingMuted = """
            {
              "id": 1002,
              "title": "标题2",
              "favoriteCount": 120,
              "wordCount": 4500
            }
        """.trimIndent()
        val restoredUnmuted = restoreNovelCardData(
            payloadJson = jsonWithPositiveAndMissingMuted,
            gson = gson,
            fallbackId = 1002L,
            fallbackTitle = "兜底标题",
            fallbackCoverUrl = null,
            fallbackFavoriteCount = 10,
            fallbackWordCount = 20,
        )
        assertEquals(120, restoredUnmuted.favoriteCount)
        assertEquals(4500, restoredUnmuted.wordCount)
        assertFalse(restoredUnmuted.serverMuted)
    }
}
