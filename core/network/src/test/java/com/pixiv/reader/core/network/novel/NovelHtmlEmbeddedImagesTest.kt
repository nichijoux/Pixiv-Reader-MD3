package com.pixiv.reader.core.network.novel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * webview HTML 内嵌图提取（OAuth 鉴权兜底路径）测试：
 * `window.pixiv.novel` 对象的 images（上传图）→ uploadedimage URL 映射。
 */
class NovelHtmlEmbeddedImagesTest {

    @Test
    fun `extract uploaded images from window pixiv novel object`() {
        val html = """
            <html><body><script>
            window.pixiv={
            userLang:"zh",
            novel:{"id":"1","title":"t","text":"正文[uploadedimage:100]",
                "images":{"100":{"novelImageId":"100","sl":"2","urls":{
                    "240mw":"https://i.pximg.net/c/240x480_80/a.jpg",
                    "1200x1200":"https://i.pximg.net/c/1200x1200/b.jpg",
                    "original":"https://i.pximg.net/novel-cover-original/c.jpg"}}}},
            isOwnWork:false
            }
            </script></body></html>
        """.trimIndent()
        val result = extractHtmlEmbeddedImages(html)
        // 尺寸优先级：1200x1200 > 240mw > original
        assertEquals(1, result.size)
        assertEquals("https://i.pximg.net/c/1200x1200/b.jpg", result["uploadedimage:100"])
    }

    @Test
    fun `brace matching survives braces inside text string`() {
        // 正文 text 含未转义花括号：字符串感知匹配不能提前闭合 novel 对象
        val html = """
            <script>window.pixiv={novel:{"id":"1","text":"前面 { 中间 } 后面[uploadedimage:1]",
            "images":{"1":{"urls":{"original":"https://i.pximg.net/x/y.jpg"}}}},isOwnWork:false}</script>
        """.trimIndent()
        val result = extractHtmlEmbeddedImages(html)
        assertEquals("https://i.pximg.net/x/y.jpg", result["uploadedimage:1"])
    }

    @Test
    fun `empty images array yields empty maps`() {
        val html =
            """<script>window.pixiv={novel:{"id":"1","text":"x","images":[],"illusts":[]},isOwnWork:false}</script>"""
        val result = extractHtmlEmbeddedImages(html)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `no window pixiv returns empty map`() {
        assertTrue(extractHtmlEmbeddedImages("<html><body>plain text</body></html>").isEmpty())
        assertTrue(extractHtmlEmbeddedImages("").isEmpty())
    }

    @Test
    fun `malformed novel object returns empty map without crash`() {
        val html =
            """<script>window.pixiv={novel:{"id":"1","text":"x","images":{"1":{"bad}}},isOwnWork:false}</script>"""
        assertTrue(extractHtmlEmbeddedImages(html).isEmpty())
    }

    @Test
    fun `novelJson extraction anchors after window pixiv`() {
        // 页面其它位置先出现 "novel:" 字样（如 CSS/文案）不应干扰定位
        val html = """
            <style>.novel:before{content:""}</style>
            <script>window.pixiv={novel:{"id":"9","text":"t","images":{}},isOwnWork:false}</script>
        """.trimIndent()
        val json = extractNovelJson(html)
        assertEquals("""{"id":"9","text":"t","images":{}}""", json)
    }

    @Test
    fun `defineProperty form with images array`() {
        // 当前 pixiv 实际形式：Object.defineProperty(window, 'pixiv', { value: { novel: {...} } })
        // images 为数组元素（每项含 novelImageId + urls）
        val html = """
            <script>
            Object.defineProperty(window, 'pixiv', {
                value: {
                    sessionUserId: 41985843,
                    isV2: true,
                    userLang: "en",
                    novel: {"id":"28917446","title":"t","text":"正文[uploadedimage:25440838]",
                        "images":[{"novelImageId":"25440838","sl":"2","urls":{
                            "240mw":"https://i.pximg.net/c/240x480_80/a.jpg",
                            "1200x1200":"https://i.pximg.net/c/1200x1200/b_master1200.jpg",
                            "original":"https://i.pximg.net/novel-cover-original/c.jpg"}}]},
                    isOwnWork: false
                }
            });
            </script>
        """.trimIndent()
        val result = extractHtmlEmbeddedImages(html)
        assertEquals(1, result.size)
        assertEquals("https://i.pximg.net/c/1200x1200/b_master1200.jpg", result["uploadedimage:25440838"])
    }

    @Test
    fun `defineProperty form with images object`() {
        val html = """
            <script>
            Object.defineProperty(window, 'pixiv', {
                value: {
                    novel: {"id":"1","text":"x",
                        "images":{"100":{"novelImageId":"100","urls":{"original":"https://i.pximg.net/o.jpg"}}}},
                    isOwnWork: false
                }
            });
            </script>
        """.trimIndent()
        val result = extractHtmlEmbeddedImages(html)
        assertEquals("https://i.pximg.net/o.jpg", result["uploadedimage:100"])
    }

    @Test
    fun `defineProperty form novel object with huge escaped text`() {
        // 正文 text 为 \uXXXX 转义长文本，含 \" 转义引号，不影响对象闭合
        val html = """
            <script>
            Object.defineProperty(window, 'pixiv', {
                value: {
                    novel: {"id":"1","text":"\u5f00\u59cb{\u7ed3\u675f}\u0022\u5f15\u53f7[uploadedimage:7]",
                        "images":[{"novelImageId":"7","urls":{"original":"https://i.pximg.net/z.jpg"}}]},
                    isOwnWork: false
                }
            });
            </script>
        """.trimIndent()
        val result = extractHtmlEmbeddedImages(html)
        assertEquals("https://i.pximg.net/z.jpg", result["uploadedimage:7"])
    }
}
