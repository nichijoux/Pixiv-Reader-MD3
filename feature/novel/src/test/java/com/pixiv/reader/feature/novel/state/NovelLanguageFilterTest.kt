package com.pixiv.reader.feature.novel.state

import com.pixiv.api.model.Novel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 数据来源：排行榜 `v1/novel/ranking`（无语言参数，客户端过滤）。 */
class NovelLanguageFilterTest {

    private fun novel(
        title: String? = null,
        caption: String? = null,
        language: String? = null,
    ): Novel = Novel(id = 1L, title = title, caption = caption, language = language)

    // ── `language` 字段优先 ──────────────────────────────────────────────────

    @Test
    fun `language字段ja 即使标题全汉字也判日语`() {
        val n = novel(title = "遠野物語", language = "ja")
        assertTrue(n.matchesLanguageFilter(NovelLanguageFilter.JAPANESE))
        assertFalse(n.matchesLanguageFilter(NovelLanguageFilter.CHINESE))
    }

    @Test
    fun `language字段zh-cn 即使简介含假名也判中文`() {
        // 中文小说简介里引用了日文假名，但接口明确 zh-cn → 字段优先
        val n = novel(caption = "参考「アニメ」制作", language = "zh-cn")
        assertTrue(n.matchesLanguageFilter(NovelLanguageFilter.CHINESE))
        assertFalse(n.matchesLanguageFilter(NovelLanguageFilter.JAPANESE))
    }

    @Test
    fun `language字段大小写不敏感`() {
        assertTrue(novel(title = "x", language = "JA").matchesLanguageFilter(NovelLanguageFilter.JAPANESE))
        assertTrue(novel(title = "x", language = "ZH-CN").matchesLanguageFilter(NovelLanguageFilter.CHINESE))
    }

    // ── `language=null` 启发式 ───────────────────────────────────────────────

    @Test
    fun `含假名判日语`() {
        val n = novel(title = "こんにちは世界")
        assertTrue(n.matchesLanguageFilter(NovelLanguageFilter.JAPANESE))
        assertFalse(n.matchesLanguageFilter(NovelLanguageFilter.CHINESE))
    }

    @Test
    fun `纯汉字无假名判中文`() {
        val n = novel(title = "远野物语")
        assertTrue(n.matchesLanguageFilter(NovelLanguageFilter.CHINESE))
        assertFalse(n.matchesLanguageFilter(NovelLanguageFilter.JAPANESE))
    }

    @Test
    fun `纯西文不落入中日任一档`() {
        val n = novel(title = "Hello World", caption = "A story")
        assertFalse(n.matchesLanguageFilter(NovelLanguageFilter.CHINESE))
        assertFalse(n.matchesLanguageFilter(NovelLanguageFilter.JAPANESE))
    }

    @Test
    fun `拼音汉字混合按假名判日语`() {
        // 有假名但无汉字（纯平假名）仍判日语
        val n = novel(title = "わたしの", caption = "きもち")
        assertTrue(n.matchesLanguageFilter(NovelLanguageFilter.JAPANESE))
        assertFalse(n.matchesLanguageFilter(NovelLanguageFilter.CHINESE))
    }

    // ── ALL 恒 true ─────────────────────────────────────────────────────────

    @Test
    fun `ALL 恒命中`() {
        assertTrue(novel(title = "Hello").matchesLanguageFilter(NovelLanguageFilter.ALL))
        assertTrue(novel(title = "无").matchesLanguageFilter(NovelLanguageFilter.ALL))
        assertTrue(novel(title = null, caption = null).matchesLanguageFilter(NovelLanguageFilter.ALL))
    }

    @Test
    fun `空标题简介无语言不命中中日`() {
        val n = novel()
        assertFalse(n.matchesLanguageFilter(NovelLanguageFilter.CHINESE))
        assertFalse(n.matchesLanguageFilter(NovelLanguageFilter.JAPANESE))
    }
}
