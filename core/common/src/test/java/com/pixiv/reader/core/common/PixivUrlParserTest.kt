package com.pixiv.reader.core.common.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PixivUrlParserTest {

    @Test
    fun `小说详情链接带页码`() {
        assertEquals(
            PixivLink(PixivLinkType.NOVEL, 22721650L),
            PixivUrlParser.parse("https://www.pixiv.net/novel/show.php?id=22721650#3"),
        )
    }

    @Test
    fun `小说详情链接 id 非首个参数`() {
        assertEquals(
            PixivLink(PixivLinkType.NOVEL, 22721650L),
            PixivUrlParser.parse("https://www.pixiv.net/novel/show.php?lang=zh&id=22721650"),
        )
    }

    @Test
    fun `小说详情链接无 www`() {
        assertEquals(
            PixivLink(PixivLinkType.NOVEL, 123456L),
            PixivUrlParser.parse("http://pixiv.net/novel/show.php?id=123456"),
        )
    }

    @Test
    fun `小说系列链接`() {
        assertEquals(
            PixivLink(PixivLinkType.SERIES, 1342417L),
            PixivUrlParser.parse("https://www.pixiv.net/novel/series/1342417"),
        )
    }

    @Test
    fun `插画链接`() {
        assertEquals(
            PixivLink(PixivLinkType.ILLUST, 120622017L),
            PixivUrlParser.parse("https://www.pixiv.net/artworks/120622017"),
        )
    }

    @Test
    fun `漫画链接解析为插画详情`() {
        assertEquals(
            PixivLink(PixivLinkType.ILLUST, 987654L),
            PixivUrlParser.parse("https://www.pixiv.net/manga/987654"),
        )
    }

    @Test
    fun `用户主页链接`() {
        assertEquals(
            PixivLink(PixivLinkType.USER, 11459631L),
            PixivUrlParser.parse("https://www.pixiv.net/users/11459631"),
        )
    }

    @Test
    fun `用户收藏页链接`() {
        assertEquals(
            PixivLink(PixivLinkType.USER, 11459631L),
            PixivUrlParser.parse("https://www.pixiv.net/user/11459631/illustrations"),
        )
    }

    @Test
    fun `链接混在普通文字中`() {
        assertEquals(
            PixivLink(PixivLinkType.ILLUST, 123L),
            PixivUrlParser.parse("我复制了 https://www.pixiv.net/artworks/123 这个链接，快看看"),
        )
    }

    @Test
    fun `多链接时取第一个`() {
        assertEquals(
            PixivLink(PixivLinkType.NOVEL, 111L),
            PixivUrlParser.parse("https://www.pixiv.net/novel/show.php?id=111 https://www.pixiv.net/artworks/222"),
        )
    }

    @Test
    fun `小说详情新式链接`() {
        assertEquals(
            PixivLink(PixivLinkType.NOVEL, 22721650L),
            PixivUrlParser.parse("https://www.pixiv.net/novel/22721650"),
        )
    }

    @Test
    fun `小说详情新式链接带页码`() {
        assertEquals(
            PixivLink(PixivLinkType.NOVEL, 22721650L),
            PixivUrlParser.parse("https://www.pixiv.net/novel/22721650#3"),
        )
    }

    @Test
    fun `插画短链 i 前缀`() {
        assertEquals(
            PixivLink(PixivLinkType.ILLUST, 120622017L),
            PixivUrlParser.parse("https://www.pixiv.net/i/120622017"),
        )
    }

    @Test
    fun `旧式 illust_id 链接`() {
        assertEquals(
            PixivLink(PixivLinkType.ILLUST, 123456L),
            PixivUrlParser.parse("https://www.pixiv.net/illust_id=123456"),
        )
    }

    @Test
    fun `旧式用户 member 链接`() {
        assertEquals(
            PixivLink(PixivLinkType.USER, 11459631L),
            PixivUrlParser.parse("https://www.pixiv.net/member.php?id=11459631"),
        )
    }

    @Test
    fun `新式小说链接与系列区分`() {
        // novel/series 仍解析为系列；novel/{id} 为单本
        assertEquals(
            PixivLink(PixivLinkType.SERIES, 1342417L),
            PixivUrlParser.parse("https://www.pixiv.net/novel/series/1342417"),
        )
        assertEquals(
            PixivLink(PixivLinkType.NOVEL, 555L),
            PixivUrlParser.parse("https://www.pixiv.net/novel/555"),
        )
    }

    @Test
    fun `普通文本返回 null`() {
        assertNull(PixivUrlParser.parse("hello world"))
    }

    @Test
    fun `非 pixiv 域名返回 null`() {
        assertNull(PixivUrlParser.parse("https://example.com/artworks/123"))
    }

    @Test
    fun `空文本返回 null`() {
        assertNull(PixivUrlParser.parse(""))
    }

    @Test
    fun `id 非数字返回 null`() {
        assertNull(PixivUrlParser.parse("https://www.pixiv.net/artworks/abc"))
    }
}
