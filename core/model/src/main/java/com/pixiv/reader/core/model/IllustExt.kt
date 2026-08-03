package com.pixiv.reader.core.model

import com.example.pixivapi.model.Illust

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
