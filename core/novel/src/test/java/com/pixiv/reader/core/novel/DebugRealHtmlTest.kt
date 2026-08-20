package com.pixiv.reader.core.novel.parser

import org.junit.Assert.assertTrue
import org.junit.Test

class DebugRealHtmlTest {

    @Test
    fun `真实 pixiv v2 HTML 能解析出正文`() {
        val html = javaClass.classLoader!!
            .getResourceAsStream("debug/26256802.html")!!
            .bufferedReader().use { it.readText() }
        assertTrue(html.length > 100_000)

        val doc = NovelParser.parse(html)
        println("blocks=${doc.blocks.size}, textLength=${doc.textLength}")
        if (doc.blocks.isEmpty()) {
            // 打印各阶段诊断
            println("EMPTY document")
        } else {
            println("fullText head: ${doc.fullText.take(200)}")
        }
        assertTrue(doc.blocks.isNotEmpty())
        assertTrue(doc.textLength > 1000)
    }
}
