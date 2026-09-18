package com.pixiv.reader.core.network.comic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * COMIC viewer 页 salt 解析单测：正常 __NEXT_DATA__ 提取、script 属性顺序变化、
 * 块缺失、JSON 损坏四种形态。
 */
class ComicViewerSaltParserTest {

    /** 标准 Next.js SSR 输出形态（字段名对齐 2026-09 实测抓包）。 */
    @Test
    fun `parses salt from standard NEXT_DATA`() {
        val html = """
        <!DOCTYPE html>
        <html><body>
        <script id="__NEXT_DATA__" type="application/json">
        {"props":{"pageProps":{"salt":"VLA66CNb8SQ9aVquC6_qiRaQE09IMdwGUaJsxerxjDk",
        "htmlMetaData":{},"id":121786,"workId":8536,"enableInterstitial":false}},"page":"/viewer/stories/[id]"}
        </script>
        </body></html>
        """.trimIndent()
        assertEquals("VLA66CNb8SQ9aVquC6_qiRaQE09IMdwGUaJsxerxjDk", ComicViewerSaltParser.parse(html))
    }

    /** script 属性顺序与官方不同（如无 type 属性）时应仍能解析。 */
    @Test
    fun `parses salt when attribute order differs`() {
        val html = """
        <html><head></head><body>
        <script id="__NEXT_DATA__">{"props":{"pageProps":{"salt":"abc123"}}}</script>
        </body></html>
        """.trimIndent()
        assertEquals("abc123", ComicViewerSaltParser.parse(html))
    }

    /** 无 __NEXT_DATA__ 块（如 CF 挑战页 / 错误页）返回 null 而非抛出。 */
    @Test
    fun `returns null when NEXT_DATA missing`() {
        assertNull(ComicViewerSaltParser.parse("<html><body>Just an error page</body></html>"))
        assertNull(ComicViewerSaltParser.parse(""))
    }

    /** JSON 损坏 / salt 键缺失时返回 null 而非抛出。 */
    @Test
    fun `returns null on malformed json or missing salt`() {
        assertNull(ComicViewerSaltParser.parse("""<script id="__NEXT_DATA__">{broken</script>"""))
        assertNull(
            ComicViewerSaltParser.parse(
                """<script id="__NEXT_DATA__">{"props":{"pageProps":{"id":1}}}</script>""",
            ),
        )
    }
}
