package com.pixiv.reader.feature.reader.data

/**
 * 全文搜索（纯函数，可单测）：在全文（忽略大小写）中搜索关键词，
 * 记录所有匹配的字符偏移，最多 [MAX_RESULTS] 条。
 */
object NovelTextSearch {
    const val MAX_RESULTS = 500

    fun search(fullText: String, query: String): List<Int> {
        if (query.isBlank()) return emptyList()
        val results = mutableListOf<Int>()
        var from = 0
        while (true) {
            val idx = fullText.indexOf(query, from, ignoreCase = true)
            if (idx < 0) break
            results.add(idx)
            from = idx + query.length
            if (results.size >= MAX_RESULTS) break
        }
        return results
    }
}
