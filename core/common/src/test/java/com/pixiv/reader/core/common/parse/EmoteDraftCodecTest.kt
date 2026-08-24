package com.pixiv.reader.core.common.parse

import org.junit.Assert.assertEquals
import org.junit.Test

class EmoteDraftCodecTest {

    private val tags = setOf("normal", "smile", "star", "heart")

    // ── encode ──────────────────────────────────────────────

    @Test
    fun `纯文本无表情时返回单个文本段`() {
        val segments = encodeEmoteDraft("你好世界", tags)
        assertEquals(listOf<EmoteSegment>(EmoteSegment.Text("你好世界")), segments)
    }

    @Test
    fun `空草稿返回空序列`() {
        assertEquals(emptyList<EmoteSegment>(), encodeEmoteDraft("", tags))
    }

    @Test
    fun `单表情解析为表情段`() {
        val segments = encodeEmoteDraft("(normal)", tags)
        assertEquals(listOf<EmoteSegment>(EmoteSegment.Emote("normal")), segments)
    }

    @Test
    fun `表情夹中文`() {
        val segments = encodeEmoteDraft("哈哈(normal)试试(star)结尾", tags)
        assertEquals(
            listOf(
                EmoteSegment.Text("哈哈"),
                EmoteSegment.Emote("normal"),
                EmoteSegment.Text("试试"),
                EmoteSegment.Emote("star"),
                EmoteSegment.Text("结尾"),
            ),
            segments,
        )
    }

    @Test
    fun `连续表情相邻排列`() {
        val segments = encodeEmoteDraft("(normal)(smile)(heart)", tags)
        assertEquals(
            listOf(EmoteSegment.Emote("normal"), EmoteSegment.Emote("smile"), EmoteSegment.Emote("heart")),
            segments,
        )
    }

    @Test
    fun `未知标签保持原文本不转图`() {
        val segments = encodeEmoteDraft("前(unknown_tag)后(smile)", tags)
        assertEquals(
            listOf(EmoteSegment.Text("前(unknown_tag)后"), EmoteSegment.Emote("smile")),
            segments,
        )
    }

    @Test
    fun `回复提及精确匹配含空格昵称`() {
        val segments = encodeEmoteDraft("@John Doe 你好(smile)", tags, mentionName = "John Doe")
        assertEquals(
            listOf(
                EmoteSegment.Mention("John Doe"),
                EmoteSegment.Text("你好"),
                EmoteSegment.Emote("smile"),
            ),
            segments,
        )
    }

    @Test
    fun `昵称不匹配时不解析提及`() {
        val segments = encodeEmoteDraft("@John Doe 你好", tags, mentionName = "Other")
        assertEquals(listOf<EmoteSegment>(EmoteSegment.Text("@John Doe 你好")), segments)
    }

    @Test
    fun `无回复目标时开头at文本保持原样`() {
        val segments = encodeEmoteDraft("@someone 打卡", tags)
        assertEquals(listOf<EmoteSegment>(EmoteSegment.Text("@someone 打卡")), segments)
    }

    // ── decode / 往返 ────────────────────────────────────────

    @Test
    fun `往返还原保持原文`() {
        val draft = "@张三 (normal)混合(star)文本(heart)"
        val roundTrip = encodeEmoteDraft(draft, tags, mentionName = "张三").toDraftText()
        assertEquals(draft, roundTrip)
    }

    @Test
    fun `往返还原含空格昵称`() {
        val draft = "@John Doe hi"
        val roundTrip = encodeEmoteDraft(draft, tags, mentionName = "John Doe").toDraftText()
        assertEquals(draft, roundTrip)
    }
}
