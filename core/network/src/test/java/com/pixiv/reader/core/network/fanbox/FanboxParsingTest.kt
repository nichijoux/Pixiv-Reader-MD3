package com.pixiv.reader.core.network.fanbox

import com.google.gson.Gson
import com.pixiv.api.model.FanboxPost
import com.pixiv.api.model.FanboxPostList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FANBOX 正文解析纯函数单测（PRD §11）：article blocks + map、image 列表形态、
 * 受限帖空 body、entry HTML、coverUrl 双来源、FanboxPostList 的 Pageable 映射。
 */
class FanboxParsingTest {

    private val gson = Gson()

    /** article 类型：blocks + imageMap/embedMap 混排，块里只存 id、资源去 map 查。 */
    @Test
    fun `article blocks resolve resources from maps`() {
        val json = """
        {
          "id": "p1", "title": "测试帖", "type": "article",
          "body": {
            "blocks": [
              {"type": "header", "text": "标题一"},
              {"type": "p", "text": "第一段",
               "links": [{"offset": 0, "length": 3, "url": "https://www.fanbox.cc/posts/p1"}],
               "styles": [{"type": "bold", "offset": 0, "length": 3}]},
              {"type": "image", "imageId": "img1"},
              {"type": "embed", "embedId": "e1"},
              {"type": "url_embed", "urlEmbedId": "u1"}
            ],
            "imageMap": {"img1": {"id": "img1", "width": 1200, "height": 800,
              "originalUrl": "https://downloads.fanbox.cc/images/o1", "thumbnailUrl": "https://downloads.fanbox.cc/images/t1"}},
            "embedMap": {"e1": {"id": "e1", "serviceProvider": "twitter", "contentId": "123"}},
            "urlEmbedMap": {"u1": {"id": "u1", "type": "default", "url": "https://example.com", "host": "example.com"}}
          }
        }
        """.trimIndent()
        val post = gson.fromJson(json, FanboxPost::class.java)
        val sections = parseFanboxBody(post)

        assertEquals(5, sections.size)
        assertTrue(sections[0] is FanboxSection.Header)
        assertEquals("标题一", (sections[0] as FanboxSection.Header).text)

        val paragraph = sections[1] as FanboxSection.Paragraph
        assertEquals("第一段", paragraph.text)
        assertEquals(1, paragraph.links.size)
        assertEquals(1, paragraph.boldSpans.size)

        val image = sections[2] as FanboxSection.Image
        assertEquals("https://downloads.fanbox.cc/images/o1", image.url)
        assertEquals(1200, image.width)

        val embed = sections[3] as FanboxSection.Embed
        assertEquals("https://twitter.com/i/web/status/123", embed.url)

        val urlEmbed = sections[4] as FanboxSection.Embed
        assertEquals("https://example.com", urlEmbed.url)
    }

    /** image 类型：text + images 列表（无 blocks）。 */
    @Test
    fun `image post renders text then images`() {
        val json = """
        {
          "id": "p2", "type": "image",
          "body": {
            "text": "图集说明",
            "images": [
              {"id": "i1", "width": 100, "height": 200, "originalUrl": "https://downloads.fanbox.cc/a"},
              {"id": "i2", "width": 300, "height": 100, "originalUrl": "https://downloads.fanbox.cc/b"}
            ]
          }
        }
        """.trimIndent()
        val sections = parseFanboxBody(gson.fromJson(json, FanboxPost::class.java))

        assertEquals(3, sections.size)
        assertEquals("图集说明", (sections[0] as FanboxSection.Paragraph).text)
        assertEquals("https://downloads.fanbox.cc/a", (sections[1] as FanboxSection.Image).url)
        assertEquals(300, (sections[2] as FanboxSection.Image).width)
    }

    /** 受限帖 / post.get 兜底：body 为 null → 空列表（正常态非失败）。 */
    @Test
    fun `null body yields empty sections`() {
        val post = gson.fromJson("""{"id": "p3", "title": "受限帖", "isRestricted": true}""", FanboxPost::class.java)
        assertTrue(parseFanboxBody(post).isEmpty())
        assertTrue(parseFanboxBody(null).isEmpty())
    }

    /** entry 类型：整段 HTML 拆段落 + 图片。 */
    @Test
    fun `entry html splits into paragraphs and images`() {
        val json = """
        {"id": "p4", "type": "entry",
         "body": {"html": "<p>第一段</p><p>第二段</p><img src=\"https://downloads.fanbox.cc/e1\">" }}
        """.trimIndent()
        val sections = parseFanboxBody(gson.fromJson(json, FanboxPost::class.java))

        assertEquals(3, sections.size)
        assertEquals("第一段", (sections[0] as FanboxSection.Paragraph).text)
        assertEquals("第二段", (sections[1] as FanboxSection.Paragraph).text)
        assertEquals("https://downloads.fanbox.cc/e1", (sections[2] as FanboxSection.Image).url)
    }

    /** coverUrl 双来源：列表接口 cover.url 优先，post.info 平铺 coverImageUrl 兜底。 */
    @Test
    fun `coverUrl prefers structured cover then flat field`() {
        val fromList = gson.fromJson(
            """{"id": "p5", "cover": {"type": "cover_image", "url": "https://downloads.fanbox.cc/c1"}}""",
            FanboxPost::class.java,
        )
        assertEquals("https://downloads.fanbox.cc/c1", fromList.coverUrl)

        val fromInfo = gson.fromJson(
            """{"id": "p6", "coverImageUrl": "https://downloads.fanbox.cc/c2"}""",
            FanboxPost::class.java,
        )
        assertEquals("https://downloads.fanbox.cc/c2", fromInfo.coverUrl)

        val missing = gson.fromJson("""{"id": "p7"}""", FanboxPost::class.java)
        assertEquals("", missing.coverUrl)
    }

    /** FanboxPostList 的 Pageable 映射：items / nextPageUrl（缺字段安全兜底）。 */
    @Test
    fun `postList maps pageable items and cursor`() {
        val list = gson.fromJson(
            """{"items": [{"id": "a"}, {"id": "b"}], "nextUrl": "https://api.fanbox.cc/post.listHome?offset=20"}""",
            FanboxPostList::class.java,
        )
        assertEquals(listOf("a", "b"), list.items.map { it.id })
        assertEquals("https://api.fanbox.cc/post.listHome?offset=20", list.nextPageUrl)
        assertTrue(list.hasMore)

        val empty = gson.fromJson("{}", FanboxPostList::class.java)
        assertTrue(empty.items.isEmpty())
        assertNull(empty.nextPageUrl)
    }
}
