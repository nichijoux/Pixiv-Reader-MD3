package com.pixiv.reader.feature.novel

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.pixiv.api.model.ImageUrls
import com.pixiv.api.model.Novel
import com.pixiv.api.model.Series
import com.pixiv.api.model.User
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.datastore.UserPreferences
import com.pixiv.reader.core.novel.NovelBlock
import com.pixiv.reader.core.novel.NovelDocument
import com.pixiv.reader.core.novel.NovelDocumentCodec
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import com.tom_roush.fontbox.ttf.TrueTypeCollection
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font

/** 导出格式：TXT（纯文本跳过插图） / EPUB（标准电子书内嵌插图） / PDF / MARKDOWN / DOCX。 */
enum class NovelExportFormat { TXT, EPUB, PDF, MARKDOWN, DOCX }

/** 导出范围：当前小说单本 / 整个系列。 */
enum class NovelExportScope { CURRENT, SERIES }

/**
 * 小说导出引擎：把小说（单本或整个系列）导出为 TXT / EPUB / PDF / MARKDOWN / DOCX 文件。
 *
 * - TXT：纯文本，正文插图位置跳过（[pixivimage]/[uploadedimage] 标记不输出）
 * - EPUB：EPUB3 标准 zip 容器，正文内嵌图片（下载失败则跳过该图）
 * - PDF：pdfbox-android 排版，中日文依赖系统字体
 * - MARKDOWN / DOCX：文本 / OOXML 容器
 *
 * 输出目录：默认 `filesDir/Downloads/novels/`；用户可在「我的-下载位置」通过 SAF
 * 指定任意目录（如系统 Download），配置为空时走默认私有目录。
 * 断点缓存始终留在私有目录（不可见，导出成功后清理）。
 */
@Singleton
class NovelExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pixivRepository: PixivRepository,
    private val contentLoader: NovelContentLoader,
    private val downloadEntryDao: DownloadEntryDao,
    private val userPreferences: UserPreferences,
) {
    private val exportDir: File
        get() = File(context.filesDir, "Downloads/novels").apply { mkdirs() }

    /** 导出目录缓存子目录（断点重下：章节文档临时缓存，导出成功后清理）。 */
    private fun cacheDir(novelId: Long, format: NovelExportFormat): File =
        File(
            context.filesDir,
            "Downloads/novels/.export_${novelId}_${format.name}"
        ).apply { mkdirs() }

    /**
     * 断点续传导出：单本或整个系列导出为指定格式文件。
     *
     * 每章下载成功后缓存到临时目录（[cacheDir]）；中断重跑（WorkManager 自动重试 /
     * 下载管理页手动重试）时已缓存章节直接读盘，只补缺失章节，实现断点重下。
     *
     * @param novelId 小说 ID（同时是下载索引 targetId）
     * @param format 导出格式
     * @param seriesId 所属系列 ID；>0 时导出整个系列，否则单本
     * @return 导出的本地路径（应用内文件路径 或 content:// uri）
     */
    suspend fun exportResumable(
        novelId: Long,
        format: NovelExportFormat,
        seriesId: Long? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        markDownloading(novelId, format, seriesId)
        runCatching {
            val (chapters, coverNovel) = loadChaptersWithCache(novelId, format, seriesId)
            if (chapters.isEmpty()) error("没有可导出的章节")
            val localPath = when (format) {
                NovelExportFormat.TXT -> buildTxtFile(
                    chapters,
                    seriesTitle = coverNovel.series?.title
                )

                NovelExportFormat.EPUB -> buildEpubFile(
                    chapters,
                    seriesTitle = coverNovel.series?.title,
                    coverNovel = coverNovel
                )

                NovelExportFormat.MARKDOWN -> buildMarkdownFile(
                    chapters,
                    seriesTitle = coverNovel.series?.title
                )

                NovelExportFormat.DOCX -> buildDocxFile(
                    chapters,
                    seriesTitle = coverNovel.series?.title
                )

                NovelExportFormat.PDF -> buildPdfFile(
                    chapters,
                    seriesTitle = coverNovel.series?.title
                )
            }
            ExportResult(localPath, coverNovel, chapters.size)
        }.onSuccess { result ->
            recordDownload(
                novelId,
                format,
                seriesId,
                result.coverNovel,
                result.localPath,
                result.chapterCount
            )
            // 导出成功清理临时缓存
            cacheDir(novelId, format).deleteRecursively()
        }.onFailure { markFailed(novelId, format, seriesId) }
            .map { it.localPath }
    }

    /** 加载章节（断点）：已缓存章节读盘，缺失章节网络下载并写缓存；按章推进进度。 */
    private suspend fun loadChaptersWithCache(
        novelId: Long,
        format: NovelExportFormat,
        seriesId: Long?,
    ): Pair<List<Pair<Novel, NovelDocument>>, Novel> {
        val cache = cacheDir(novelId, format)
        val chapters = if (seriesId != null && seriesId > 0L) {
            val novels = fetchSeriesNovels(seriesId)
            if (novels.isEmpty()) error("系列中没有可下载的分册")
            novels.mapIndexed { index, chapter ->
                val pair = loadChapterCached(cache, chapter.id, index, novelId, format)
                updateProgress(novelId, format, seriesId, ((index + 1) * 100) / novels.size)
                pair
            }
        } else {
            listOf(
                loadChapterCached(cache, novelId, 0, novelId, format).also {
                    updateProgress(novelId, format, seriesId, 100)
                },
            )
        }
        if (chapters.isEmpty()) error("没有可导出的章节")
        return chapters to chapters.first().first
    }

    /** 单章加载：`chapter_{index}_{id}.json` 缓存命中直接读盘，否则网络加载并写缓存。 */
    private suspend fun loadChapterCached(
        cache: File,
        chapterId: Long,
        index: Int,
        rootId: Long,
        format: NovelExportFormat,
    ): Pair<Novel, NovelDocument> {
        val cacheFile = File(cache, "chapter_${index}_$chapterId.json")
        if (cacheFile.exists()) {
            decodeChapterCache(cacheFile.readText(Charsets.UTF_8))?.let { return it }
        }
        val loaded = contentLoader.load(chapterId).getOrThrow()
        runCatching { cacheFile.writeText(encodeChapterCache(loaded.first, loaded.second)) }
        return loaded
    }

    /** 章节缓存编码：Novel 元数据 + NovelDocument（org.json）。 */
    private fun encodeChapterCache(novel: Novel, document: NovelDocument): String {
        val obj = JSONObject()
        obj.put("id", novel.id)
        obj.put("title", novel.title.orEmpty())
        obj.put("caption", novel.caption.orEmpty())
        novel.series?.let {
            obj.put("seriesId", it.id)
            obj.put("seriesTitle", it.title.orEmpty())
        }
        novel.user?.let { obj.put("userName", it.name.orEmpty()) }
        novel.image_urls?.medium?.let { obj.put("cover", it) }
        obj.put("document", NovelDocumentCodec.encode(document))
        return obj.toString()
    }

    /** 章节缓存解码（损坏/缺失返回 null）。 */
    private fun decodeChapterCache(json: String): Pair<Novel, NovelDocument>? = runCatching {
        val obj = JSONObject(json)
        val id = obj.optLong("id")
        val doc = NovelDocumentCodec.decode(obj.optString("document")) ?: return@runCatching null
        val novel = Novel(
            id = id,
            title = obj.optString("title"),
            caption = obj.optString("caption").ifEmpty { null },
            image_urls = ImageUrls(medium = obj.optString("cover").ifEmpty { null }),
            series = if (obj.has("seriesId")) Series(
                obj.optLong("seriesId"),
                obj.optString("seriesTitle")
            ) else null,
            user = if (obj.has("userName")) User(id, obj.optString("userName")) else null,
        )
        novel to doc
    }.getOrNull()

    /** 写入下载索引（targetType=novel，localPath=导出文件路径或 content uri）。 */
    private suspend fun recordDownload(
        novelId: Long,
        format: NovelExportFormat,
        seriesId: Long?,
        coverNovel: Novel,
        localPath: String,
        chapterCount: Int,
    ) {
        upsertIndex(
            novelId = novelId,
            title = coverNovel.title,
            format = format,
            seriesId = seriesId,
            coverUrl = coverNovel.image_urls?.medium ?: coverNovel.image_urls?.square_medium,
            localPath = localPath,
            status = "done",
            progress = 100,
            chapterCount = chapterCount,
        )
    }

    /** 标记开始下载（downloading 中间态，进度 0）。 */
    private suspend fun markDownloading(novelId: Long, format: NovelExportFormat, seriesId: Long?) {
        upsertIndex(
            novelId = novelId, title = null, format = format, seriesId = seriesId, coverUrl = null,
            localPath = null, status = "downloading", progress = 0, chapterCount = 0,
        )
    }

    /** 更新下载进度（第 x/y 章 → 百分比）。 */
    private suspend fun updateProgress(
        novelId: Long,
        format: NovelExportFormat,
        seriesId: Long?,
        progress: Int,
    ) {
        upsertIndex(
            novelId = novelId, title = null, format = format, seriesId = seriesId, coverUrl = null,
            localPath = null, status = "downloading", progress = progress, chapterCount = 0,
        )
    }

    /** 标记下载失败（failed 状态，供下载管理页标记/重试）。 */
    private suspend fun markFailed(novelId: Long, format: NovelExportFormat, seriesId: Long?) {
        upsertIndex(
            novelId = novelId, title = null, format = format, seriesId = seriesId, coverUrl = null,
            localPath = null, status = "failed", progress = 0, chapterCount = 0,
        )
    }

    private suspend fun upsertIndex(
        novelId: Long,
        title: String?,
        format: NovelExportFormat,
        seriesId: Long?,
        coverUrl: String?,
        localPath: String?,
        status: String,
        progress: Int,
        chapterCount: Int,
    ) {
        runCatching {
            downloadEntryDao.upsert(
                DownloadEntryEntity(
                    targetId = novelId,
                    targetType = "novel",
                    title = title?.let { "$it（${format.name}）" },
                    coverUrl = coverUrl,
                    localPath = localPath,
                    status = status,
                    progress = progress,
                    pageCount = chapterCount,
                    seriesId = seriesId,
                    format = format.name,
                ),
            )
        }
    }

    /** 导出结果（本地路径 + 封面小说 + 章节数）。 */
    private data class ExportResult(
        val localPath: String,
        val coverNovel: Novel,
        val chapterCount: Int
    )

    // ── TXT ──────────────────────────────────────────────────────────────────

    private suspend fun buildTxtFile(
        chapters: List<Pair<Novel, NovelDocument>>,
        seriesTitle: String?,
    ): String {
        val first = chapters.first().first
        val fileName = "${sanitizeFileName(seriesTitle ?: first.title.orEmpty())}_${first.id}.txt"
        return writeExportFile(
            fileName,
            mimeFor(NovelExportFormat.TXT),
            buildTxt(chapters, seriesTitle).toByteArray(Charsets.UTF_8)
        )
    }

    // ── EPUB ─────────────────────────────────────────────────────────────────

    private suspend fun buildEpubFile(
        chapters: List<Pair<Novel, NovelDocument>>,
        seriesTitle: String?,
        coverNovel: Novel,
    ): String {
        val first = chapters.first().first
        val fileName = "${sanitizeFileName(seriesTitle ?: first.title.orEmpty())}_${first.id}.epub"
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
        return writeExportFile(
            fileName,
            mimeFor(NovelExportFormat.EPUB),
            buildEpub(chapters, seriesTitle, images)
        )
    }

    // ── MARKDOWN ─────────────────────────────────────────────────────────────

    private suspend fun buildMarkdownFile(
        chapters: List<Pair<Novel, NovelDocument>>,
        seriesTitle: String?,
    ): String {
        val first = chapters.first().first
        val fileName = "${sanitizeFileName(seriesTitle ?: first.title.orEmpty())}_${first.id}.md"
        return writeExportFile(
            fileName,
            mimeFor(NovelExportFormat.MARKDOWN),
            buildMarkdown(chapters, seriesTitle).toByteArray(Charsets.UTF_8)
        )
    }

    // ── DOCX（手写最小 OOXML 容器，纯段落文本） ──────────────────────────────

    private suspend fun buildDocxFile(
        chapters: List<Pair<Novel, NovelDocument>>,
        seriesTitle: String?,
    ): String {
        val first = chapters.first().first
        val fileName = "${sanitizeFileName(seriesTitle ?: first.title.orEmpty())}_${first.id}.docx"
        return writeExportFile(
            fileName,
            mimeFor(NovelExportFormat.DOCX),
            buildDocx(chapters, seriesTitle)
        )
    }

    // ── PDF（pdfbox-android；中日文依赖系统字体，仅真机验证） ────────────────

    private suspend fun buildPdfFile(
        chapters: List<Pair<Novel, NovelDocument>>,
        seriesTitle: String?,
    ): String {
        val first = chapters.first().first
        val fileName = "${sanitizeFileName(seriesTitle ?: first.title.orEmpty())}_${first.id}.pdf"
        // 复用 TXT 纯文本为排版源（插图不渲染）
        return writeExportFile(
            fileName,
            mimeFor(NovelExportFormat.PDF),
            buildPdf(buildTxt(chapters, seriesTitle))
        )
    }

    // ── 导出目标（默认私有目录 / 用户指定 SAF 目录） ─────────────────────────

    /**
     * 写入导出文件并返回本地定位串（应用内文件路径 或 content:// uri）。
     * 用户配置了 SAF tree uri 时写入指定目录（同名文件先删再建，避免重复），否则写默认私有目录。
     */
    private suspend fun writeExportFile(fileName: String, mime: String, bytes: ByteArray): String {
        val dirUri = userPreferences.novelExportDir.first()
        if (dirUri.isBlank()) {
            val file = File(exportDir, fileName)
            file.writeBytes(bytes)
            return file.path
        }
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(dirUri)) ?: error("导出目录不可用")
        tree.findFile(fileName)?.delete()
        val doc = tree.createFile(mime, fileName) ?: error("无法创建导出文件")
        context.contentResolver.openOutputStream(doc.uri)?.use { it.write(bytes) }
            ?: error("无法写入导出文件")
        return doc.uri.toString()
    }

    private fun mimeFor(format: NovelExportFormat): String = when (format) {
        NovelExportFormat.TXT -> "text/plain"
        NovelExportFormat.EPUB -> "application/epub+zip"
        NovelExportFormat.PDF -> "application/pdf"
        NovelExportFormat.MARKDOWN -> "text/markdown"
        NovelExportFormat.DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    }

    /**
     * 生成 PDF 字节（pdfbox-android）：A4 分页，加载系统中日文字体，
     * 按行绘制并自动换行/分页。
     */
    private fun buildPdf(text: String): ByteArray {
        val doc = PDDocument()
        try {
            val font = loadCjkFont(doc)
            if (font == null) {
                // 无可用中日文字体：退化为 Helvetica（纯 ASCII，中文不显示，避免崩溃）
                renderPdf(doc, null, text)
            } else {
                renderPdf(doc, font, text)
            }
            val out = ByteArrayOutputStream()
            doc.save(out)
            return out.toByteArray()
        } finally {
            doc.close()
        }
    }

    /** 加载系统中日文字体（Android 各版本路径不同；ttc 用 TrueTypeCollection 取 CJK 子字体）。 */
    private fun loadCjkFont(doc: PDDocument): PDFont? = runCatching {
        val candidates = listOf(
            Triple("/system/fonts/NotoSansCJK-Regular.ttc", "NotoSansCJK-Regular", true),
            Triple("/system/fonts/DroidSansFallback.ttf", null, false),
            Triple("/system/fonts/NotoSansSC-Regular.otf", null, false),
            Triple("/system/fonts/NotoSansCJKsc-Regular.otf", null, false),
        )
        for ((path, fontName, isTtc) in candidates) {
            val file = File(path)
            if (!file.exists()) continue
            return@runCatching if (isTtc && fontName != null) {
                val ttc = TrueTypeCollection(file)
                val ttf = ttc.getFontByName(fontName) ?: continue
                PDType0Font.load(doc, ttf, true)
            } else {
                file.inputStream().use { PDType0Font.load(doc, it, true) }
            }
        }
        null
    }.getOrNull()

    /** 逐行绘制文本：超过行宽按字符断行，到底部自动换页。 */
    private fun renderPdf(doc: PDDocument, font: PDFont?, text: String) {
        val fontSize = 12f
        val leading = fontSize * 1.6f
        val margin = 50f
        var page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        val usableWidth = page.mediaBox.width - margin * 2
        val usableHeight = page.mediaBox.height - margin * 2
        var y = page.mediaBox.height - margin
        var content = PDPageContentStream(doc, page)
        content.beginText()
        if (font != null) content.setFont(font, fontSize)
        content.newLineAtOffset(margin, y)

        fun finishLine() {
            content.newLineAtOffset(0f, -leading)
            y -= leading
        }

        for (line in text.lines()) {
            if (line.isBlank()) {
                // 空行保持占位，避免段落粘连
                if (y < margin + leading) {
                    content.endText()
                    content.close()
                    page = PDPage(PDRectangle.A4)
                    doc.addPage(page)
                    content = PDPageContentStream(doc, page)
                    y = page.mediaBox.height - margin
                    content.beginText()
                    if (font != null) content.setFont(font, fontSize)
                    content.newLineAtOffset(margin, y)
                }
                finishLine()
                continue
            }
            val lines =
                if (font != null) wrapPdfLine(font, fontSize, line, usableWidth) else listOf(line)
            for (wl in lines) {
                if (y < margin + leading) {
                    content.endText()
                    content.close()
                    page = PDPage(PDRectangle.A4)
                    doc.addPage(page)
                    content = PDPageContentStream(doc, page)
                    y = page.mediaBox.height - margin
                    content.beginText()
                    if (font != null) content.setFont(font, fontSize)
                    content.newLineAtOffset(margin, y)
                }
                content.showText(pdfEscape(wl))
                finishLine()
            }
        }
        content.endText()
        content.close()
    }

    /** 按显示宽度断行（宽度 = font.getStringWidth * fontSize / 1000）。 */
    private fun wrapPdfLine(
        font: PDFont,
        fontSize: Float,
        line: String,
        maxWidth: Float
    ): List<String> {
        if (line.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        var start = 0
        var lastGood = 0
        for (i in 1..line.length) {
            val width = font.getStringWidth(line.substring(start, i)) * fontSize / 1000f
            if (width > maxWidth) {
                if (lastGood == start) lastGood = i // 单字超宽也断，避免死循环
                lines.add(line.substring(start, lastGood))
                start = lastGood
                lastGood = start
            } else {
                lastGood = i
            }
        }
        if (start < line.length) lines.add(line.substring(start))
        return lines
    }

    /** PDF 文本串转义（`\` `(` `)`）。 */
    private fun pdfEscape(s: String): String = buildString {
        for (c in s) {
            when (c) {
                '\\' -> append("\\\\")
                '(' -> append("\\(")
                ')' -> append("\\)")
                else -> append(c)
            }
        }
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
 * 生成 Markdown 全文（纯函数，可测）。
 * 标题保留 `#` 层级、引用保留 `>`、分隔线用 `---`；插图跳过。
 */
internal fun buildMarkdown(
    chapters: List<Pair<Novel, NovelDocument>>,
    seriesTitle: String?,
): String {
    return buildString {
        val first = chapters.first().first
        appendLine("# ${first.title.orEmpty()}")
        first.user?.name?.let { if (it.isNotBlank()) appendLine("> 作者：$it") }
        if (!seriesTitle.isNullOrBlank()) appendLine("> 系列：$seriesTitle")
        first.caption?.takeIf { it.isNotBlank() }?.let {
            appendLine("> 简介：${htmlToPlainText(it)}")
        }
        appendLine()
        chapters.forEach { (novel, document) ->
            if (chapters.size > 1) {
                appendLine("---")
                appendLine("## ${novel.title.orEmpty()}")
                appendLine("---")
            }
            document.blocks.forEach { block ->
                when (block) {
                    is NovelBlock.Paragraph -> appendLine(block.text)
                    is NovelBlock.Heading -> appendLine(
                        "#".repeat(
                            block.level.coerceIn(
                                1,
                                6
                            )
                        ) + " " + block.text
                    )

                    is NovelBlock.Quote -> appendLine("> ${block.text}")
                    is NovelBlock.Image -> Unit // Markdown 无法内嵌 pixiv 图，跳过
                    is NovelBlock.Separator -> appendLine("---")
                }
            }
            appendLine()
        }
    }
}

// ── DOCX 容器（手写最小 OOXML：Content_Types + rels + document.xml） ─────────

private const val DOCX_CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

private const val DOCX_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

/**
 * 生成最小 .docx 字节（纯函数，可测）。
 * 段落/标题/引用 → `w:p`；插图跳过；结尾 sectPr 保证 Word/WPS 正常分页。
 */
internal fun buildDocx(
    chapters: List<Pair<Novel, NovelDocument>>,
    seriesTitle: String?,
): ByteArray {
    val body = buildString {
        val first = chapters.first().first
        append(docxHeading(first.title.orEmpty(), 1))
        first.user?.name?.takeIf { it.isNotBlank() }?.let { append(docxParagraph("作者：$it")) }
        if (!seriesTitle.isNullOrBlank()) append(docxParagraph("系列：$seriesTitle"))
        first.caption?.takeIf { it.isNotBlank() }
            ?.let { append(docxParagraph("简介：${htmlToPlainText(it)}")) }
        chapters.forEach { (novel, document) ->
            if (chapters.size > 1) {
                append(docxParagraph(null))
                append(docxHeading(novel.title.orEmpty(), 2))
                append(docxParagraph(null))
            }
            document.blocks.forEach { block ->
                when (block) {
                    is NovelBlock.Paragraph -> append(docxParagraph(block.text))
                    is NovelBlock.Heading -> append(docxHeading(block.text, block.level))
                    is NovelBlock.Quote -> append(docxParagraph(block.text, indent = true))
                    is NovelBlock.Image -> Unit
                    is NovelBlock.Separator -> append(docxParagraph(null))
                }
            }
        }
        append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr>")
    }
    val docXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body</w:body></w:document>"""
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zip ->
        zip.writeEntry("[Content_Types].xml", DOCX_CONTENT_TYPES)
        zip.writeEntry("_rels/.rels", DOCX_RELS)
        zip.writeEntry("word/document.xml", docXml)
    }
    return bytes.toByteArray()
}

/** 标题段落：加粗 + 字号（half-points：36=18pt / 32=16pt / 28=14pt）。 */
internal fun docxHeading(text: String, level: Int): String {
    val size = when (level) {
        1 -> 36; 2 -> 32; else -> 28
    }
    return """<w:p><w:pPr><w:spacing w:before="240" w:after="120"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val="$size"/></w:rPr><w:t xml:space="preserve">${
        escapeXml(
            text
        )
    }</w:t></w:r></w:p>"""
}

/** 普通段落（引用带左缩进）；null/空白 → 空段落。 */
internal fun docxParagraph(
    content: String?,
    bold: Boolean = false,
    indent: Boolean = false
): String {
    if (content.isNullOrBlank()) return "<w:p/>"
    val pPr = if (indent) "<w:pPr><w:ind w:left=\"720\"/></w:pPr>" else ""
    val rPr = if (bold) "<w:rPr><w:b/></w:rPr>" else ""
    return "<w:p>$pPr<w:r>$rPr<w:t xml:space=\"preserve\">${escapeXml(content)}</w:t></w:r></w:p>"
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
