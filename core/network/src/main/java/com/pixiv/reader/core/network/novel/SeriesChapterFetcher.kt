package com.pixiv.reader.core.network.novel

import com.pixiv.api.model.Novel
import com.pixiv.reader.core.network.session.PixivRepository

/**
 * 系列分册全量拉取（last_order 游标循环分页），阅读器目录 / 部分下载 / 导出整系列共用。
 * maxPages 仅防御无底系列：每页条数由 API 决定。
 */
suspend fun fetchAllSeriesChapters(
    pixivRepository: PixivRepository,
    seriesId: Long,
    maxPages: Int = 20,
): List<Novel> {
    val result = mutableListOf<Novel>()
    var lastOrder: Int? = null
    repeat(maxPages) {
        val resp = pixivRepository.api.getNovelSeries(seriesId, lastOrder)
        resp.novels?.let { result.addAll(it) }
        val next = resp.next_url
        if (next.isNullOrBlank()) return result
        lastOrder = parseLastOrder(next) ?: return result
    }
    return result
}

private fun parseLastOrder(nextUrl: String?): Int? {
    if (nextUrl.isNullOrBlank()) return null
    return nextUrl.substringAfter('?', "").split('&')
        .firstOrNull { it.startsWith("last_order=") }
        ?.substringAfter('=')
        ?.toIntOrNull()
}
