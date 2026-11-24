package com.pixiv.reader.feature.reader.data

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
