package com.pixiv.reader.feature.reader.data

import com.pixiv.api.model.Novel
import com.pixiv.reader.core.novel.model.NovelDocument
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 进程级内存 LRU 缓存（上限 [MAX_CHAPTERS] 章 + 系列目录），进程结束即失效，非持久化离线缓存。
 * 供上下章切换秒开与新阅读器复用目录，避免重复拉取与解析。
 */
@Singleton
class ReaderChapterCache @Inject constructor() {

    data class Entry(val novel: Novel, val document: NovelDocument)

    private val chapterCache = object : LinkedHashMap<Long, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Long, Entry>?,
        ): Boolean = size > MAX_CHAPTERS
    }

    private val tocCache = ConcurrentHashMap<Long, List<Novel>>()

    @Synchronized
    fun getChapter(novelId: Long): Entry? = chapterCache[novelId]

    @Synchronized
    fun putChapter(novelId: Long, entry: Entry) {
        chapterCache[novelId] = entry
    }

    fun getToc(seriesId: Long): List<Novel>? = tocCache[seriesId]

    fun putToc(seriesId: Long, novels: List<Novel>) {
        if (novels.isNotEmpty()) tocCache[seriesId] = novels
    }

    private companion object {
        const val MAX_CHAPTERS = 6
    }
}
