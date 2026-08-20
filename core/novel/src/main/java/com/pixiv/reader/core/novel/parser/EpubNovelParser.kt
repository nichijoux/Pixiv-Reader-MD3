package com.pixiv.reader.core.novel.parser

import com.pixiv.reader.core.novel.model.NovelBlock
import com.pixiv.reader.core.novel.model.NovelDocument
import com.pixiv.reader.core.novel.model.assignCharRanges
import com.pixiv.reader.core.novel.model.buildFullText
import java.io.File
import java.util.zip.ZipInputStream
import org.jsoup.Jsoup

/**
 * EPUB 小说解析：解压 zip → container.xml → content.opf（manifest/spine）→
 * 按 spine 顺序读取章节 xhtml → 提取段落/标题/引用/分隔线 → [NovelDocument]。
 * 正文插图（img）跳过，文本为主。
 */
object EpubNovelParser {

    fun parse(file: File): NovelDocument? = runCatching {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    entries[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        // container.xml → opf 路径
        val containerXml = entries["META-INF/container.xml"]?.toString(Charsets.UTF_8)
            ?: return@runCatching null
        val opfPath = Jsoup.parse(containerXml).select("rootfile").attr("full-path")
        val opfBytes = entries[opfPath] ?: return@runCatching null
        val opfDoc = Jsoup.parse(opfBytes.toString(Charsets.UTF_8))
        val baseDir = opfPath.substringBeforeLast('/', "")
        // manifest：id → href
        val manifest = opfDoc.select("manifest item").associate { item ->
            item.attr("id") to item.attr("href")
        }
        // spine：章节顺序
        val spine = opfDoc.select("spine itemref").mapNotNull { item ->
            manifest[item.attr("idref")]
        }

        val blocks = mutableListOf<NovelBlock>()
        spine.forEach { href ->
            val fullHref = if (baseDir.isBlank()) href else "$baseDir/$href"
            val chapterBytes = entries[fullHref] ?: return@forEach
            val doc = Jsoup.parse(chapterBytes.toString(Charsets.UTF_8))
            doc.body()?.select("p, h1, h2, h3, h4, h5, h6, blockquote, hr")?.forEach { el ->
                when (el.tagName()) {
                    "p" -> blocks.add(NovelBlock.Paragraph(el.text()))
                    "blockquote" -> blocks.add(NovelBlock.Quote(el.text()))
                    "hr" -> blocks.add(NovelBlock.Separator())
                    else -> {
                        val level = el.tagName().substring(1).toIntOrNull() ?: 2
                        blocks.add(NovelBlock.Heading(el.text(), level.coerceIn(1, 6)))
                    }
                }
            }
        }
        if (blocks.isEmpty()) return@runCatching null
        val assigned = assignCharRanges(blocks)
        val fullText = buildFullText(assigned)
        NovelDocument(blocks = assigned, fullText = fullText, textLength = fullText.length)
    }.getOrNull()
}
