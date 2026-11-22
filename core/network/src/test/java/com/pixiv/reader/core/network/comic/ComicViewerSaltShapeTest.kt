package com.pixiv.reader.core.network.comic

import org.junit.Assert.assertNull
import org.junit.Test

class ComicViewerSaltShapeTest {

    /**
     * 验证无效节点类型和空 salt 统一返回空结果。
     *
     * @return 无返回值
     */
    @Test
    fun `rejects missing or invalid salt nodes`() {
        // 遍历各层的不匹配形状，并覆盖可被 Gson 转换成字符串的数字和布尔值。
        val payloads = listOf(
            "null",
            "[]",
            """{"props":null}""",
            """{"props":false}""",
            """{"props":{"pageProps":[]}}""",
            """{"props":{"pageProps":{"salt":null}}}""",
            """{"props":{"pageProps":{"salt":""}}}""",
            """{"props":{"pageProps":{"salt":42}}}""",
            """{"props":{"pageProps":{"salt":true}}}""",
        )
        payloads.forEach { json ->
            assertNull(json, ComicViewerSaltParser.parse("""<script id="__NEXT_DATA__">$json</script>"""))
        }
    }
}
