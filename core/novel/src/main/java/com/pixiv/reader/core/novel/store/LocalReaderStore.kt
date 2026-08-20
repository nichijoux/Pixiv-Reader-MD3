package com.pixiv.reader.core.novel.store

import com.pixiv.reader.core.novel.model.NovelDocument

/**
 * 本地阅读文档传递：下载管理解析 TXT/EPUB 后暂存，`local_reader` 路由消费。
 */
object LocalReaderStore {

    private var pending: Pair<NovelDocument, String>? = null

    /** 暂存待阅读的本地文档与标题。 */
    fun set(document: NovelDocument, title: String) {
        pending = document to title
    }

    /** 取出并清空（仅一次有效）。 */
    fun consume(): Pair<NovelDocument, String>? {
        val p = pending ?: return null
        pending = null
        return p
    }
}
