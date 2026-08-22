package com.pixiv.reader.core.network.novel

import com.pixiv.api.model.Novel
import com.pixiv.reader.core.network.session.PixivRepository

/**
 * 系列分册全量拉取（last_order 游标循环分页，core 共享）。
 *
 * 供阅读器目录、系列页「选取部分下载」、导出整系列复用——游标解析与分页上限只维护一份，
 * 此前三处同构实现（ReaderSeriesToc / NovelExporter / NovelSeriesViewModel）合并于此。
 *
 * @param maxPages 分页防御上限（每页条数由 API 决定，默认 20 页足够绝大多数系列；
 *                 超大系列可放宽）
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

/** 从 next_url 解析 last_order 查询参数。 */
private fun parseLastOrder(nextUrl: String?): Int? {
    if (nextUrl.isNullOrBlank()) return null
    return nextUrl.substringAfter('?', "").split('&')
        .firstOrNull { it.startsWith("last_order=") }
        ?.substringAfter('=')
        ?.toIntOrNull()
}
