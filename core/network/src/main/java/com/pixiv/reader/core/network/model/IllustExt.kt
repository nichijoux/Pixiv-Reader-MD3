package com.pixiv.reader.core.network.model

import com.pixiv.api.model.Illust

/** 封面 URL 单源（medium 回退 square_medium，全项目同款 elvis 写法收敛到一处）。 */
val Illust.bestCoverUrl: String?
    get() = image_urls?.medium ?: image_urls?.square_medium

/**
 * 作品卡片快照 JSON（浏览历史 payloadJson / 下载索引 payloadJson 共用 schema）。
 * 字段装配与原各调用点手写实现逐字一致（org.json 装配，保持输出字节不变）：
 * id / title / coverUrl / width / height / bookmarks / pageCount / isBookmarked。
 *
 * @return 快照 JSON 字符串（字段值均经空值兜底，不含 JSON null）
 */
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

/** 作品单页信息（展示/原图 URL + 真实宽高） */
data class IllustPageInfo(
    val displayUrl: String?,
    val originalUrl: String?,
    val width: Int = 0,
    val height: Int = 0,
)

/**
 * 由作品 DTO 展开为页面列表。
 * 多图：meta_pages；单图：image_urls / meta_single_page。
 * 宽高为空时由网页接口（/ajax/illust/{id}/pages）补齐。
 */
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
