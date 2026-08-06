package com.pixiv.reader.feature.reader.data

import com.pixiv.api.model.Novel
import com.pixiv.reader.core.network.session.PixivRepository

/**
 * 系列目录加载器：循环分页拉取系列内全部小说。
 * 纯数据层（不持有 UI 状态），由 ReaderViewModel 的 buildToc 调用。
 */
class ReaderSeriesToc(
    private val pixivRepository: PixivRepository,
) {
    /** 拉取系列内全部小说（循环分页，防御最多 20 页）。 */
    suspend fun fetchSeriesNovels(seriesId: Long): List<Novel> {
        val result = mutableListOf<Novel>()
        var lastOrder: Int? = null
        repeat(20) {
            val resp = pixivRepository.api.getNovelSeries(seriesId, lastOrder)
            resp.novels?.let { result.addAll(it) }
            val next = resp.next_url
            if (next.isNullOrBlank()) return result
            lastOrder = parseLastOrder(next)
            if (lastOrder == null) return result
        }
        return result
    }

    /** 从 next_url 解析 last_order 查询参数。 */
    fun parseLastOrder(nextUrl: String?): Int? {
        if (nextUrl.isNullOrBlank()) return null
        return nextUrl.substringAfter('?', "").split('&')
            .firstOrNull { it.startsWith("last_order=") }
            ?.substringAfter('=')?.toIntOrNull()
    }
}
