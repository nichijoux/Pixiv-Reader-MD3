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
import kotlinx.coroutines.CancellationException
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
 * 导出目录优先用户 SAF，其次 Android 10+ 走 MediaStore 写 `Download/PixivReader/`，
 * Android 8-9 因后台 Worker 无法申请存储权限回退私有目录；断点缓存始终在 `filesDir/Downloads/novels/.export_*`，成功后清理。
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

    /** 断点缓存目录 `filesDir/Downloads/novels/.export_*`，按小说 ID、格式与 scopeKey 隔离防互相污染。 */
    private fun cacheDir(novelId: Long, format: NovelExportFormat, scopeKey: String): File {
        val scopePart = scopeKey.ifEmpty { "single" }
        return File(
            context.filesDir,
            "Downloads/novels/.export_${novelId}_${format.name}_$scopePart"
        ).apply { mkdirs() }
    }

    /**
     * 断点续传导出单本/整系列（`seriesId > 0`）/部分分册（`chapterIds` 非空时合并为单文件），
     * 返回本地路径或 `content://` URI；已缓存章节重跑时直接读盘跳过下载。
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
            cacheDir(novelId, format, scopeKey).deleteRecursively()
        }.onFailure {
            // 协程取消（用户删除任务 / Worker 停止）不落 failed：保留已缓存章节供下次续传，
            // 也避免已删除的下载条目被「复活」成失败卡并触发重试
            if (it is CancellationException) throw it
            Log.e(
                TAG,
                "导出失败 novelId=$novelId format=$format seriesId=$seriesId chapterIds=$chapterIds",
                it
            )
            markFailed(novelId, format, seriesId, scopeKey)
        }
            .map { it.localPath }
    }

    private suspend fun loadChaptersWithCache(
        novelId: Long,
        format: NovelExportFormat,
        seriesId: Long?,
        chapterIds: List<Long>? = null,
        scopeKey: String = "",
    ): Pair<List<Pair<Novel, NovelDocument>>, Novel> {
        val cache = cacheDir(novelId, format, scopeKey)
        val chapters = if ((seriesId != null) && (seriesId > 0L)) {
            val novels = fetchAllSeriesChapters(pixivRepository, seriesId)
            Log.d(TAG, "系列分册获取完成 seriesId=$seriesId 总数=${novels.size}")
            if (novels.isEmpty()) error(context.getString(R.string.novel_export_series_empty))
            val selected = if (chapterIds.isNullOrEmpty()) {
                novels
            } else {
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
        val detail = loaded.first ?: error(context.getString(R.string.novel_not_found))
        runCatching { cacheFile.writeText(encodeChapterCache(detail, loaded.second)) }
        Log.d(TAG, "章节网络加载完成 index=$index chapterId=$chapterId title=${detail.title}")
        return detail to loaded.second
    }

    /** 断点缓存 JSON 契约：`document` 字段经 [NovelDocumentCodec.encode] 写入，由 [decodeChapterCache] 恢复断点（运行时在用）。 */
    private fun encodeChapterCache(novel: Novel, document: NovelDocument): String {
        val obj = JSONObject()
        obj.put("id", novel.id)
        obj.put("title", novel.title.orEmpty())
        obj.put("caption", novel.caption.orEmpty())
        novel.create_date?.let { obj.put("createDate", it) }
        novel.text_length?.let { obj.put("textLength", it) }
        novel.total_bookmarks?.let { obj.put("totalBookmarks", it) }
        novel.series?.let {
            obj.put("seriesId", it.id)
            obj.put("seriesTitle", it.title.orEmpty())
        }
        novel.user?.let {
            obj.put("userName", it.name.orEmpty())
            // 作者 ID 与头像一并缓存，断点续传后文件名模板与下载索引快照仍可取回完整作者信息。
            obj.put("userId", it.id)
            it.profile_image_urls?.best()?.let { avatar -> obj.put("userAvatar", avatar) }
        }
        novel.image_urls?.medium?.let { obj.put("cover", it) }
        obj.put("document", NovelDocumentCodec.encode(document))
        return obj.toString()
    }

    /** 与 [encodeChapterCache] 配对，经 [NovelDocumentCodec.decode] 恢复断点缓存，损坏返回 null。 */
    private fun decodeChapterCache(json: String): Pair<Novel, NovelDocument>? = runCatching {
        val obj = JSONObject(json)
        val id = obj.optLong("id")
        val doc = NovelDocumentCodec.decode(obj.optString("document")) ?: return@runCatching null
        val userAvatar = obj.optString("userAvatar").ifEmpty { null }
        val novel = Novel(
            id = id,
            title = obj.optString("title"),
            caption = obj.optString("caption").ifEmpty { null },
            image_urls = ImageUrls(medium = obj.optString("cover").ifEmpty { null }),
            create_date = obj.optString("createDate").ifEmpty { null },
            text_length = if (obj.has("textLength")) obj.optInt("textLength") else null,
            total_bookmarks = if (obj.has("totalBookmarks")) obj.optInt("totalBookmarks") else null,
            series = if (obj.has("seriesId")) Series(
                obj.optLong("seriesId"),
                obj.optString("seriesTitle")
            ) else null,
            user = obj.optString("userName").takeIf { it.isNotEmpty() }
                ?.let {
                    User(
                        id = obj.optLong("userId", 0L),
                        name = it,
                        profile_image_urls = userAvatar?.let { url -> ImageUrls(medium = url) },
                    )
                },
        )
        novel to doc
    }.getOrNull()

    /**
     * 系列/部分导出卡片维度为系列而非首章，`title`/`payloadJson` 传 null 以保留入队快照不被首章覆盖；
     * 必须转发 `scopeKey`，否则终态会落到 `scopeKey=""` 另一主键行，导致系列卡与首章卡分裂。
     */
    private suspend fun recordDownload(
        novelId: Long,
        format: NovelExportFormat,
        seriesId: Long?,
        coverNovel: Novel,
        localPath: String,
        chapterCount: Int,
        scopeKey: String = "",
    ) {
        val seriesScope = scopeKey.isNotEmpty()
        upsertIndex(
            novelId = novelId,
            title = if (seriesScope) null else coverNovel.title,
            format = format,
            seriesId = seriesId,
            coverUrl = coverNovel.image_urls?.medium ?: coverNovel.image_urls?.square_medium,
            localPath = localPath,
            status = DownloadEntryEntity.STATUS_DONE,
            progress = 100,
            chapterCount = chapterCount,
            authorName = coverNovel.user?.name,
            authorAvatarUrl = coverNovel.user?.profile_image_urls?.best(),
            wordCount = coverNovel.text_length ?: 0,
            favoriteCount = coverNovel.total_bookmarks ?: 0,
            publishDate = coverNovel.create_date,
            seriesTitle = coverNovel.series?.title,
            payloadJson = if (seriesScope) null else Gson().toJson(coverNovel.toCardData()),
            scopeKey = scopeKey,
        )
    }

    private suspend fun markDownloading(
        novelId: Long,
        format: NovelExportFormat,
        seriesId: Long?,
        scopeKey: String,
    ) {
        upsertIndex(
            novelId = novelId, title = null, format = format, seriesId = seriesId, coverUrl = null,
            localPath = null, status = DownloadEntryEntity.STATUS_DOWNLOADING, progress = 0, chapterCount = 0,
            scopeKey = scopeKey,
        )
    }

    private suspend fun updateProgress(
        novelId: Long,
        format: NovelExportFormat,
        seriesId: Long?,
        progress: Int,
        scopeKey: String = "",
    ) {
        upsertIndex(
            novelId = novelId, title = null, format = format, seriesId = seriesId, coverUrl = null,
            localPath = null, status = DownloadEntryEntity.STATUS_DOWNLOADING, progress = progress, chapterCount = 0,
            scopeKey = scopeKey,
        )
    }

    private suspend fun markFailed(
        novelId: Long,
        format: NovelExportFormat,
        seriesId: Long?,
        scopeKey: String,
    ) {
        upsertIndex(
            novelId = novelId, title = null, format = format, seriesId = seriesId, coverUrl = null,
            localPath = null, status = DownloadEntryEntity.STATUS_FAILED, progress = 0, chapterCount = 0,
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
            // 中间态（markDownloading/updateProgress/markFailed）传入的展示字段为 null：
            // 与现有行合并防 REPLACE 抹掉卡片快照（待同步→下载中卡片不闪空）；终态字段齐全，合并不生效
            val existing = downloadEntryDao.get("novel", novelId, format.name, scopeKey)
            downloadEntryDao.upsert(
                DownloadEntryEntity(
                    targetId = novelId,
                    targetType = "novel",
                    // 新标题（无后缀基底）加格式后缀；中间态回退现有标题（已含后缀，不再重复拼接）
                    title = if (title != null) "$title（${format.name}）" else existing?.title,
                    coverUrl = coverUrl ?: existing?.coverUrl,
                    localPath = localPath,
                    status = status,
                    progress = progress,
                    pageCount = chapterCount,
                    seriesId = seriesId ?: existing?.seriesId,
                    format = format.name,
                    scopeKey = scopeKey,
                    authorName = authorName ?: existing?.authorName,
                    authorAvatarUrl = authorAvatarUrl ?: existing?.authorAvatarUrl,
                    wordCount = if (wordCount > 0) wordCount else existing?.wordCount ?: 0,
                    favoriteCount = if (favoriteCount > 0) favoriteCount else existing?.favoriteCount ?: 0,
                    publishDate = publishDate ?: existing?.publishDate,
                    seriesTitle = seriesTitle ?: existing?.seriesTitle,
                    payloadJson = payloadJson ?: existing?.payloadJson,
                ),
            )
        }
    }

    private data class ExportResult(
        val localPath: String,
        val coverNovel: Novel,
        val chapterCount: Int
    )

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
        val images = mutableListOf<ExportImage>()
        // 封面：保留真实格式扩展名与 MIME，避免非 JPG 资源与容器元数据不一致。
        val coverUrl = coverNovel.image_urls?.medium ?: coverNovel.image_urls?.square_medium
        if (!coverUrl.isNullOrBlank()) {
            val coverMime = mimeFromUrl(coverUrl)
            val coverExt = extForMime(coverMime)
            downloadImage(coverUrl)?.let { images += ExportImage("cover.$coverExt", it, mime = coverMime) }
        }
        chapters.forEachIndexed { ci, (_, document) ->
            document.blocks.filterIsInstance<NovelBlock.Image>().forEachIndexed { ii, img ->
                if (img.url.startsWith("pixivimage:") || img.url.startsWith("uploadedimage:")) return@forEachIndexed
                val imgMime = mimeFromUrl(img.url)
                val imgExt = extForMime(imgMime)
                downloadImage(img.url)?.let { images += ExportImage("img_${ci}_${ii}.$imgExt", it, mime = imgMime) }
            }
        }
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
        val images = downloadImages(chapters)
        Log.d(TAG, "buildMarkdownFile 开始 fileName=$fileName 内嵌图片=${images.count { it != null }}")
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
        val images = downloadImages(chapters)
        Log.d(
            TAG,
            "buildDocxFile 开始 fileName=$fileName 章节数=${chapters.size} 内嵌图片=${images.count { it != null }}"
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
        // 行高倍数 = 1.6 基线 + 阅读器增量，对齐阅读器排版
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

    /** pdfbox 2.x 不支持嵌入 WebP，转 JPEG（质量 90）供 PDF 使用。 */
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

    /** 返回列表与全局插图块序严格等长，未解析标记或失败项以 null 占位，防止下游按块序取图整体错位。 */
    private fun downloadImages(
        chapters: List<Pair<Novel, NovelDocument>>,
    ): List<ExportImage?> {
        val images = mutableListOf<ExportImage?>()
        // 单调递增全局序号，避免单章超过 100 张图时文件名与 docPr id 冲突。
        var imageSeq = 0
        chapters.forEachIndexed { ci, (_, document) ->
            document.blocks.filterIsInstance<NovelBlock.Image>()
                .forEachIndexed { ii, img ->
                    if (img.url.startsWith("pixivimage:") || img.url.startsWith("uploadedimage:")) {
                        images += null
                        return@forEachIndexed
                    }
                    val bytes = downloadImage(img.url)
                    if (bytes == null) {
                        images += null
                        return@forEachIndexed
                    }
                    val dims = imageDimensions(bytes)
                    val mime = mimeFromUrl(img.url)
                    if (dims == null) {
                        Log.w(
                            TAG,
                            "插图尺寸解析失败，跳过 chapter=$ci index=$ii url=${img.url.take(80)}"
                        )
                        images += null
                        return@forEachIndexed
                    }
                    val ext = extForMime(mime)
                    images += ExportImage(
                        ref = "image${++imageSeq}.$ext",
                        bytes = bytes,
                        mime = mime,
                        width = dims.first,
                        height = dims.second,
                    )
                }
        }
        return images
    }

    private fun extForMime(mime: String): String = when (mime) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "jpg"
    }

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
                // Android 8-9 公共目录需运行时权限，后台 Worker 无法申请，回退私有目录
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

    private fun buildPdfContents(
        chapters: List<Pair<Novel, NovelDocument>>,
        seriesTitle: String?,
    ): List<PdfContent> {
        val images = downloadImages(chapters)
        var imgIdx = 0
        val contents = mutableListOf<PdfContent>()
        val first = chapters.first().first
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
                            // pdfbox 2.x 不支持嵌入 WebP，先转 JPEG
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


    private fun downloadImage(url: String): ByteArray? {
        return runCatching {
            val request = Request.Builder().url(url).build()
            pixivRepository.imageClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.bytes()
            }
        }.getOrNull()
    }

    /** 首次调用由 OpenCC 自动解压词典到 filesDir；转换失败回退原文避免中断导出。 */
    private fun simplifyConverter(): (String) -> String = { text ->
        runCatching { ChineseConverter.convert(text, ConversionType.T2S, context) }.getOrDefault(
            text
        )
    }

    companion object {
        private const val TAG = "NovelExporter"
    }
}
