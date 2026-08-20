package com.pixiv.reader.feature.reader.data

import com.pixiv.api.model.Novel
import com.pixiv.reader.core.novel.model.NovelDocument
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 阅读器章节缓存（进程级单例）：阅读时预加载 + 缓存系列章节，上下章跳转秒开。
 *
 * - 章节缓存：`LinkedHashMap` LRU（accessOrder=true），上限 [MAX_CHAPTERS] 章；
 *   当前章加载成功后写入（跳回上一章命中），预加载下一章后写入（跳下一章秒开）。
 * - 系列目录缓存：`seriesId → List<Novel>`，跳转后新阅读器 buildToc 直接命中，
 *   目录立即显示并高亮新位置（无需等待网络）。
 *
 * 缓存的是解析后的内存对象（非持久化），进程结束即失效；上限防长时间阅读内存膨胀。
 */
@Singleton
class ReaderChapterCache @Inject constructor() {

    /** 缓存条目：详情 + 解析后正文。 */
    data class Entry(val novel: Novel, val document: NovelDocument)

    private val chapterCache = object : LinkedHashMap<Long, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<Long, Entry>?,
        ): Boolean = size > MAX_CHAPTERS
    }

    private val tocCache = ConcurrentHashMap<Long, List<Novel>>()

    /** 读取章节缓存；未命中返回 null。 */
    @Synchronized
    fun getChapter(novelId: Long): Entry? = chapterCache[novelId]

    /** 写入章节缓存（LRU 超限自动淘汰最久未访问项）。 */
    @Synchronized
    fun putChapter(novelId: Long, entry: Entry) {
        chapterCache[novelId] = entry
    }

    /** 读取系列目录缓存；未命中返回 null。 */
    fun getToc(seriesId: Long): List<Novel>? = tocCache[seriesId]

    /** 写入系列目录缓存。 */
    fun putToc(seriesId: Long, novels: List<Novel>) {
        if (novels.isNotEmpty()) tocCache[seriesId] = novels
    }

    private companion object {
        /** 章节缓存上限（当前 + 上一章 + 下一章 + 余量）。 */
        const val MAX_CHAPTERS = 6
    }
}
