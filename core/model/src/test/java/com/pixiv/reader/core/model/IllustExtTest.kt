package com.pixiv.reader.core.model

import com.example.pixivapi.model.Illust
import com.example.pixivapi.model.ImageUrls
import com.example.pixivapi.model.MetaPage
import com.example.pixivapi.model.MetaSinglePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IllustExtTest {

    @Test
    fun `single page uses image_urls and meta_single_page`() {
        val illust = Illust(
            id = 1L,
            page_count = 1,
            image_urls = ImageUrls(medium = "m", original = "o"),
            meta_single_page = MetaSinglePage(original_image_url = "orig"),
        )
        val pages = illust.toPages()
        assertEquals(1, pages.size)
        assertEquals("m", pages[0].displayUrl)
        assertEquals("orig", pages[0].originalUrl)
    }

    @Test
    fun `multi page maps meta_pages preserving original urls`() {
        val illust = Illust(
            id = 2L,
            page_count = 3,
            meta_pages = listOf(
                MetaPage(image_urls = ImageUrls(medium = "m0", original = "o0")),
                MetaPage(image_urls = ImageUrls(medium = "m1", original = "o1")),
                MetaPage(image_urls = ImageUrls(medium = "m2", original = "o2")),
            ),
        )
        val pages = illust.toPages()
        assertEquals(3, pages.size)
        assertEquals("m0", pages[0].displayUrl)
        assertEquals("o2", pages[2].originalUrl)
    }

    @Test
    fun `missing urls are null-safe`() {
        val illust = Illust(id = 3L, page_count = 1)
        val pages = illust.toPages()
        assertEquals(1, pages.size)
        assertNull(pages[0].displayUrl)
        assertNull(pages[0].originalUrl)
    }
}
