package com.pixiv.reader.core.network.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {

    // ── AppUpdateVersion.isNewer：语义化比较 ──

    @Test
    fun `remote patch newer`() {
        assertTrue(AppUpdateVersion.isNewer("1.2.3", "v1.2.4"))
    }

    @Test
    fun `remote minor and major newer`() {
        assertTrue(AppUpdateVersion.isNewer("1.2.3", "v1.3.0"))
        assertTrue(AppUpdateVersion.isNewer("1.2.3", "v2.0.0"))
    }

    @Test
    fun `same or older version not newer`() {
        assertFalse(AppUpdateVersion.isNewer("1.2.3", "v1.2.3"))
        assertFalse(AppUpdateVersion.isNewer("0.1.0", "v0.1.0"))
        assertFalse(AppUpdateVersion.isNewer("1.2.3", "v1.2.2"))
    }

    @Test
    fun `missing segments treated as zero`() {
        // 远程 v1.2.1 > 本地 1.2（=1.2.0）；远程 v1.2 < 本地 1.2.3
        assertTrue(AppUpdateVersion.isNewer("1.2", "v1.2.1"))
        assertFalse(AppUpdateVersion.isNewer("1.2.3", "v1.2"))
    }

    @Test
    fun `suffix segment uses leading digits`() {
        // 后缀（-beta/-rc）不参与比较，仅前导数字有效
        assertTrue(AppUpdateVersion.isNewer("1.2.3", "1.2.4-beta"))
        assertFalse(AppUpdateVersion.isNewer("1.2.3", "1.2.3-rc.1"))
    }

    @Test
    fun `invalid versions never newer`() {
        // 本地空串 / 远程缺失 / 纯文本 tag：宁可漏报不误报
        assertFalse(AppUpdateVersion.isNewer("", "v1.2.3"))
        assertFalse(AppUpdateVersion.isNewer("1.2.3", ""))
        assertFalse(AppUpdateVersion.isNewer("1.2.3", "latest"))
    }

    // ── parseRelease：GitHub 响应解析 ──

    private val checker = AppUpdateChecker()

    @Test
    fun `parse release extracts required fields`() {
        val json = """
            {"tag_name":"v1.2.3","name":"PixivReader v1.2.3","body":"## 更新\n- 修复若干问题",
             "html_url":"https://github.com/nichijoux/Pixiv-Reader-MD3/releases/tag/v1.2.3"}
        """.trimIndent()
        val release = checker.parseRelease(json)
        assertEquals("v1.2.3", release?.tagName)
        assertEquals("PixivReader v1.2.3", release?.name)
        assertEquals("## 更新\n- 修复若干问题", release?.body)
        assertEquals(
            "https://github.com/nichijoux/Pixiv-Reader-MD3/releases/tag/v1.2.3",
            release?.htmlUrl,
        )
    }

    @Test
    fun `parse release tolerates null optional fields`() {
        val json = """{"tag_name":"v0.1.0","name":null,"body":null,"html_url":null}"""
        val release = checker.parseRelease(json)
        assertEquals("v0.1.0", release?.tagName)
        assertEquals("", release?.name)
        assertEquals("", release?.body)
        // html_url 缺省回退到 Releases 列表页
        assertTrue(release?.htmlUrl?.startsWith("https://github.com/nichijoux/Pixiv-Reader-MD3/releases") == true)
    }

    @Test
    fun `parse release invalid input returns null`() {
        assertNull(checker.parseRelease("""{"name":"no tag"}"""))
        assertNull(checker.parseRelease(""))
        assertNull(checker.parseRelease("not json at all"))
    }
}
