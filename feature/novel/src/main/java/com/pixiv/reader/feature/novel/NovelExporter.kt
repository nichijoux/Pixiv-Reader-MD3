package com.pixiv.reader.feature.novel

import android.content.Context
import com.example.pixivapi.model.Novel
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.novel.NovelBlock
import com.pixiv.reader.core.novel.NovelDocument
import com.pixiv.reader.core.novel.htmlToPlainText
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/** 导出格式：TXT（纯文本跳过插图） / EPUB（标准电子书内嵌插图）。 */
enum class NovelExportFormat { TXT, EPUB }

/** 导出范围：当前小说单本 / 整个系列。 */
enum class NovelExportScope { CURRENT, SERIES }

/**
 * 小说导出引擎：把小说（单本或整个系列）导出为 TXT / EPUB 文件。
 *
 * - TXT：纯文本，正文插图位置跳过（[pixivimage]/[uploadedimage] 标记不输出）
 * - EPUB：EPUB3 标准 zip 容器，正文内嵌图片（下载失败则跳过该图）
 *
 * 输出目录：filesDir/Downloads/novels/
 * TODO(后续)：SAF / MediaStore 导出到公共 Downloads；"我的下载"管理页复用 download_entry 表；
 * WorkManager 后台队列 + 中断恢复。
 */
@Singleton
class NovelExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pixivRepository: PixivRepository,
    private val contentLoader: NovelContentLoader,
    private val downloadEntryDao: DownloadEntryDao,
) {
    private val exportDir: File
        get() = File(context.filesDir, "Downloads/novels").apply { mkdirs() }

    /** 导出当前单本小说，返回导出的文件（成功时写入下载索引）。 */
    suspend fun exportNovel(
        novel: Novel,
        format: NovelExportFormat,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val loaded = contentLoader.load(novel.id).getOrThrow()
            when (format) {
                NovelExportFormat.TXT -> buildTxtFile(listOf(loaded), seriesTitle = null)
                NovelExportFormat.EPUB -> buildEpubFile(listOf(loaded), seriesTitle = null, coverNovel = novel)
            }
        }.onSuccess { file ->
            recordDownload(novel, file, format, chapterCount = 1)
        }
    }

    /** 导出整个系列（循环分页拉全部章节，逐章抓取串行下载），返回导出的文件。 */
    suspend fun exportSeries(
        novel: Novel,
        format: NovelExportFormat,
        onProgress: (index: Int, total: Int) -> Unit = { _, _ -> },
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val seriesId = novel.series?.id ?: error("该小说不属于任何系列")
            val novels = fetchSeriesNovels(seriesId)
            if (novels.isEmpty()) error("系列中没有可下载的分册")
            val chapters = mutableListOf<Pair<Novel, NovelDocument>>()
            novels.forEachIndexed { index, chapter ->
                onProgress(index + 1, novels.size)
                chapters.add(contentLoader.load(chapter.id).getOrThrow())
            }
            val file = when (format) {
                NovelExportFormat.TXT -> buildTxtFile(chapters, seriesTitle = novel.series?.title)
                NovelExportFormat.EPUB -> buildEpubFile(chapters, seriesTitle = novel.series?.title, coverNovel = novel)
            }
            file to novels.size
        }.onSuccess { (file, chapterCount) ->
            recordDownload(novel, file, format, chapterCount)
        }.map { it.first }
    }

    /** 写入下载索引（targetType=novel，localPath=导出文件）。 */
    private suspend fun recordDownload(
        novel: Novel,
        file: File,
        format: NovelExportFormat,
        chapterCount: Int,
    ) {
        runCatching {
            downloadEntryDao.upsert(
                DownloadEntryEntity(
                    targetId = novel.id,
                    targetType = "novel",
                    title = "${novel.title.orEmpty()}（${format.name}）",
                    coverUrl = novel.image_urls?.medium ?: novel.image_urls?.square_medium,
                    localPath = file.path,
                    status = "done",
                    pageCount = chapterCount,
                ),
            )
        }
    }

    // ── TXT ──────────────────────────────────────────────────────────────────

    private fun buildTxtFile(
        chapters: List<Pair<Novel, NovelDocument>>,
        seriesTitle: String?,
    ): File {
        val first = chapters.first().first
        val fileName = "${sanitizeFileName(seriesTitle ?: first.title.orEmpty())}_${first.id}.txt"
        val file = File(exportDir, fileName)
        file.writeText(buildTxt(chapters, seriesTitle), Charsets.UTF_8)
        return file
    }

    // ── EPUB ─────────────────────────────────────────────────────────────────

    private suspend fun buildEpubFile(
        chapters: List<Pair<Novel, NovelDocument>>,
        seriesTitle: String?,
        coverNovel: Novel,
    ): File {
        val first = chapters.first().first
        val fileName = "${sanitizeFileName(seriesTitle ?: first.title.orEmpty())}_${first.id}.epub"
        val file = File(exportDir, fileName)
        val images = mutableListOf<EpubImage>()
        // 封面
        val coverUrl = coverNovel.image_urls?.medium ?: coverNovel.image_urls?.square_medium
        if (!coverUrl.isNullOrBlank()) {
            downloadImage(coverUrl)?.let { images += EpubImage("cover.jpg", it) }
        }
        // 正文内嵌图片（按章节/序号命名；未解析的 pixivimage 标记或失败图片直接跳过）
        chapters.forEachIndexed { ci, (_, document) ->
            document.blocks.filterIsInstance<NovelBlock.Image>().forEachIndexed { ii, img ->
                if (img.url.startsWith("pixivimage:") || img.url.startsWith("uploadedimage:")) return@forEachIndexed
                downloadImage(img.url)?.let { images += EpubImage("img_${ci}_${ii}.jpg", it) }
            }
        }
        file.writeBytes(buildEpub(chapters, seriesTitle, images))
        return file
    }

    /** 下载图片字节（带 Referer 的 imageClient；失败返回 null）。 */
    private suspend fun downloadImage(url: String): ByteArray? {
        return runCatching {
            val request = Request.Builder().url(url).build()
            pixivRepository.imageClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.bytes()
            }
        }.getOrNull()
    }

    // ── 系列分页（与 ReaderViewModel.fetchSeriesNovels 一致） ────────────────

    private suspend fun fetchSeriesNovels(seriesId: Long): List<Novel> {
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

    private fun parseLastOrder(nextUrl: String?): Int? {
        if (nextUrl.isNullOrBlank()) return null
        return nextUrl.substringAfter('?', "").split('&')
            .firstOrNull { it.startsWith("last_order=") }
            ?.substringAfter('=')
            ?.toIntOrNull()
    }
}

// ── EPUB 容器内部数据 ────────────────────────────────────────────────────────

/** EPUB 内嵌图片：ref 为 OEBPS/images 下的相对文件名（如 `img_0_1.jpg` / `cover.jpg`）。 */
internal data class EpubImage(
    val ref: String,
    val bytes: ByteArray,
    val mime: String = "image/jpeg",
)

private const val CONTAINER_XML = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""

// ── 纯函数（可单测） ─────────────────────────────────────────────────────────

/**
 * 生成 TXT 全文（纯函数，可测）。
 * 多章（系列）时每章前输出分隔线与章节标题；插图块直接跳过。
 */
internal fun buildTxt(
    chapters: List<Pair<Novel, NovelDocument>>,
    seriesTitle: String?,
): String {
    return buildString {
        val first = chapters.first().first
        appendLine(first.title.orEmpty())
        first.user?.name?.let { if (it.isNotBlank()) appendLine("作者：$it") }
        if (!seriesTitle.isNullOrBlank()) appendLine("系列：$seriesTitle")
        first.caption?.takeIf { it.isNotBlank() }?.let {
            appendLine("简介：${htmlToPlainText(it)}")
        }
        appendLine()
        chapters.forEach { (novel, document) ->
            if (chapters.size > 1) {
                appendLine("================")
                appendLine("【${novel.title.orEmpty()}】")
                appendLine("================")
            }
            document.blocks.forEach { block ->
                when (block) {
                    is NovelBlock.Paragraph -> appendLine(block.text)
                    is NovelBlock.Heading -> {
                        appendLine()
                        appendLine(block.text)
                        appendLine()
                    }
                    is NovelBlock.Quote -> appendLine("> ${block.text}")
                    is NovelBlock.Image -> Unit // TXT 模式跳过插图
                    is NovelBlock.Separator -> appendLine(block.symbol)
                }
            }
            appendLine()
        }
    }
}

/**
 * 生成 EPUB3 zip 字节（纯函数，可测）。
 * 图片已由调用方下载为 [EpubImage] 传入（缺失即不内嵌）。
 */
internal fun buildEpub(
    chapters: List<Pair<Novel, NovelDocument>>,
    seriesTitle: String?,
    images: List<EpubImage>,
): ByteArray {
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zip ->
        // EPUB 规范：mimetype 必须是第一个条目且不压缩（STORED）
        val mimetypeBytes = "application/epub+zip".toByteArray(Charsets.US_ASCII)
        zip.putNextEntry(
            ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimetypeBytes.size.toLong()
                crc = crc32(mimetypeBytes)
            },
        )
        zip.write(mimetypeBytes)
        zip.closeEntry()

        zip.writeEntry("META-INF/container.xml", CONTAINER_XML)
        zip.writeEntry("OEBPS/content.opf", buildOpf(chapters, seriesTitle, images))
        zip.writeEntry("OEBPS/nav.xhtml", buildNav(chapters))
        chapters.forEachIndexed { index, (novel, document) ->
            zip.writeEntry(
                "OEBPS/chapter_$index.xhtml",
                buildChapterXhtml(novel, document, chapterIndex = index, images = images),
            )
        }
        images.forEach { img ->
            zip.writeEntry("OEBPS/images/${img.ref}", img.bytes)
        }
    }
    return bytes.toByteArray()
}

/** 生成 content.opf（manifest / spine / metadata）。 */
internal fun buildOpf(
    chapters: List<Pair<Novel, NovelDocument>>,
    seriesTitle: String?,
    images: List<EpubImage>,
): String {
    val first = chapters.first().first
    val manifest = buildString {
        append("""<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""")
        chapters.forEachIndexed { index, _ ->
            append("""<item id="chapter_$index" href="chapter_$index.xhtml" media-type="application/xhtml+xml"/>""")
        }
        images.forEach { img ->
            val props = if (img.ref == "cover.jpg") " properties=\"cover-image\"" else ""
            append("""<item id="${img.ref}" href="images/${img.ref}" media-type="${img.mime}"$props/>""")
        }
    }
    val spine = buildString {
        chapters.forEachIndexed { index, _ -> append("""<itemref idref="chapter_$index"/>""") }
    }
    return """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
<dc:identifier id="uid">pixiv-novel-${first.id}</dc:identifier>
<dc:title>${escapeXml(seriesTitle ?: first.title.orEmpty())}</dc:title>
<dc:creator>${escapeXml(first.user?.name.orEmpty())}</dc:creator>
<dc:language>ja</dc:language>
<meta property="dcterms:modified">2026-01-01T00:00:00Z</meta>
</metadata>
<manifest>$manifest</manifest>
<spine>$spine</spine>
</package>"""
}

/** 生成 EPUB3 导航文档（toc）。 */
internal fun buildNav(chapters: List<Pair<Novel, NovelDocument>>): String {
    val items = chapters.mapIndexed { index, (novel, _) ->
        """<li><a href="chapter_$index.xhtml">${escapeXml(novel.title.orEmpty())}</a></li>"""
    }.joinToString("\n")
    return """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>目录</title></head>
<body>
<nav epub:type="toc">
<h1>目录</h1>
<ol>
$items
</ol>
</nav>
</body>
</html>"""
}

/** 生成单章 xhtml（blocks → 语义化 HTML；插图成功下载才嵌 <img>）。 */
internal fun buildChapterXhtml(
    novel: Novel,
    document: NovelDocument,
    chapterIndex: Int,
    images: List<EpubImage>,
): String {
    val title = escapeXml(novel.title.orEmpty())
    val body = buildString {
        append("<h1>$title</h1>\n")
        var imgIndex = 0
        document.blocks.forEach { block ->
            when (block) {
                is NovelBlock.Paragraph -> append("<p>${escapeXml(block.text)}</p>\n")
                is NovelBlock.Heading -> {
                    val level = block.level.coerceIn(2, 6)
                    append("<h$level>${escapeXml(block.text)}</h$level>\n")
                }
                is NovelBlock.Quote -> append("<blockquote><p>${escapeXml(block.text)}</p></blockquote>\n")
                is NovelBlock.Separator -> append("<hr/>\n")
                is NovelBlock.Image -> {
                    val ref = "img_${chapterIndex}_${imgIndex}.jpg"
                    imgIndex++
                    if (images.any { it.ref == ref }) {
                        append("""<img src="images/$ref" alt="插图"/>""").append('\n')
                    }
                    // 未下载成功则不输出 <img>
                }
            }
        }
    }
    return """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>$title</title></head>
<body>
$body
</body>
</html>"""
}

// ── 工具 ─────────────────────────────────────────────────────────────────────

private fun ZipOutputStream.writeEntry(name: String, content: String) {
    writeEntry(name, content.toByteArray(Charsets.UTF_8))
}

private fun ZipOutputStream.writeEntry(name: String, content: ByteArray) {
    putNextEntry(ZipEntry(name))
    write(content)
    closeEntry()
}

private fun crc32(bytes: ByteArray): Long {
    val crc = CRC32()
    crc.update(bytes)
    return crc.value
}

/** XML 转义（纯函数，可测）。 */
internal fun escapeXml(s: String): String = buildString {
    for (c in s) {
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }
}

/** 文件名清洗：替换文件系统非法字符（纯函数，可测）。 */
internal fun sanitizeFileName(name: String): String =
    name.replace(Regex("""[\\/:*?"<>|\r\n]"""), "_")
        .trim()
        .ifBlank { "novel" }
        .take(80)
