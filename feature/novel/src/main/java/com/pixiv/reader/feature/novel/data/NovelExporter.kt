package com.pixiv.reader.feature.novel.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.pixiv.api.model.ImageUrls
import com.pixiv.api.model.Novel
import com.pixiv.api.model.Series
import com.pixiv.api.model.User
import com.pixiv.reader.core.common.format.NovelFileNameTemplate
import com.pixiv.reader.core.common.format.renderNovelFileName
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.datastore.UserPreferences
import com.pixiv.reader.core.network.novel.NovelContentLoader
import com.pixiv.reader.core.network.novel.fetchAllSeriesChapters
import com.pixiv.reader.core.novel.codec.NovelDocumentCodec
import com.pixiv.reader.core.novel.model.NovelBlock
import com.pixiv.reader.core.novel.model.NovelDocument
import com.pixiv.reader.core.novel.util.htmlToPlainText
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.core.ui.component.card.toCardData
import com.pixiv.reader.feature.novel.R
import com.google.gson.Gson
import com.zqc.opencc.android.lib.ChineseConverter
import com.zqc.opencc.android.lib.ConversionType
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

/**
 * 下载范围键：区分同一小说（同 targetId+format）的不同下载范围，避免互相顶替索引条目。
 * 单本=""；整系列="series"；部分分册="partial"。
 */
internal fun novelScopeKey(seriesId: Long?, chapterIds: List<Long>?): String = when {
    seriesId == null || seriesId <= 0L -> ""
    chapterIds.isNullOrEmpty() -> "series"
    else -> "partial"
}


/**
 * 小说导出引擎：把小说（单本或整个系列）导出为 TXT / EPUB / PDF / MARKDOWN / DOCX 文件。
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
    @param:ApplicationContext private val context: Context,
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
        Log.d(
            TAG,
            "exportResumable 开始 novelId=$novelId format=$format seriesId=$seriesId chapterIds=$chapterIds"
        )
        val scopeKey = novelScopeKey(seriesId, chapterIds)
        markDownloading(novelId, format, seriesId, scopeKey)
        runCatching {
            val (chapters, coverNovel) = loadChaptersWithCache(
                novelId,
                format,
                seriesId,
                chapterIds,
                scopeKey,
            )
            Log.d(
                TAG,
                "章节加载完成 count=${chapters.size} 首章id=${chapters.firstOrNull()?.first?.id}"
            )
            if (chapters.isEmpty()) error(context.getString(R.string.novel_export_no_chapters))
            // 导出前统一格式化（对齐 format_novel）：合并硬换行、卷/章重排、简繁转换（OpenCC）、标点规范化；
            // 四种格式（TXT/EPUB/DOCX/Markdown，PDF 复用 TXT）自动全部生效。
            val formatted = formatChapters(chapters, simplifyConverter())
            val localPath = when (format) {
                NovelExportFormat.TXT -> buildTxtFile(
                    formatted,
                    seriesTitle = coverNovel.series?.title,
                    scopeKey = scopeKey,
                )

                NovelExportFormat.EPUB -> buildEpubFile(
                    formatted,
                    seriesTitle = coverNovel.series?.title,
                    coverNovel = coverNovel,
                    scopeKey = scopeKey,
                )

                NovelExportFormat.MARKDOWN -> buildMarkdownFile(
                    formatted,
                    seriesTitle = coverNovel.series?.title,
                    scopeKey = scopeKey,
                )

                NovelExportFormat.DOCX -> buildDocxFile(
                    formatted,
                    seriesTitle = coverNovel.series?.title,
                    scopeKey = scopeKey,
                )

                NovelExportFormat.PDF -> buildPdfFile(
                    formatted,
                    seriesTitle = coverNovel.series?.title,
                    scopeKey = scopeKey,
                )
            }
            ExportResult(localPath, coverNovel, chapters.size)
        }.onSuccess { result ->
            Log.d(
                TAG,
                "导出成功 novelId=$novelId format=$format localPath=${result.localPath} 章节数=${result.chapterCount}"
            )
            recordDownload(
                novelId,
                format,
                seriesId,
                result.coverNovel,
                result.localPath,
                result.chapterCount,
                scopeKey,
            )
            // 导出成功清理临时缓存
            cacheDir(novelId, format).deleteRecursively()
        }.onFailure {
            Log.e(
                TAG,
                "导出失败 novelId=$novelId format=$format seriesId=$seriesId chapterIds=$chapterIds",
                it
            )
            markFailed(novelId, format, seriesId, scopeKey)
        }
            .map { it.localPath }
    }

    /** 加载章节（断点）：已缓存章节读盘，缺失章节网络下载并写缓存；按章推进进度。 */
    private suspend fun loadChaptersWithCache(
        novelId: Long,
        format: NovelExportFormat,
        seriesId: Long?,
        chapterIds: List<Long>? = null,
        scopeKey: String = "",
    ): Pair<List<Pair<Novel, NovelDocument>>, Novel> {
        val cache = cacheDir(novelId, format)
        val chapters = if ((seriesId != null) && (seriesId > 0L)) {
            val novels = fetchAllSeriesChapters(pixivRepository, seriesId)
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
                val pair = loadChapterCached(cache, chapter.id, index)
                updateProgress(novelId, format, seriesId, ((index + 1) * 100) / selected.size, scopeKey)
                pair
            }
        } else {
            listOf(
                loadChapterCached(cache, novelId, 0).also {
                    updateProgress(novelId, format, seriesId, 100, scopeKey)
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
        // 共享加载器详情可空：导出必须拿到详情元数据，缺失视为失败
        val detail = loaded.first ?: error(context.getString(R.string.novel_not_found))
        runCatching { cacheFile.writeText(encodeChapterCache(detail, loaded.second)) }
        Log.d(TAG, "章节网络加载完成 index=$index chapterId=$chapterId title=${detail.title}")
        return detail to loaded.second
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
        scopeKey: String = "",
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
            // 完整卡片快照（与浏览历史同格式，下载管理页完整显示用）
            payloadJson = Gson().toJson(coverNovel.toCardData()),
        )
    }

    /** 标记开始下载（downloading 中间态，进度 0）。 */
    private suspend fun markDownloading(
        novelId: Long,
        format: NovelExportFormat,
        seriesId: Long?,
        scopeKey: String,
    ) {
        upsertIndex(
            novelId = novelId, title = null, format = format, seriesId = seriesId, coverUrl = null,
            localPath = null, status = "downloading", progress = 0, chapterCount = 0,
            scopeKey = scopeKey,
        )
    }

    /** 更新下载进度（第 x/y 章 → 百分比）。 */
    private suspend fun updateProgress(
        novelId: Long,
        format: NovelExportFormat,
        seriesId: Long?,
        progress: Int,
        scopeKey: String = "",
    ) {
        upsertIndex(
            novelId = novelId, title = null, format = format, seriesId = seriesId, coverUrl = null,
            localPath = null, status = "downloading", progress = progress, chapterCount = 0,
            scopeKey = scopeKey,
        )
    }

    /** 标记下载失败（failed 状态，供下载管理页标记/重试）。 */
    private suspend fun markFailed(
        novelId: Long,
        format: NovelExportFormat,
        seriesId: Long?,
        scopeKey: String,
    ) {
        upsertIndex(
            novelId = novelId, title = null, format = format, seriesId = seriesId, coverUrl = null,
            localPath = null, status = "failed", progress = 0, chapterCount = 0,
            scopeKey = scopeKey,
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
        payloadJson: String? = null,
        scopeKey: String = "",
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
                    scopeKey = scopeKey,
                    authorName = authorName,
                    authorAvatarUrl = authorAvatarUrl,
                    wordCount = wordCount,
                    favoriteCount = favoriteCount,
                    publishDate = publishDate,
                    seriesTitle = seriesTitle,
                    payloadJson = payloadJson,
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
        scopeKey: String,
    ): String {
        val first = chapters.first().first
        val fileName = "${fileNameBase(first, seriesTitle, scopeKey)}.txt"
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
        scopeKey: String,
    ): String {
        val first = chapters.first().first
        val fileName = "${fileNameBase(first, seriesTitle, scopeKey)}.epub"
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
        scopeKey: String,
    ): String {
        val first = chapters.first().first
        val fileName = "${fileNameBase(first, seriesTitle, scopeKey)}.md"
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
        scopeKey: String,
    ): String {
        val first = chapters.first().first
        val fileName = "${fileNameBase(first, seriesTitle, scopeKey)}.docx"
        // 正文插图（按块顺序下载；未解析的 pixivimage/uploadedimage 标记或下载失败跳过）
        val images = downloadImages(chapters)
        Log.d(
            TAG,
            "buildDocxFile 开始 fileName=$fileName 章节数=${chapters.size} 内嵌图片=${images.size}"
        )
        return writeExportFile(
            fileName,
            mimeFor(NovelExportFormat.DOCX),
            buildDocx(chapters, seriesTitle, images)
        )
    }

    private suspend fun buildPdfFile(
        chapters: List<Pair<Novel, NovelDocument>>,
        seriesTitle: String?,
        scopeKey: String,
    ): String {
        val first = chapters.first().first
        val fileName = "${fileNameBase(first, seriesTitle, scopeKey)}.pdf"
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
        val bytes = PdfRenderer.buildPdf(context, contents, layout)
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
    private fun downloadImages(
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
                        Log.w(
                            TAG,
                            "插图尺寸解析失败，跳过 chapter=$ci index=$ii url=${img.url.take(80)}"
                        )
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
     * 单本下载与系列导出各用各的模板（「我的」页分别配置），回退默认亦随范围。
     */
    private suspend fun fileNameBase(novel: Novel, seriesTitle: String?, scopeKey: String): String {
        val isSingle = scopeKey.isEmpty()
        val template = if (isSingle) {
            userPreferences.novelFileNameTemplate.first()
        } else {
            userPreferences.novelFileNameTemplateSeries.first()
        }
        return renderNovelFileName(
            template = template,
            title = novel.title.orEmpty(),
            author = novel.user?.name,
            id = novel.id,
            seriesTitle = seriesTitle,
            publishDate = novel.create_date,
            favoriteCount = novel.total_bookmarks,
            fallbackTemplate = if (isSingle) {
                NovelFileNameTemplate.DEFAULT_SINGLE
            } else {
                NovelFileNameTemplate.DEFAULT_SERIES
            },
        )
    }

    private suspend fun writeExportFile(fileName: String, mime: String, bytes: ByteArray): String {
        Log.d(TAG, "writeExportFile 开始 fileName=$fileName mime=$mime size=${bytes.size} bytes")
        val dirUri = userPreferences.novelExportDir.first()
        if (dirUri.isBlank()) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.d(
                    TAG,
                    "writeExportFile 目标=MediaStore(Download/PixivReader) sdk=${Build.VERSION.SDK_INT}"
                )
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
        val tree = DocumentFile.fromTreeUri(context, dirUri.toUri())
            ?: error(context.getString(R.string.novel_export_dir_unavailable))
        tree.findFile(fileName)?.delete()
        val doc = tree.createFile(mime, fileName)
            ?: error(context.getString(R.string.novel_export_create_failed))
        context.contentResolver.openOutputStream(doc.uri)?.use { it.write(bytes) }
            ?: error(context.getString(R.string.novel_export_write_failed))
        Log.d(TAG, "writeExportFile SAF写入完成 uri=${doc.uri}")
        return doc.uri.toString()
    }

    /** 通过 MediaStore 写入公共 Download/PixivReader 目录（Android 10+，免存储权限）。 */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeToMediaStore(fileName: String, mime: String, bytes: ByteArray): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/PixivReader"
            )
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

    // ── PDF 渲染（PdfRenderer 独立类：字体发现/加载/排版） ────────────────

    /**
     * 生成 PDF 内容元素流（文本结构对齐 buildTxt，插图按块顺序内嵌）。
     * 未解析的 pixivimage:/uploadedimage: 标记与下载失败/格式不支持（webp 等）的图片跳过。
     */
    private fun buildPdfContents(
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
        if (!seriesTitle.isNullOrBlank()) contents += PdfContent.Text(
            "系列：$seriesTitle",
            indent = false
        )
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
                    is NovelBlock.Paragraph -> contents += PdfContent.Text(
                        stripIndent(block.text),
                        indent = true
                    )

                    is NovelBlock.Heading -> contents += PdfContent.Text(
                        "\n${block.text}\n",
                        indent = false
                    )

                    is NovelBlock.Quote -> contents += PdfContent.Text(
                        "> ${stripIndent(block.text)}",
                        indent = true
                    )

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

                    is NovelBlock.Separator -> contents += PdfContent.Text(
                        block.symbol,
                        indent = true
                    )
                }
            }
            contents += PdfContent.Text("\n", indent = true)
        }
        return contents
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
        runCatching { ChineseConverter.convert(text, ConversionType.T2S, context) }.getOrDefault(
            text
        )
    }

    // ── 系列分页（共享 core:network fetchAllSeriesChapters，游标解析只维护一份） ────

    companion object {
        private const val TAG = "NovelExporter"
    }
}
