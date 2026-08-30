package com.pixiv.reader.core.ui.component.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [parseCaptionHtml] 解析器单测：换行 / 样式 / pixiv 深链注解 / 危险标签丢弃。 */
class HtmlCaptionTextTest {

    private val linkColor = Color(0xFF1976D2)

    @Test
    fun `用户示例 HTML 无标签残留且有换行`() {
        val html = "<strong><a href=\"pixiv://illusts/149009962\">制作精良</a></strong><br/><br/>一位制作精良<br/>正文"
        val parsed = parseCaptionHtml(html, linkColor)
        assertFalse(parsed.text.contains('<'))
        assertFalse(parsed.text.contains("href"))
        assertTrue(parsed.text.contains("一位制作精良"))
        assertTrue(parsed.text.contains("\n\n"))
        assertTrue(parsed.text.trimEnd().endsWith("正文"))
    }

    @Test
    fun `br 单换行与连续 br 空行`() {
        assertEquals("a\nb", parseCaptionHtml("a<br/>b", linkColor).text)
        assertEquals("a\n\nb", parseCaptionHtml("a<br/><br/>b", linkColor).text)
        // 三个及以上 br 压缩为两个
        assertEquals("a\n\nb", parseCaptionHtml("a<br/><br/><br/>b", linkColor).text)
    }

    @Test
    fun `pixiv 深链注解可查`() {
        val parsed = parseCaptionHtml("<a href=\"pixiv://illusts/123\">作品</a>", linkColor)
        val ann = parsed.getStringAnnotations("pixivLink", 0, 2).firstOrNull()
        assertEquals("illusts:123", ann?.item)
    }

    @Test
    fun `novels 与 users 深链注解`() {
        val novel = parseCaptionHtml("<a href=\"pixiv://novels/456\">小说</a>", linkColor)
        assertEquals("novels:456", novel.getStringAnnotations("pixivLink", 0, 2).first()?.item)
        val user = parseCaptionHtml("<a href=\"pixiv://users/789\">作者</a>", linkColor)
        assertEquals("users:789", user.getStringAnnotations("pixivLink", 0, 2).first()?.item)
    }

    @Test
    fun `非 pixiv 链接保留文字无注解`() {
        val parsed = parseCaptionHtml("<a href=\"https://example.com\">外部</a>", linkColor)
        assertEquals("外部", parsed.text)
        assertTrue(parsed.getStringAnnotations("pixivLink", 0, 2).isEmpty())
    }

    @Test
    fun `strong 与 u 样式覆盖文本`() {
        val parsed = parseCaptionHtml("<strong>加粗</strong> 和 <u>下划线</u>", linkColor)
        val boldSpan = parsed.spanStyles.firstOrNull { it.item.fontWeight == FontWeight.Bold }
        assertEquals("加粗", parsed.text.substring(boldSpan?.start ?: 0, boldSpan?.end ?: 0))
        val underlineSpan = parsed.spanStyles.firstOrNull {
            it.item.textDecoration == TextDecoration.Underline
        }
        assertEquals("下划线", parsed.text.substring(underlineSpan?.start ?: 0, underlineSpan?.end ?: 0))
    }

    @Test
    fun `script 与 img 丢弃`() {
        val html = "<script>alert(1)</script>正文<img src=\"https://i.pximg.net/x.png\"/>结束"
        val parsed = parseCaptionHtml(html, linkColor)
        assertFalse(parsed.text.contains("alert"))
        assertFalse(parsed.text.contains("pximg"))
        assertTrue(parsed.text.contains("正文"))
        assertTrue(parsed.text.contains("结束"))
    }

    @Test
    fun `空白与空输入`() {
        assertEquals("", parseCaptionHtml("", linkColor).text)
        assertEquals("", parseCaptionHtml("   ", linkColor).text)
        assertEquals("你好 世界", parseCaptionHtml("你好\n世界", linkColor).text.replace("\n", " "))
    }

    @Test
    fun `块级标签前后换行`() {
        val parsed = parseCaptionHtml("<p>段落一</p><p>段落二</p>", linkColor)
        assertTrue(parsed.text.contains("段落一\n\n段落二"))
    }
}
