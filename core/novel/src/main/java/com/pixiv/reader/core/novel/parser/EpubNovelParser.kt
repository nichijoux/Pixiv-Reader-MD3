package com.pixiv.reader.core.novel.parser

import com.pixiv.reader.core.novel.model.NovelBlock
import com.pixiv.reader.core.novel.model.NovelDocument
import com.pixiv.reader.core.novel.model.assignCharRanges
import com.pixiv.reader.core.novel.model.buildFullText
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipInputStream
import org.jsoup.Jsoup

/**
 * EPUB 小说解析：解压 zip → container.xml → content.opf（manifest/spine）→
 * 按 spine 顺序读取章节 xhtml → 提取段落/标题/引用/分隔线 → [NovelDocument]。
 * 正文插图（img）跳过，文本为主。
 */
object EpubNovelParser {

    /**
     * 解析 EPUB 文件为结构化小说文档。
     *
     * @param file 本地 EPUB 文件
     * @return 解析得到的 [NovelDocument]；文件损坏、缺少 OPF 或无有效正文块时返回 null
     */
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
        val opfPath = Jsoup.parse(containerXml).select("rootfile").attr("full-path").trimStart('/')
        val opfBytes = findZipEntry(entries, opfPath) ?: return@runCatching null
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
            val chapterBytes = findChapterEntry(entries, baseDir, href) ?: return@forEach
            val doc = Jsoup.parse(chapterBytes.toString(Charsets.UTF_8))
            val body = doc.body() ?: return@forEach
            body.select("p, h1, h2, h3, h4, h5, h6, blockquote, hr").forEach { el ->
                // blockquote 内部嵌套的 p 已包含在 blockquote 文本中，跳过避免重复成段。
                if (el.tagName() == "p" && el.parents().any { it.tagName() == "blockquote" }) {
                    return@forEach
                }
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

    /** 根据 OPF 基准目录规范化 `../`、`.` 与 URL 编码后查找章节字节。 */
    private fun findChapterEntry(
        entries: Map<String, ByteArray>,
        baseDir: String,
        rawHref: String,
    ): ByteArray? {
        val clean = rawHref.substringBefore('#').substringBefore('?').trim()
        if (clean.isEmpty()) return null
        val normalized = normalizeRelativePath(baseDir, clean)
        entries[normalized]?.let { return it }
        val decoded = runCatching { URLDecoder.decode(clean, Charsets.UTF_8.name()) }.getOrNull()
        if (decoded != null && decoded != clean) {
            val decodedNormalized = normalizeRelativePath(baseDir, decoded)
            entries[decodedNormalized]?.let { return it }
        }
        return null
    }

    /** 兼容前导斜杠或 URL 编码后查 zip 条目。 */
    private fun findZipEntry(entries: Map<String, ByteArray>, path: String): ByteArray? {
        val clean = path.trimStart('/')
        return entries[clean] ?: runCatching {
            entries[URLDecoder.decode(clean, Charsets.UTF_8.name())]
        }.getOrNull()
    }

    /** 折叠路径中的 `.`、`..` 与多余斜杠，得到 zip 内的规范相对路径。 */
    private fun normalizeRelativePath(baseDir: String, href: String): String {
        val combined = if (baseDir.isBlank() || href.startsWith('/')) href else "$baseDir/$href"
        val stack = ArrayDeque<String>()
        combined.replace('\\', '/').split('/').forEach { segment ->
            when {
                segment.isEmpty() || segment == "." -> Unit
                segment == ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(segment)
            }
        }
        return stack.joinToString("/")
    }
}
