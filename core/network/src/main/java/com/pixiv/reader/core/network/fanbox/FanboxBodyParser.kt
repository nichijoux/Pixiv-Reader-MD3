package com.pixiv.reader.core.network.fanbox

import com.pixiv.api.model.FanboxBlockLink
import com.pixiv.api.model.FanboxBlockStyle
import com.pixiv.api.model.FanboxEmbed
import com.pixiv.api.model.FanboxPost
import com.pixiv.api.model.FanboxPostBody
import org.jsoup.Jsoup

/**
 * FANBOX 正文渲染段：把 [FanboxPostBody]（按 post 类型分家的原始结构）拍平成
 * 顺序无关的 UI 中立段列表，Compose 详情页按段类型分发渲染。
 */
sealed interface FanboxSection {

    /** 段落文本（含内嵌超链接与粗体装饰的 UTF-16 下标区间）。 */
    data class Paragraph(
        val text: String,
        val links: List<FanboxBlockLink> = emptyList(),
        val boldSpans: List<FanboxBlockStyle> = emptyList(),
    ) : FanboxSection

    /** 小节标题（article 的 header 块）。 */
    data class Header(val text: String) : FanboxSection

    /** 正文图片（点击可进全屏预览）。 */
    data class Image(
        val url: String?,
        val thumbnailUrl: String?,
        val width: Int,
        val height: Int,
    ) : FanboxSection

    /** 附件（站外下载，不在 App 内打开）。 */
    data class File(val name: String?, val size: Long, val url: String?) : FanboxSection

    /** 嵌入占位卡（twitter / youtube 外链或站内卡片，原生不渲染内容，点击跳网页）。 */
    data class Embed(val label: String?, val url: String?) : FanboxSection
}

/**
 * 解析 FANBOX 帖子正文为渲染段列表（纯函数，可单测）。
 *
 * 处理范围：
 * - 帖子为 null / 正文为 null（受限帖未赞助、post.get 兜底无 body）→ 空列表（正常态）；
 * - `article`：blocks + imageMap/fileMap/embedMap/urlEmbedMap，块里只存 id、资源去 map 查；
 * - `image` / `file`：text + 资源列表；`text`：只有 text；`video`：仅渲染 text（外链视频不播）；
 * - `entry`：整段 HTML，用 Jsoup 拆成段落与图片。
 *
 * @param post 帖子对象（取 type / body；null 视为无正文）
 * @return 顺序稳定的渲染段列表；无正文时为空列表
 */
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
            // 类型缺失 / 未知：按资源兜底——有 blocks 走 article，否则退化为 text
            if (body.blocks != null) parseArticleBody(body, sections) else appendTextParagraph(body.text, sections)
        }
    }
    return sections
}

/**
 * 解析 article 类型正文（blocks + 四张资源 map）。
 *
 * @param body 正文原始结构
 * @param out 输出段列表（追加写入）
 * @return 无返回值（结果写入 [out]）
 */
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

/**
 * 解析 entry 类型的整段 HTML：块级元素拍成段落，`<img>` 拍成图片段。
 * 富文本样式不保留（链接 / 加粗丢失），entry 多为博客导入的旧帖，保文字与图即可。
 *
 * @param html 原始 HTML（null / 空白直接返回）
 * @param out 输出段列表（追加写入）
 * @return 无返回值（结果写入 [out]）
 */
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
    // 选择器没匹配到任何块级元素时退化为全文文本，避免整段正文丢失
    if (out.isEmpty()) {
        doc.body().text().takeIf { it.isNotBlank() }?.let { out += FanboxSection.Paragraph(it) }
    }
}

/**
 * 追加纯文本段（text / video 类型的说明文字）。
 *
 * @param text 原文（null / 空白跳过）
 * @param out 输出段列表
 * @return 无返回值
 */
private fun appendTextParagraph(text: String?, out: MutableList<FanboxSection>) {
    if (!text.isNullOrBlank()) out += FanboxSection.Paragraph(text)
}

/**
 * 拼旧式嵌入的落点 URL（twitter → 推文页，youtube → 视频页，其余给 null 走占位卡）。
 *
 * @param embed 嵌入资源
 * @return 可跳转 URL；拼不出时 null
 */
private fun buildEmbedUrl(embed: FanboxEmbed): String? {
    val contentId = embed.contentId ?: return null
    return when (embed.serviceProvider) {
        "twitter" -> "https://twitter.com/i/web/status/$contentId"
        "youtube" -> "https://www.youtube.com/watch?v=$contentId"
        else -> null
    }
}

/** post.type 取值。 */
private const val POST_TYPE_ARTICLE = "article"
private const val POST_TYPE_IMAGE = "image"
private const val POST_TYPE_FILE = "file"
private const val POST_TYPE_VIDEO = "video"
private const val POST_TYPE_ENTRY = "entry"

/** block.type 取值。 */
private const val BLOCK_TYPE_PARAGRAPH = "p"
private const val BLOCK_TYPE_HEADER = "header"
private const val BLOCK_TYPE_IMAGE = "image"
private const val BLOCK_TYPE_FILE = "file"
private const val BLOCK_TYPE_EMBED = "embed"
private const val BLOCK_TYPE_URL_EMBED = "url_embed"

/** styles.type 目前仅有的取值。 */
private const val STYLE_BOLD = "bold"
