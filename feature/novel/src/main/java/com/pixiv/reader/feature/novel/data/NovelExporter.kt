package com.pixiv.reader.feature.novel.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.pixiv.api.model.ImageUrls
import com.pixiv.api.model.Novel
import com.pixiv.api.model.Series
import com.pixiv.api.model.User
import com.pixiv.reader.core.common.renderNovelFileName
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.datastore.UserPreferences
import com.pixiv.reader.core.novel.NovelBlock
import com.pixiv.reader.core.novel.NovelDocument
import com.pixiv.reader.core.novel.NovelDocumentCodec
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.feature.novel.R
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
import androidx.core.net.toUri
import com.zqc.opencc.android.lib.ChineseConverter
import com.zqc.opencc.android.lib.ConversionType

/**
 * 小说导出引擎：把小说（单本或整个系列）导出为 TXT / EPUB / PDF / MARKDOWN / DOCX 文件。
 *
 * - TXT：纯文本，正文插图位置跳过（[pixivimage]/[uploadedimage] 标记不输出）
 * - EPUB：EPUB3 标准 zip 容器，正文内嵌图片（下载失败则跳过该图）
 * - PDF：pdfbox-android 排版，中日文依赖系统字体
 * - MARKDOWN / DOCX：文本 / OOXML 容器
 *
 * 输出目录：默认 Android 10+ 写公共 `Download/PixivReader/`（MediaStore，用户可在文件管理器直接看到），
 * Android 8-9 回退应用私有 `filesDir/Downloads/novels/`（后台 Worker 无法申请存储权限）；
 * 用户可在「我的-下载位置」通过 SAF 指定任意目录（如系统 Download），配置非空时优先。
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
     * @param chapterIds 系列部分分册 ID；非空且 seriesId>0 时只导出选中的分册（合并为一个文件）
     * @return 导出的本地路径（应用内文件路径 或 content:// uri）
     */
    suspend fun exportResumable(
        novelId: Long,
        format: NovelExportFormat,
        seriesId: Long? = null,
        chapterIds: List<Long>? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        markDownloading(novelId, format, seriesId)
        runCatching {
            val (chapters, coverNovel) = loadChaptersWithCache(novelId, format, seriesId, chapterIds)
            if (chapters.isEmpty()) error(context.getString(R.string.novel_export_no_chapters))
            // 导出前统一格式化（对齐 format_novel）：合并硬换行、卷/章重排、简繁转换（OpenCC）、标点规范化；
            // 四种格式（TXT/EPUB/DOCX/Markdown，PDF 复用 TXT）自动全部生效。
            val formatted = formatChapters(chapters, simplifyConverter())
            val localPath = when (format) {
                NovelExportFormat.TXT -> buildTxtFile(
                    formatted,
                    seriesTitle = coverNovel.series?.title
                )

                NovelExportFormat.EPUB -> buildEpubFile(
                    formatted,
                    seriesTitle = coverNovel.series?.title,
                    coverNovel = coverNovel
                )

                NovelExportFormat.MARKDOWN -> buildMarkdownFile(
                    formatted,
                    seriesTitle = coverNovel.series?.title
                )

                NovelExportFormat.DOCX -> buildDocxFile(
                    formatted,
                    seriesTitle = coverNovel.series?.title
                )

                NovelExportFormat.PDF -> buildPdfFile(
                    formatted,
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
        chapterIds: List<Long>? = null,
    ): Pair<List<Pair<Novel, NovelDocument>>, Novel> {
        val cache = cacheDir(novelId, format)
        val chapters = if ((seriesId != null) && (seriesId > 0L)) {
            val novels = fetchSeriesNovels(seriesId)
            if (novels.isEmpty()) error(context.getString(R.string.novel_export_series_empty))
            val selected = if (chapterIds.isNullOrEmpty()) {
                novels
            } else {
                // 部分下载：只保留选中分册（保持系列接口返回顺序），至少一本
                novels.filter { it.id in chapterIds }.ifEmpty {
                    error(context.getString(R.string.novel_export_no_chapters))
                }
            }
            selected.mapIndexed { index, chapter ->
                val pair = loadChapterCached(cache, chapter.id, index, novelId, format)
                updateProgress(novelId, format, seriesId, ((index + 1) * 100) / selected.size)
                pair
            }
        } else {
            listOf(
                loadChapterCached(cache, novelId, 0, novelId, format).also {
                    updateProgress(novelId, format, seriesId, 100)
                },
            )
        }
        if (chapters.isEmpty()) error(context.getString(R.string.novel_export_no_chapters))
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
            // 下载管理卡片元数据快照（作者/字数/收藏/发布/系列标题）
            authorName = coverNovel.user?.name,
            authorAvatarUrl = coverNovel.user?.profile_image_urls?.best(),
            wordCount = coverNovel.text_length ?: 0,
            favoriteCount = coverNovel.total_bookmarks ?: 0,
            publishDate = coverNovel.create_date,
            seriesTitle = coverNovel.series?.title,
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
        authorName: String? = null,
        authorAvatarUrl: String? = null,
        wordCount: Int = 0,
        favoriteCount: Int = 0,
        publishDate: String? = null,
        seriesTitle: String? = null,
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
                    authorName = authorName,
                    authorAvatarUrl = authorAvatarUrl,
                    wordCount = wordCount,
                    favoriteCount = favoriteCount,
                    publishDate = publishDate,
                    seriesTitle = seriesTitle,
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
        val fileName = "${fileNameBase(first, seriesTitle)}.txt"
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
        val fileName = "${fileNameBase(first, seriesTitle)}.epub"
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
        // 合并样式表（assets/epub/Main.css，样书 Main.css 全文；读取失败则导出无样式文件）
        val css = runCatching {
            context.assets.open("epub/Main.css").bufferedReader().use { it.readText() }
        }.getOrDefault("")
        return writeExportFile(
            fileName,
            mimeFor(NovelExportFormat.EPUB),
            buildEpub(chapters, seriesTitle, images, css)
        )
    }

    private suspend fun buildMarkdownFile(
        chapters: List<Pair<Novel, NovelDocument>>,
        seriesTitle: String?,
    ): String {
        val first = chapters.first().first
        val fileName = "${fileNameBase(first, seriesTitle)}.md"
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
        val fileName = "${fileNameBase(first, seriesTitle)}.docx"
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
        val fileName = "${fileNameBase(first, seriesTitle)}.pdf"
        // 复用 TXT 纯文本为排版源（插图不渲染）
        return writeExportFile(
            fileName,
            mimeFor(NovelExportFormat.PDF),
            buildPdf(buildTxt(chapters, seriesTitle))
        )
    }

    // ── 导出目标（默认系统 Download / 用户指定 SAF 目录） ─────────────────────

    /**
     * 写入导出文件并返回本地定位串（content:// uri 或应用内文件路径）。
     * - 用户配置了 SAF tree uri：写入指定目录（同名文件先删再建，避免重复）
     * - 未配置：Android 10+ 走 MediaStore 写入公共 Download/PixivReader（用户可在文件管理器直接看到）；
     *   Android 8-9（MediaStore.Downloads 不可用且公共目录需运行时权限）回退应用私有目录。
     */
    /**
     * 按用户模板渲染导出文件名主体（不含扩展名）。
     * 模板来自「我的」页设置（占位符 {title}/{author}/{id}/{series}）。
     */
    private suspend fun fileNameBase(novel: Novel, seriesTitle: String?): String {
        val template = userPreferences.novelFileNameTemplate.first()
        return renderNovelFileName(
            template = template,
            title = novel.title.orEmpty(),
            author = novel.user?.name,
            id = novel.id,
            seriesTitle = seriesTitle,
            publishDate = novel.create_date,
            favoriteCount = novel.total_bookmarks,
        )
    }

    private suspend fun writeExportFile(fileName: String, mime: String, bytes: ByteArray): String {
        val dirUri = userPreferences.novelExportDir.first()
        if (dirUri.isBlank()) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeToMediaStore(fileName, mime, bytes)
            } else {
                // Android 8-9：公共目录需 WRITE_EXTERNAL_STORAGE 运行时权限，而导出在后台 Worker
                // 无法弹权限框，回退应用私有目录（下载管理页仍可打开/系统分享）。
                val file = File(exportDir, fileName)
                file.writeBytes(bytes)
                file.path
            }
        }
        val tree = DocumentFile.fromTreeUri(context, dirUri.toUri()) ?: error(context.getString(R.string.novel_export_dir_unavailable))
        tree.findFile(fileName)?.delete()
        val doc = tree.createFile(mime, fileName) ?: error(context.getString(R.string.novel_export_create_failed))
        context.contentResolver.openOutputStream(doc.uri)?.use { it.write(bytes) }
            ?: error(context.getString(R.string.novel_export_write_failed))
        return doc.uri.toString()
    }

    /** 通过 MediaStore 写入公共 Download/PixivReader 目录（Android 10+，免存储权限）。 */
    private fun writeToMediaStore(fileName: String, mime: String, bytes: ByteArray): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PixivReader")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error(context.getString(R.string.novel_export_create_failed))
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error(context.getString(R.string.novel_export_write_failed))
        } catch (e: Exception) {
            // 写入失败：回滚占位条目，避免下载管理残留不可打开文件
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri.toString()
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
        doc.use { doc ->
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

        fun newPage() {
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

        for (line in text.lines()) {
            if (line.isBlank()) {
                // 空行保持占位，避免段落粘连
                if (y < margin + leading) newPage()
                finishLine()
                continue
            }
            val lines =
                if (font != null) wrapPdfLine(font, fontSize, line, usableWidth) else listOf(line)
            for (wl in lines) {
                if (y < margin + leading) newPage()
                // showText 内部处理 () \ 转义，勿再手动转义；
                // 个别字符字体无法编码（如特殊符号/Helvetica 下的中文）会抛异常——
                // 整行失败时逐字符降级为 '?'，保证导出不中断
                try {
                    content.showText(wl)
                } catch (e: Exception) {
                    content.showText(
                        wl.map { ch ->
                            if (font != null &&
                                runCatching { font.encode(ch.toString()) }.isSuccess
                            ) {
                                ch
                            } else {
                                '?'
                            }
                        }.joinToString(""),
                    )
                }
                finishLine()
            }
        }
        content.endText()
        content.close()
    }

    /** 按显示宽度断行（字符级增量累加宽度，大文本 O(n) 而非 O(n²)）。 */
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
        var width = 0f
        for (i in 1..line.length) {
            width += font.getStringWidth(line[i - 1].toString()) * fontSize / 1000f
            if (width > maxWidth) {
                if (lastGood == start) lastGood = i // 单字超宽也断，避免死循环
                lines.add(line.substring(start, lastGood))
                start = lastGood
                lastGood = start
                width = 0f
            } else {
                lastGood = i
            }
        }
        if (start < line.length) lines.add(line.substring(start))
        return lines
    }

    /** 下载图片字节（带 Referer 的 imageClient；失败返回 null）。 */
    private fun downloadImage(url: String): ByteArray? {
        return runCatching {
            val request = Request.Builder().url(url).build()
            pixivRepository.imageClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.bytes()
            }
        }.getOrNull()
    }

    /**
     * 繁→简转换器（Android-OpenCC）：首次调用会由库内部把字典从 assets 复制到 filesDir；
     * 转换失败回退原文，保证导出不中断。
     */
    private fun simplifyConverter(): (String) -> String = { text ->
        runCatching { ChineseConverter.convert(text, ConversionType.T2S, context) }.getOrDefault(text)
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
