package com.pixiv.reader.feature.novel.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
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
import com.pixiv.reader.core.novel.htmlToPlainText
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.feature.novel.R
import com.tom_roush.fontbox.ttf.TTFParser
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.zqc.opencc.android.lib.ChineseConverter
import com.zqc.opencc.android.lib.ConversionType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * PDF 字体发现工具（纯 JVM 可测，无 Android 依赖）：
 * 目录扫描 + CFF 轮廓检测（pdfbox-android 只能嵌入 glyf 轮廓字体）。
 */
internal object PdfFonts {

    private fun isFontFile(name: String) =
        name.endsWith(".ttf") || name.endsWith(".otf") || name.endsWith(".ttc")

    /**
     * 扫描系统字体目录，返回符号字体候选（排除彩色 emoji 位图字体）。
     * 匹配规则：文件名含 "symbol"，或含 "emoji" 且非彩色（单色 emoji 字体）；
     * 不依赖具体文件名，OEM 定制命名也能命中。
     */
    fun systemSymbolCandidates(fontsDir: File): List<File> {
        val files = fontsDir.listFiles() ?: return emptyList()
        return files.filter { f ->
            if (!f.isFile || !isFontFile(f.name.lowercase())) return@filter false
            val name = f.name.lowercase()
            name.contains("symbol") || (name.contains("emoji") && !name.contains("color"))
        }.sortedBy { it.name }
    }

    /**
     * 扫描系统字体目录，返回可作为 PDF 主字体的 CJK 候选（glyf 轮廓 + cmap 覆盖中文）。
     * 与 PdfFontTest 使用同一判定逻辑（[hasCjkGlyph]），适配才返回：
     * - 排除符号/emoji/彩色字体（不负责中文）
     * - CFF 轮廓（pdfbox 无法嵌入）经 [hasCffTable] 过滤；TTC 容器保守跳过
     * - 剩余文件逐个 TTFParser 解析验证中文（U+4E00）与假名（U+3042）字形
     * 现代设备通常返回空（系统 CJK 全为 CFF），老设备可命中 DroidSansFallback.ttf，
     * OEM 定制 TrueType 中文字体（如 MiSans）也能自动发现。结果按偏好排序（Droid 优先）。
     */
    fun systemCjkCandidates(fontsDir: File): List<File> {
        val files = fontsDir.listFiles() ?: return emptyList()
        return files.filter { f ->
            if (!f.isFile || !isFontFile(f.name.lowercase())) return@filter false
            val name = f.name.lowercase()
            if (name.contains("symbol") || name.contains("emoji") || name.contains("color")) {
                return@filter false
            }
            if (hasCffTable(f)) return@filter false
            hasCjkGlyph(f)
        }.sortedWith(compareBy({ !it.name.lowercase().contains("droid") }, { it.name }))
    }

    /** 解析字体并检查 cmap 是否覆盖中文（U+4E00）与常用假名（U+3042）。 */
    fun hasCjkGlyph(file: File): Boolean = runCatching {
        val ttf = TTFParser().parse(file)
        try {
            val cmap = ttf.unicodeCmap ?: return@runCatching false
            cmap.getGlyphId(0x4E00) != 0 && cmap.getGlyphId(0x3042) != 0
        } finally {
            ttf.close()
        }
    }.getOrDefault(false)

    /**
     * 检测字体文件是否为 CFF/OTF 轮廓（读 sfnt 表目录查 "CFF " 表）。
     * pdfbox-android 的子集化/完整嵌入都只支持 glyf（TrueType）轮廓，
     * CFF 字体（如系统 NotoSansCJK）嵌入会在 save 阶段抛异常，必须跳过。
     * TTC 容器 / 解析失败保守返回 true（跳过）。
     */
    fun hasCffTable(file: File): Boolean = try {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(0)
            val tag0 = ByteArray(4)
            raf.readFully(tag0)
            if (tag0.decodeToString() == "ttcf") return true // TTC 容器不做子字体级判定，跳过
            raf.seek(4) // 跳过 sfnt version
            val numTables = raf.readUnsignedShort()
            for (i in 0 until numTables) {
                raf.seek(12L + i * 16L)
                val tag = ByteArray(4)
                raf.readFully(tag)
                if (tag.decodeToString() == "CFF ") return true
            }
            false
        }
    } catch (e: Exception) {
        true // 无法解析时保守视为不可嵌入
    }
}

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
        Log.d(TAG, "exportResumable 开始 novelId=$novelId format=$format seriesId=$seriesId chapterIds=$chapterIds")
        markDownloading(novelId, format, seriesId)
        runCatching {
            val (chapters, coverNovel) = loadChaptersWithCache(novelId, format, seriesId, chapterIds)
            Log.d(TAG, "章节加载完成 count=${chapters.size} 首章id=${chapters.firstOrNull()?.first?.id}")
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
            Log.d(TAG, "导出成功 novelId=$novelId format=$format localPath=${result.localPath} 章节数=${result.chapterCount}")
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
        }.onFailure {
            Log.e(TAG, "导出失败 novelId=$novelId format=$format seriesId=$seriesId chapterIds=$chapterIds", it)
            markFailed(novelId, format, seriesId)
        }
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
            Log.d(TAG, "系列分册获取完成 seriesId=$seriesId 总数=${novels.size}")
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
            decodeChapterCache(cacheFile.readText(Charsets.UTF_8))?.let {
                Log.d(TAG, "章节缓存命中 index=$index chapterId=$chapterId")
                return it
            }
            Log.w(TAG, "章节缓存损坏，重新下载 index=$index chapterId=$chapterId")
        }
        val loaded = contentLoader.load(chapterId).getOrThrow()
        runCatching { cacheFile.writeText(encodeChapterCache(loaded.first, loaded.second)) }
        Log.d(TAG, "章节网络加载完成 index=$index chapterId=$chapterId title=${loaded.first.title}")
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
        // 插图以 data URI 内嵌（下载失败跳过）
        val images = downloadImages(chapters)
        Log.d(TAG, "buildMarkdownFile 开始 fileName=$fileName 内嵌图片=${images.size}")
        return writeExportFile(
            fileName,
            mimeFor(NovelExportFormat.MARKDOWN),
            buildMarkdown(chapters, seriesTitle, images).toByteArray(Charsets.UTF_8)
        )
    }

    private suspend fun buildDocxFile(
        chapters: List<Pair<Novel, NovelDocument>>,
        seriesTitle: String?,
    ): String {
        val first = chapters.first().first
        val fileName = "${fileNameBase(first, seriesTitle)}.docx"
        // 正文插图（按块顺序下载；未解析的 pixivimage/uploadedimage 标记或下载失败跳过）
        val images = downloadImages(chapters)
        Log.d(TAG, "buildDocxFile 开始 fileName=$fileName 章节数=${chapters.size} 内嵌图片=${images.size}")
        return writeExportFile(
            fileName,
            mimeFor(NovelExportFormat.DOCX),
            buildDocx(chapters, seriesTitle, images)
        )
    }

    private suspend fun buildPdfFile(
        chapters: List<Pair<Novel, NovelDocument>>,
        seriesTitle: String?,
    ): String {
        val first = chapters.first().first
        val fileName = "${fileNameBase(first, seriesTitle)}.pdf"
        Log.d(TAG, "buildPdfFile 开始 fileName=$fileName 章节数=${chapters.size}")
        // PDF 排版对齐阅读器设置：字号 / 行距（1.6+增量）/ 段首缩进 / 段距
        val layout = PdfLayoutSettings(
            fontSize = userPreferences.readerFontSize.first(),
            lineHeightMultiplier = 1.6f + userPreferences.readerLineSpacing.first(),
            indentCount = userPreferences.readerParagraphIndent.first(),
            paragraphSpacingEm = userPreferences.readerParagraphSpacing.first(),
        )
        Log.d(
            TAG,
            "PDF 排版参数 fontSize=${layout.fontSize} 行高倍数=${layout.lineHeightMultiplier} " +
                "缩进数=${layout.indentCount} 段距em=${layout.paragraphSpacingEm}"
        )
        // 结构化内容流（文本 + 内嵌插图；文本结构对齐 buildTxt）
        val contents = buildPdfContents(chapters, seriesTitle)
        val pictureCount = contents.count { it is PdfContent.Picture }
        Log.d(TAG, "buildPdfFile 内容块数=${contents.size} 图片块=$pictureCount")
        val bytes = buildPdf(contents, layout)
        Log.d(TAG, "buildPdfFile PDF字节生成完成 size=${bytes.size} bytes")
        return writeExportFile(
            fileName,
            mimeFor(NovelExportFormat.PDF),
            bytes
        )
    }

    /**
     * webp 字节转 jpg（BitmapFactory 解码 + JPEG 压缩，质量 90）；
     * pdfbox 2.x 不支持嵌入 webp（PDF 侧用），转换失败返回 null。
     */
    private fun webpToJpeg(bytes: ByteArray): ByteArray? = runCatching {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        try {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }.getOrNull()

    /** 按块顺序下载正文插图（pixivimage:/uploadedimage: 未解析标记与失败跳过）。 */
    private suspend fun downloadImages(
        chapters: List<Pair<Novel, NovelDocument>>,
    ): List<DocxImage> {
        val images = mutableListOf<DocxImage>()
        chapters.forEachIndexed { ci, (_, document) ->
            document.blocks.filterIsInstance<NovelBlock.Image>()
                .forEachIndexed { ii, img ->
                    if (img.url.startsWith("pixivimage:") || img.url.startsWith("uploadedimage:")) {
                        return@forEachIndexed
                    }
                    val bytes = downloadImage(img.url) ?: return@forEachIndexed
                    val dims = imageDimensions(bytes)
                    val mime = mimeFromUrl(img.url)
                    if (dims == null) {
                        Log.w(TAG, "插图尺寸解析失败，跳过 chapter=$ci index=$ii url=${img.url.take(80)}")
                        return@forEachIndexed
                    }
                    val ext = when (mime) {
                        "image/png" -> "png"
                        "image/webp" -> "webp"
                        else -> "jpg"
                    }
                    images += DocxImage(
                        ref = "image${ci * 100 + ii + 1}.$ext",
                        bytes = bytes,
                        mime = mime,
                        width = dims.first,
                        height = dims.second,
                    )
                }
        }
        return images
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
        Log.d(TAG, "writeExportFile 开始 fileName=$fileName mime=$mime size=${bytes.size} bytes")
        val dirUri = userPreferences.novelExportDir.first()
        if (dirUri.isBlank()) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.d(TAG, "writeExportFile 目标=MediaStore(Download/PixivReader) sdk=${Build.VERSION.SDK_INT}")
                writeToMediaStore(fileName, mime, bytes)
            } else {
                // Android 8-9：公共目录需 WRITE_EXTERNAL_STORAGE 运行时权限，而导出在后台 Worker
                // 无法弹权限框，回退应用私有目录（下载管理页仍可打开/系统分享）。
                val file = File(exportDir, fileName)
                file.writeBytes(bytes)
                Log.d(TAG, "writeExportFile 目标=私有目录 path=${file.path} size=${file.length()}")
                file.path
            }
        }
        Log.d(TAG, "writeExportFile 目标=SAF树 dirUri=$dirUri")
        val tree = DocumentFile.fromTreeUri(context, dirUri.toUri()) ?: error(context.getString(R.string.novel_export_dir_unavailable))
        tree.findFile(fileName)?.delete()
        val doc = tree.createFile(mime, fileName) ?: error(context.getString(R.string.novel_export_create_failed))
        context.contentResolver.openOutputStream(doc.uri)?.use { it.write(bytes) }
            ?: error(context.getString(R.string.novel_export_write_failed))
        Log.d(TAG, "writeExportFile SAF写入完成 uri=${doc.uri}")
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
        Log.d(TAG, "writeToMediaStore 占位条目插入成功 uri=$uri")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error(context.getString(R.string.novel_export_write_failed))
            Log.d(TAG, "writeToMediaStore 写入完成 size=${bytes.size} bytes")
        } catch (e: Exception) {
            // 写入失败：回滚占位条目，避免下载管理残留不可打开文件
            Log.w(TAG, "writeToMediaStore 写入失败 uri=$uri", e)
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        val updated = resolver.update(uri, values, null, null)
        Log.d(TAG, "writeToMediaStore IS_PENDING 置 0 完成 updated=$updated uri=$uri")
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

    /** PDF 排版参数（对齐阅读器设置：字号/行距/段首缩进/段距）。 */
    private data class PdfLayoutSettings(
        val fontSize: Float,
        val lineHeightMultiplier: Float,
        val indentCount: Int,
        val paragraphSpacingEm: Float,
    )

    /** PDF 内容元素流：文本块（块首行是否缩进由 [indent] 控制，标题/元数据不缩进）或内嵌图片。 */
    private sealed class PdfContent {
        data class Text(val text: String, val indent: Boolean) : PdfContent()
        data class Picture(val bytes: ByteArray, val mime: String) : PdfContent()
    }

    /**
     * 生成 PDF 内容元素流（文本结构对齐 buildTxt，插图按块顺序内嵌）。
     * 未解析的 pixivimage:/uploadedimage: 标记与下载失败/格式不支持（webp 等）的图片跳过。
     */
    private suspend fun buildPdfContents(
        chapters: List<Pair<Novel, NovelDocument>>,
        seriesTitle: String?,
    ): List<PdfContent> {
        val images = downloadImages(chapters)
        var imgIdx = 0
        val contents = mutableListOf<PdfContent>()
        val first = chapters.first().first
        // 头部元数据（对齐 buildTxt；不缩进）
        contents += PdfContent.Text(first.title.orEmpty(), indent = false)
        first.user?.name?.takeIf { it.isNotBlank() }?.let {
            contents += PdfContent.Text("作者：$it", indent = false)
        }
        if (!seriesTitle.isNullOrBlank()) contents += PdfContent.Text("系列：$seriesTitle", indent = false)
        first.caption?.takeIf { it.isNotBlank() }?.let {
            contents += PdfContent.Text("简介：${htmlToPlainText(it)}", indent = false)
        }
        contents += PdfContent.Text("\n", indent = true)
        chapters.forEach { (novel, document) ->
            if (chapters.size > 1) {
                contents += PdfContent.Text("\n${novel.title}\n", indent = false)
            }
            document.blocks.forEach { block ->
                when (block) {
                    is NovelBlock.Paragraph -> contents += PdfContent.Text(stripIndent(block.text), indent = true)
                    is NovelBlock.Heading -> contents += PdfContent.Text("\n${block.text}\n", indent = false)
                    is NovelBlock.Quote -> contents += PdfContent.Text("> ${stripIndent(block.text)}", indent = true)
                    is NovelBlock.Image -> {
                        val img = images.getOrNull(imgIdx)
                        imgIdx++
                        if (img != null) {
                            // webp → jpg：pdfbox 2.x 无法嵌入 webp，先转码（失败跳过该图）
                            if (img.mime == "image/webp") {
                                webpToJpeg(img.bytes)?.let {
                                    contents += PdfContent.Picture(it, "image/jpeg")
                                } ?: Log.w(TAG, "webp 转 jpg 失败，跳过该图")
                            } else {
                                contents += PdfContent.Picture(img.bytes, img.mime)
                            }
                        }
                    }
                    is NovelBlock.Separator -> contents += PdfContent.Text(block.symbol, indent = true)
                }
            }
            contents += PdfContent.Text("\n", indent = true)
        }
        return contents
    }

    /**
     * 生成 PDF 字节（pdfbox-android）：A4 分页，加载系统中日文字体，
     * 按元素流（文本行 + 内嵌插图）绘制并自动换行/分页；
     * 排版参数对齐阅读器设置（缩进/段距/字号/行距）。
     */
    private fun buildPdf(contents: List<PdfContent>, layout: PdfLayoutSettings): ByteArray {
        // pdfbox-android 的 cmap/afm 等运行时资源打包在 APK assets 里，使用前必须先初始化
        // 资源加载器；否则 PDType0Font.load 抛 "Could not find referenced cmap stream
        // Identity-H"、PDType1Font（Helvetica 退化路径）类初始化抛 "afm not found"。
        // 重复调用幂等（内部 isReady 守卫）。
        PDFBoxResourceLoader.init(context)
        val doc = PDDocument()
        doc.use { doc ->
            val fonts = loadPdfFonts(doc)
            if (fonts.isEmpty()) {
                // 无可用系统中文字体：退化为 Helvetica（仅 Latin-1 字形，中文不显示；无字形字符跳过不画）
                Log.w(TAG, "buildPdf 无可用系统中文字体，退化为 Helvetica（中文将不显示）")
            } else {
                Log.d(TAG, "buildPdf 使用字体 [${fonts.joinToString(", ") { runCatching { it.name }.getOrDefault("?") }}]，开始渲染内容块=${contents.size}")
            }
            renderPdf(doc, fonts, contents, layout)
            Log.d(TAG, "buildPdf 渲染完成 页数=${doc.numberOfPages}")
            val out = ByteArrayOutputStream()
            doc.save(out)
            Log.d(TAG, "buildPdf 保存完成 size=${out.size()} bytes")
            return out.toByteArray()
        }
    }

    /**
     * 加载 PDF 渲染字体集（pdfbox-android；仅真机验证）。
     *
     * 返回按优先级排序的字体列表，渲染时逐字符选择第一个能编码该字符的字体：
     * 1. 系统 CJK 字体（glyf 轮廓且 cmap 覆盖中文，[PdfFonts.systemCjkCandidates] 自动
     *    发现）：老设备 DroidSansFallback.ttf、OEM 定制 TrueType 中文字体直接复用，
     *    免打包体积；现代设备系统 CJK 全为 CFF 不可嵌入，此步通常为空。
     * 2. 打包中文字体 `assets/pdf_fonts/DroidSansFallbackFull.ttf`（AOSP，Apache 2.0）兜底。
     * 3. 打包符号字体 `assets/pdf_fonts/DejaVuSans.ttf`（双许可，含 ❤ U+2764 等
     *    Dingbats/杂项符号——CJK 字体不覆盖这些字形）。
     * 4. 系统符号字体（glyf）：目录扫描自动发现，CFF 经 [PdfFonts.hasCffTable] 过滤。
     * 全部失败返回空列表（调用方退化为 Helvetica，仅 Latin-1 字形）。
     */
    private fun loadPdfFonts(doc: PDDocument): List<PDFont> {
        val fonts = mutableListOf<PDFont>()
        // 1. 系统 CJK（进程内缓存扫描结果，避免每次导出全目录解析）
        val systemCjk = cachedCjkCandidates ?: PdfFonts.systemCjkCandidates(File("/system/fonts"))
            .also { cachedCjkCandidates = it }
        for (file in systemCjk) {
            val result = runCatching { file.inputStream().use { PDType0Font.load(doc, it, true) } }
            if (result.isSuccess) {
                Log.d(TAG, "系统 CJK 字体加载成功 path=${file.path} font=${runCatching { result.getOrThrow().name }.getOrDefault("?")}")
                fonts += result.getOrThrow()
                break
            }
            Log.w(TAG, "系统 CJK 字体加载失败 path=${file.path}", result.exceptionOrNull())
        }
        // 2-3. 打包字体兜底
        for ((asset, desc) in listOf(
            ASSET_CJK_FONT to "打包中文字体",
            ASSET_SYMBOL_FONT to "打包符号字体",
        )) {
            runCatching { context.assets.open(asset).use { PDType0Font.load(doc, it, true) } }
                .onSuccess {
                    Log.d(TAG, "${desc}加载成功 $asset font=${runCatching { it.name }.getOrDefault("?")}")
                    fonts += it
                }
                .onFailure { e -> Log.w(TAG, "${desc}加载失败 $asset", e) }
        }
        // 4. 系统符号字体（glyf）：目录扫描自动发现，CFF 轮廓（pdfbox 无法嵌入）跳过
        for (file in PdfFonts.systemSymbolCandidates(File("/system/fonts"))) {
            if (PdfFonts.hasCffTable(file)) {
                Log.d(TAG, "跳过 CFF 轮廓符号字体 path=${file.path}")
                continue
            }
            runCatching { file.inputStream().use { PDType0Font.load(doc, it, true) } }
                .onSuccess {
                    Log.d(TAG, "系统符号字体加载成功 path=${file.path} font=${runCatching { it.name }.getOrDefault("?")}")
                    fonts += it
                }
                .onFailure { e -> Log.w(TAG, "系统符号字体加载失败 path=${file.path}", e) }
        }
        if (fonts.isEmpty()) Log.w(TAG, "无可用字体，退化为 Helvetica")
        return fonts
    }

    /** 系统 CJK 候选扫描结果缓存（进程内有效；字体文件变更需重启进程）。 */
    @Volatile
    private var cachedCjkCandidates: List<File>? = null

    /**
     * 逐行绘制文本：超过行宽按字符断行，到底部自动换页。
     *
     * 多字体渲染：每个字符选用字体列表中第一个能编码它的字体（主中文字体优先，
     * 符号字体兜底 ❤ 等 CJK 缺字形符号）；所有字体都无法编码的字符跳过不画
     * （不替换为 '?'，保持原文语义）。字体切换通过同一文本对象内 setFont(Tf) 实现，
     * 不影响文本基线位置。
     */
    /**
     * 按元素流绘制 PDF（pdfbox-android）：
     * - [PdfContent.Text]：段落文本行——空行（段落边界）渲染段距空隙；段落首行缩进
     *   （[PdfContent.Text.indent]=false 的标题/元数据块不缩进）；超宽按字符断行、到底换页
     * - [PdfContent.Picture]：内嵌插图（jpg/png），按可用宽度等比缩放居中绘制，
     *   高度不够自动换页；格式不支持（webp 等）或损坏的图片跳过
     *
     * 排版对齐阅读器设置（[PdfLayoutSettings]）：字号、行距（1.6+增量）、
     * 段首缩进（字号 × 缩进数）、段距（em × 字号）。
     *
     * 多字体渲染：每个字符选用字体列表中第一个能编码它的字体（主中文字体优先，
     * 符号字体兜底 ❤ 等 CJK 缺字形符号）；所有字体都无法编码的字符跳过不画
     * （不替换为 '?'，保持原文语义）。字体切换通过同一文本对象内 setFont(Tf) 实现，
     * 不影响文本基线位置。
     */
    private fun renderPdf(doc: PDDocument, fonts: List<PDFont>, contents: List<PdfContent>, layout: PdfLayoutSettings) {
        val fontSize = layout.fontSize
        val leading = fontSize * layout.lineHeightMultiplier
        val margin = 50f
        // 段首缩进 = 全角字宽（= 字号）× 缩进数；段距空隙 = 段距(em) × 字号
        val indentWidth = fontSize * layout.indentCount
        val paragraphGap = fontSize * layout.paragraphSpacingEm
        var page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        val usableWidth = page.mediaBox.width - margin * 2
        var y = page.mediaBox.height - margin
        var content = PDPageContentStream(doc, page)
        content.beginText()
        // 无可用字体时退化为 Helvetica；必须 setFont 否则 showText 抛异常
        val primary = fonts.firstOrNull() ?: PDType1Font.HELVETICA
        content.setFont(primary, fontSize)
        content.newLineAtOffset(margin, y)

        var newPageCount = 0
        var showTextFailCount = 0
        var noGlyphCharCount = 0
        var pictureCount = 0
        var pictureSkipCount = 0
        // 跨行跟踪 content 的当前字体（PDPageContentStream 无 getter，须与每次
        // setFont/newPage/图片恢复同步；否则下一行首段误判「已设置」而不切换，
        // 用上一行末尾的符号字体绘制中文 → No glyph）
        var activeFont: PDFont = primary

        fun newPage() {
            content.endText()
            content.close()
            page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            content = PDPageContentStream(doc, page)
            y = page.mediaBox.height - margin
            content.beginText()
            content.setFont(primary, fontSize)
            content.newLineAtOffset(margin, y)
            activeFont = primary // 新页字体重置为 primary，同步跟踪变量
            newPageCount++
        }

        var lineCount = 0
        var isFirstContentLine = true // 全文首行（主标题）不缩进
        for (item in contents) {
            when (item) {
                is PdfContent.Picture -> {
                    // 插图：jpg/png 内嵌（webp 等 pdfbox 不支持 → 跳过）
                    val img = runCatching {
                        PDImageXObject.createFromByteArray(doc, item.bytes, item.mime)
                    }.getOrNull()
                    if (img == null) {
                        pictureSkipCount++
                        Log.w(TAG, "图片内嵌失败（格式不支持或损坏）mime=${item.mime}")
                        continue
                    }
                    val w = img.width.toFloat()
                    val h = img.height.toFloat()
                    if (w <= 0f || h <= 0f) continue
                    // 可用宽度内等比缩放；图片块视为段落边界（前后留段距）
                    val scale = minOf(1f, usableWidth / w)
                    val drawW = w * scale
                    val drawH = h * scale
                    if (paragraphGap > 0f) y -= paragraphGap
                    if (y - drawH < margin) newPage()
                    // drawImage 不能在文本块（BT...ET）内执行：先结束文本块，绘制后恢复
                    content.endText()
                    // 图片 y 为 PDF 底部坐标：顶边对齐当前文本游标
                    content.drawImage(img, margin, y - drawH, drawW, drawH)
                    y -= drawH
                    content.beginText()
                    content.setFont(primary, fontSize)
                    content.newLineAtOffset(margin, y)
                    activeFont = primary // 图片恢复后字体重置为 primary
                    pictureCount++
                }

                is PdfContent.Text -> {
                    var atParagraphStart = true // 块首行视为段首（缩进由 indent 与首行标记控制）
                    for (line in item.text.lines()) {
                        if (line.isBlank()) {
                            // 空行 = 段落/块边界：渲染段距空隙（阅读器段距语义，非整行行距）
                            if (paragraphGap > 0f) {
                                if (y < margin + paragraphGap) newPage()
                                content.newLineAtOffset(0f, -paragraphGap)
                                y -= paragraphGap
                            }
                            atParagraphStart = true
                            continue
                        }
                        // 逐字符绑定字体：null = 无任何字体能编码（跳过不画，宽 0）
                        val glyphs = line.map { ch ->
                            fonts.firstOrNull { runCatching { it.encode(ch.toString()) }.isSuccess }
                                ?.let { ch to it }
                        }
                        noGlyphCharCount += glyphs.count { it == null }
                        // 按显示宽度断行（无字形字符宽 0；单字超宽也强制断，避免死循环）
                        val rows = mutableListOf<List<Pair<Char, PDFont>>>()
                        var start = 0
                        var lastGood = 0
                        var width = 0f
                        for (i in glyphs.indices) {
                            val g = glyphs[i]
                            width += g?.let { (c, f) -> f.getStringWidth(c.toString()) * fontSize / 1000f } ?: 0f
                            if (width > usableWidth) {
                                if (lastGood == start) lastGood = i + 1
                                rows += glyphs.subList(start, lastGood).filterNotNull()
                                start = lastGood
                                lastGood = start
                                width = 0f
                            } else {
                                lastGood = i + 1
                            }
                        }
                        if (start < glyphs.size) rows += glyphs.subList(start, glyphs.size).filterNotNull()
                        for (row in rows) {
                            if (y < margin + leading) newPage()
                            // 段落首行缩进（标题/元数据块与全文首行不缩进）
                            val indent = if (atParagraphStart && !isFirstContentLine && item.indent) {
                                indentWidth
                            } else {
                                0f
                            }
                            if (indent > 0f) content.newLineAtOffset(indent, 0f)
                            // 行内按连续同字体分段绘制（setFont 切换不改变文本位置）；
                            // activeFont 为跨行跟踪变量（见上），勿在此重复声明
                            var i = 0
                            while (i < row.size) {
                                val (_, f) = row[i]
                                if (f !== activeFont) {
                                    content.setFont(f, fontSize)
                                    activeFont = f
                                }
                                var j = i + 1
                                while (j < row.size && row[j].second === f) j++
                                val seg = row.subList(i, j).joinToString("") { it.first.toString() }
                                // showText 内部处理 () \ 转义，勿再手动转义；异常时跳过该段保证导出不中断
                                try {
                                    content.showText(seg)
                                } catch (e: Exception) {
                                    showTextFailCount++
                                    Log.w(TAG, "showText 失败，跳过该段 长度=${seg.length}", e)
                                }
                                i = j
                            }
                            // 换行：退回缩进 + 下移一行
                            content.newLineAtOffset(-indent, -leading)
                            y -= leading
                            atParagraphStart = false
                            isFirstContentLine = false
                            lineCount++
                        }
                    }
                }
            }
        }
        content.endText()
        content.close()
        Log.d(
            TAG,
            "renderPdf 完成 总行数=$lineCount 新增页=$newPageCount 总页数=${doc.numberOfPages} " +
                "showText失败段=$showTextFailCount 无字形字符=$noGlyphCharCount 图片=$pictureCount 图片跳过=$pictureSkipCount"
        )
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

    companion object {
        private const val TAG = "NovelExporter"
        /** 打包的 PDF 中文字体（AOSP DroidSansFallbackFull，TrueType/glyf 轮廓）。 */
        private const val ASSET_CJK_FONT = "pdf_fonts/DroidSansFallbackFull.ttf"
        /** 打包的 PDF 符号字体（DejaVu Sans，含 ❤ 等 CJK 缺失字形）。 */
        private const val ASSET_SYMBOL_FONT = "pdf_fonts/DejaVuSans.ttf"
    }
}
