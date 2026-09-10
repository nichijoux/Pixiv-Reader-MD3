package com.pixiv.reader.core.ui.component.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [parseChangelogMarkdown] 解析器单测：标题 / 列表 / 分隔线 / 行内标记 / 链接 / 图片丢弃。 */
class ChangelogMarkdownTest {

    /** 单测用样式集：仅结构与颜色可断言即可，字号无关解析行为 */
    private val styles = ChangelogStyles(
        headingLarge = SpanStyle(fontWeight = FontWeight.Bold),
        headingSmall = SpanStyle(fontWeight = FontWeight.Bold),
        code = SpanStyle(fontFamily = FontFamily.Monospace),
        rule = SpanStyle(color = Color.Gray),
        link = TextLinkStyles(style = SpanStyle(color = Color.Blue)),
    )

    @Test
    fun `标题去除井号并加粗`() {
        val parsed = parseChangelogMarkdown("## 更新内容", styles)
        assertEquals("更新内容", parsed.text)
        val span = parsed.spanStyles.firstOrNull()
        assertEquals(FontWeight.Bold, span?.item?.fontWeight)
        assertEquals(0, span?.start)
        assertEquals("更新内容".length, span?.end)
    }

    @Test
    fun `无序列表渲染为圆点且粗体标记去除`() {
        val parsed = parseChangelogMarkdown("- 修复了 **崩溃** 问题", styles)
        assertEquals("•  修复了 崩溃 问题", parsed.text)
        assertFalse(parsed.text.contains("**"))
        // 「崩溃」区间带加粗 span
        val bold = parsed.spanStyles.firstOrNull { it.item.fontWeight == FontWeight.Bold }
        assertNotNull(bold)
        assertEquals("•  修复了 ".length, bold?.start)
        assertEquals("•  修复了 崩溃".length, bold?.end)
    }

    @Test
    fun `嵌套列表按前导空格缩进`() {
        val parsed = parseChangelogMarkdown("- 一级\n  - 二级", styles)
        assertEquals("•  一级\n    •  二级", parsed.text)
    }

    @Test
    fun `分隔线渲染为细线字符行`() {
        val parsed = parseChangelogMarkdown("---", styles)
        assertEquals("─".repeat(24), parsed.text)
    }

    @Test
    fun `行内代码去除反引号`() {
        val parsed = parseChangelogMarkdown("升级到 `v1.2` 版本", styles)
        assertEquals("升级到 v1.2 版本", parsed.text)
        assertFalse(parsed.text.contains('`'))
    }

    @Test
    fun `markdown 链接转为可点击链接注解`() {
        val parsed = parseChangelogMarkdown("见 [发布页](https://example.com/a) 说明", styles)
        assertEquals("见 发布页 说明", parsed.text)
        val link = parsed.getLinkAnnotations(0, parsed.length).firstOrNull()?.item
        assertTrue(link is LinkAnnotation.Url)
        assertEquals("https://example.com/a", (link as LinkAnnotation.Url).url)
    }

    @Test
    fun `裸 URL 自动识别为链接`() {
        val parsed = parseChangelogMarkdown("下载 https://example.com/x 安装包", styles)
        val link = parsed.getLinkAnnotations(0, parsed.length).firstOrNull()?.item
        assertEquals("https://example.com/x", (link as? LinkAnnotation.Url)?.url)
    }

    @Test
    fun `图片语法整段丢弃`() {
        val parsed = parseChangelogMarkdown("![logo](https://example.com/i.png) 正文", styles)
        assertFalse(parsed.text.contains("!["))
        assertFalse(parsed.text.contains("logo"))
        assertTrue(parsed.text.contains("正文"))
    }

    @Test
    fun `空行保留为段间空行`() {
        val parsed = parseChangelogMarkdown("第一段\n\n第二段", styles)
        assertEquals("第一段\n\n第二段", parsed.text)
    }

    @Test
    fun `斜体与有序列表`() {
        val parsed = parseChangelogMarkdown("1. *重点* 项", styles)
        assertEquals("1. 重点 项", parsed.text)
        assertTrue(parsed.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
    }
}
