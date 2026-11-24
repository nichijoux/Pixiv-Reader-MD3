package com.pixiv.reader.core.network.model

import com.pixiv.api.model.Illust

/** 封面 URL 的唯一取值口径：medium 回退 square_medium。 */
val Illust.bestCoverUrl: String?
    get() = image_urls?.medium ?: image_urls?.square_medium

/** 卡片快照 JSON：浏览历史与下载索引的 payloadJson 共用同一 schema。 */
fun Illust.snapshotPayload(): String = org.json.JSONObject().apply {
    put("id", id)
    put("title", title.orEmpty())
    put("coverUrl", bestCoverUrl)
    put("width", width)
    put("height", height)
    put("bookmarks", total_bookmarks ?: 0)
    put("pageCount", page_count)
    put("isBookmarked", is_bookmarked == true)
}.toString()

data class IllustPageInfo(
    val displayUrl: String?,
    val originalUrl: String?,
    val width: Int = 0,
    val height: Int = 0,
)

/** 多图取 meta_pages，单图取 image_urls / meta_single_page；宽高为 0 时由网页接口补齐。 */
fun Illust.toPages(): List<IllustPageInfo> =
    if (page_count > 1) {
        meta_pages.orEmpty().map { mp ->
            val urls = mp.image_urls
            IllustPageInfo(
                displayUrl = urls?.large ?: urls?.medium,
                originalUrl = urls?.original,
            )
        }
    } else {
        val urls = image_urls
        listOf(
            IllustPageInfo(
                displayUrl = urls?.large ?: urls?.medium,
                originalUrl = meta_single_page?.original_image_url ?: urls?.original,
            ),
        )
    }
