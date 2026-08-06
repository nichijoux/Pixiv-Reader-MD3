package com.pixiv.reader.feature.novel.data

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
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
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
 *
 * 各格式字节生成见 [buildTxt] / [buildMarkdown] / [buildDocx] / [buildEpub]（纯函数可测）；
 * 文件级编排、缓存、下载索引写入、SAF 写入与 PDF 渲染在本类。
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

    // ── 各格式文件构建（编排：文件名 + 下载内嵌图 + 写入目标） ────────────────

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

    // ── PDF（pdfbox-android；中日文依赖系统字体，仅真机验证） ────────────────

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
