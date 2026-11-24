package com.pixiv.reader.core.network.fanbox

import com.pixiv.api.model.FanboxBlockLink
import com.pixiv.api.model.FanboxBlockStyle
import com.pixiv.api.model.FanboxEmbed
import com.pixiv.api.model.FanboxPost
import com.pixiv.api.model.FanboxPostBody
import org.jsoup.Jsoup

sealed interface FanboxSection {

    /** 段落文本，[links] 与 [boldSpans] 使用 UTF-16 下标区间。 */
    data class Paragraph(
        val text: String,
        val links: List<FanboxBlockLink> = emptyList(),
        val boldSpans: List<FanboxBlockStyle> = emptyList(),
    ) : FanboxSection

    data class Header(val text: String) : FanboxSection

    data class Image(
        val url: String?,
        val thumbnailUrl: String?,
        val width: Int,
        val height: Int,
    ) : FanboxSection

    data class File(val name: String?, val size: Long, val url: String?) : FanboxSection

    data class Embed(val label: String?, val url: String?) : FanboxSection
}

/** 解析 FANBOX 帖子正文为渲染段列表；无正文（未赞助受限帖或兜底缺 body）返回空列表。 */
fun parseFanboxBody(post: FanboxPost?): List<FanboxSection> {
    val body = post?.body ?: return emptyList()
    val sections = mutableListOf<FanboxSection>()
    when (post.type) {
        POST_TYPE_ARTICLE -> parseArticleBody(body, sections)
        POST_TYPE_IMAGE -> {
            appendTextParagraph(body.text, sections)
            body.images.orEmpty().forEach { img ->
                sections += FanboxSection.Image(img.originalUrl, img.thumbnailUrl, img.width, img.height)
            }
        }
        POST_TYPE_FILE -> {
            appendTextParagraph(body.text, sections)
            body.files.orEmpty().forEach { f ->
                sections += FanboxSection.File(f.name, f.size, f.url)
            }
        }
        POST_TYPE_VIDEO -> appendTextParagraph(body.text, sections)
        POST_TYPE_ENTRY -> parseEntryHtml(body.html, sections)
        else -> {
            if (body.blocks != null) parseArticleBody(body, sections) else appendTextParagraph(body.text, sections)
        }
    }
    return sections
}

private fun parseArticleBody(body: FanboxPostBody, out: MutableList<FanboxSection>) {
    val imageMap = body.imageMap.orEmpty()
    val fileMap = body.fileMap.orEmpty()
    val embedMap = body.embedMap.orEmpty()
    val urlEmbedMap = body.urlEmbedMap.orEmpty()
    body.blocks.orEmpty().forEach { block ->
        when (block.type) {
            BLOCK_TYPE_PARAGRAPH -> out += FanboxSection.Paragraph(
                text = block.text.orEmpty(),
                links = block.links.orEmpty(),
                boldSpans = block.styles.orEmpty().filter { it.type == STYLE_BOLD },
            )
            BLOCK_TYPE_HEADER -> out += FanboxSection.Header(block.text.orEmpty())
            BLOCK_TYPE_IMAGE -> imageMap[block.imageId]?.let { img ->
                out += FanboxSection.Image(img.originalUrl, img.thumbnailUrl, img.width, img.height)
            }
            BLOCK_TYPE_FILE -> fileMap[block.fileId]?.let { f ->
                out += FanboxSection.File(f.name, f.size, f.url)
            }
            BLOCK_TYPE_EMBED -> embedMap[block.embedId]?.let { embed ->
                out += FanboxSection.Embed(
                    label = embed.serviceProvider.orEmpty().ifEmpty { BLOCK_TYPE_EMBED },
                    url = buildEmbedUrl(embed),
                )
            }
            BLOCK_TYPE_URL_EMBED -> urlEmbedMap[block.urlEmbedId]?.let { ue ->
                out += FanboxSection.Embed(label = ue.host ?: ue.type, url = ue.url)
            }
        }
    }
}

private fun parseEntryHtml(html: String?, out: MutableList<FanboxSection>) {
    if (html.isNullOrBlank()) return
    val doc = runCatching { Jsoup.parse(html) }.getOrNull() ?: return
    doc.body().select("p, h1, h2, h3, h4, h5, h6, img, br + span").forEach { element ->
        when (element.tagName()) {
            "img" -> {
                val src = element.attr("abs:src").ifEmpty { element.attr("src") }
                if (src.isNotEmpty()) out += FanboxSection.Image(src, src, 0, 0)
            }
            else -> element.text().takeIf { it.isNotBlank() }?.let { out += FanboxSection.Paragraph(it) }
        }
    }
    // 未命中块级元素时退化为纯文本，避免整段正文丢失
    if (out.isEmpty()) {
        doc.body().text().takeIf { it.isNotBlank() }?.let { out += FanboxSection.Paragraph(it) }
    }
}

private fun appendTextParagraph(text: String?, out: MutableList<FanboxSection>) {
    if (!text.isNullOrBlank()) out += FanboxSection.Paragraph(text)
}

private fun buildEmbedUrl(embed: FanboxEmbed): String? {
    val contentId = embed.contentId ?: return null
    return when (embed.serviceProvider) {
        "twitter" -> "https://twitter.com/i/web/status/$contentId"
        "youtube" -> "https://www.youtube.com/watch?v=$contentId"
        else -> null
    }
}

private const val POST_TYPE_ARTICLE = "article"
private const val POST_TYPE_IMAGE = "image"
private const val POST_TYPE_FILE = "file"
private const val POST_TYPE_VIDEO = "video"
private const val POST_TYPE_ENTRY = "entry"

private const val BLOCK_TYPE_PARAGRAPH = "p"
private const val BLOCK_TYPE_HEADER = "header"
private const val BLOCK_TYPE_IMAGE = "image"
private const val BLOCK_TYPE_FILE = "file"
private const val BLOCK_TYPE_EMBED = "embed"
private const val BLOCK_TYPE_URL_EMBED = "url_embed"

private const val STYLE_BOLD = "bold"
