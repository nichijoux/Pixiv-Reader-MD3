package com.pixiv.reader.core.novel.store

import com.pixiv.reader.core.novel.model.NovelDocument

/**
 * 内存单槽，不按 novelId 存储；[consume] 取出即清空（单次消费语义）。
 */
object LocalReaderStore {

    private var pending: Pair<NovelDocument, String>? = null

    fun set(document: NovelDocument, title: String) {
        pending = document to title
    }

    fun consume(): Pair<NovelDocument, String>? {
        val p = pending ?: return null
        pending = null
        return p
    }
}
