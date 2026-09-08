package com.pixiv.reader.core.network.download

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.pixiv.api.model.GifFrame
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.common.media.Mp4FrameEncoder
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.network.R
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** ugoira 动图导出格式：MP4 视频（H.264，逐帧 pts 表达变速帧）或 ZIP 帧包（原始帧 + 延时表）。 */
enum class UgoiraExportFormat(val format: String) {
    MP4(DownloadEntryEntity.FORMAT_MP4),
    ZIP(DownloadEntryEntity.FORMAT_ZIP);

    companion object {
        /** 由下载索引 format 列还原（重试分发用）；无法识别时默认 MP4。 */
        fun from(raw: String?): UgoiraExportFormat = entries.firstOrNull { it.format == raw } ?: MP4
    }
}

/**
 * ugoira 动图导出器（参照 NovelExporter 编排模式）：
 * metadata → 下载帧包 zip（[ProgressDownloader] Range 断点续传）→ 解压帧 →
 * 按格式产出（MP4 经 [Mp4FrameEncoder] 编码 / ZIP 直接交付原始包 + 延时表 sidecar）。
 *
 * 状态全程写入下载索引（targetType="ugoira"，format=MP4/ZIP）：下载管理页展示进度卡片，
 * 完成通知由 DownloadCompletionNotifier 统一发出。MP4 成功后清理中间产物（zip + 解压帧）。
 */
@Singleton
class UgoiraExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pixivRepository: PixivRepository,
    private val progressDownloader: ProgressDownloader,
    private val downloadEntryDao: DownloadEntryDao,
) {

    /** 解压帧产物：文件 + 延时毫秒。 */
    private data class FrameEntry(val file: File, val delayMs: Int)

    /**
     * 导出动图为 MP4 / ZIP。
     *
     * @param illustId 动图作品 id
     * @param format 导出格式
     * @param illust 作品详情快照（Worker 内拉取；可能为 null——仅卡片展示信息缺失，不阻断导出）
     * @return 产物路径（MP4=文件路径，ZIP=帧包所在目录）
     */
    suspend fun export(
        illustId: Long,
        format: UgoiraExportFormat,
        illust: Illust?,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "Downloads/ugoira_$illustId").apply { mkdirs() }
            upsert(illustId, format, status = "downloading", progress = 0, localPath = dir.path, illust = illust)

            // 元数据：高清 zip 优先，缺失回退 medium（与在线播放同源）
            val meta = pixivRepository.api.getUgoiraMetadata(illustId).ugoira_metadata
                ?: error(context.getString(R.string.ugoira_error_no_metadata))
            val zipUrl = meta.zip_urls?.large ?: meta.zip_urls?.medium
                ?: error(context.getString(R.string.ugoira_error_no_zip))
            val frames = meta.frames.orEmpty()
            if (frames.isEmpty()) error(context.getString(R.string.ugoira_error_no_frames))

            // 下载阶段进度权重：ZIP 导出即最终产物（100%）；MP4 下载只占前半（50%）
            val downloadWeight = if (format == UgoiraExportFormat.ZIP) 100 else 50
            val zipFile = progressDownloader.download(zipUrl, "ugoira_$illustId/$ZIP_NAME") { done, total ->
                val pct = if (total > 0) (done * 100 / total).toInt() else 0
                updateProgress(illustId, format, (pct * downloadWeight / 100).coerceIn(0, downloadWeight))
            }.getOrThrow()

            // 解压帧（已解压文件跳过，天然断点续解压；损坏 zip 删除待重下）
            val frameDir = File(dir, FRAMES_DIR).apply { mkdirs() }
            val entries = unzipFrames(zipFile, frameDir, frames)

            val resultPath = when (format) {
                UgoiraExportFormat.ZIP -> {
                    // ZIP 交付：原始帧包 + 延时表 sidecar（供外部工具还原播放节奏）
                    writeFrameDelays(dir, entries)
                    zipFile.path
                }

                UgoiraExportFormat.MP4 -> encodeMp4(illustId, format, entries, dir, illust)
            }

            upsert(
                illustId, format, status = "done", progress = 100,
                localPath = resultPath, illust = illust, widthHeightOf(entries),
            )
            // MP4 成功后清理中间产物（zip + 解压帧），释放私有目录空间
            if (format == UgoiraExportFormat.MP4) {
                runCatching { frameDir.deleteRecursively() }
                runCatching { zipFile.delete() }
            }
            resultPath
        }.onFailure { e ->
            Log.w(TAG, "ugoira 导出失败 illustId=$illustId format=$format", e)
            // 失败仍带作品快照（保留卡片展示信息；进度停在最后写入值）
            runCatching { upsert(illustId, format, status = "failed", illust = illust) }
        }
    }

    /** MP4 编码：首帧解析尺寸 → 逐帧惰性解码（编码器用完即 recycle）→ 后半程进度（50..100）。 */
    private suspend fun encodeMp4(
        illustId: Long,
        format: UgoiraExportFormat,
        entries: List<FrameEntry>,
        dir: File,
        illust: Illust?,
    ): String {
        val out = File(dir, "ugoira_$illustId.mp4")
        // 解析首帧尺寸（帧序列尺寸一致；仅用于编码器配置与卡片宽高显示）
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(entries.first().file.path, opts)
        val width = opts.outWidth
        val height = opts.outHeight
        val delays = IntArray(entries.size) { entries[it].delayMs }
        val last = intArrayOf(49)
        Mp4FrameEncoder().encode(
            width = width,
            height = height,
            delaysMs = delays,
            frameAt = { index -> BitmapFactory.decodeFile(entries[index].file.path) },
            out = out,
            onProgress = { done, total ->
                val overall = 50 + done * 50 / total
                if (overall > last[0]) {
                    last[0] = overall
                    updateProgress(illustId, format, overall)
                }
            },
        ).getOrThrow()
        return out.path
    }

    /** 解压 zip 内各帧（已存在跳过）；zip 损坏时删除 zip 抛错（下次重下）。 */
    private fun unzipFrames(zipFile: File, frameDir: File, frames: List<GifFrame>): List<FrameEntry> {
        return try {
            java.util.zip.ZipFile(zipFile).use { zf ->
                frames.mapNotNull { frame ->
                    val entryName = frame.file ?: return@mapNotNull null
                    val out = File(frameDir, entryName.substringAfterLast('/'))
                    if (!out.exists()) {
                        zf.getInputStream(zf.getEntry(entryName)).use { it.copyTo(out.outputStream()) }
                    }
                    FrameEntry(file = out, delayMs = (frame.delay ?: 80).coerceAtLeast(10))
                }
            }
        } catch (e: Exception) {
            runCatching { zipFile.delete() }
            throw e
        }
    }

    /** 延时表 sidecar（帧文件名 + 毫秒），与 zip 同目录。 */
    private fun writeFrameDelays(dir: File, entries: List<FrameEntry>) {
        val json = org.json.JSONArray().apply {
            entries.forEach { entry ->
                put(org.json.JSONObject().apply {
                    put("file", entry.file.name)
                    put("delayMs", entry.delayMs)
                })
            }
        }
        File(dir, DELAYS_FILE).writeText(json.toString())
    }

    /** 首帧尺寸（卡片按真实比例显示；解码失败回退 0 走结构字段）。 */
    private fun widthHeightOf(entries: List<FrameEntry>): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(entries.first().file.path, opts)
        return opts.outWidth to opts.outHeight
    }

    /** 进度节流写入（百分比有变化才落库）。 */
    private suspend fun updateProgress(illustId: Long, format: UgoiraExportFormat, progress: Int) {
        runCatching { downloadEntryDao.updateProgress("ugoira", illustId, format.format, progress.coerceIn(0, 100)) }
    }

    /** 写/更新下载索引条目（与插画下载同款 payloadJson 快照格式，下载管理页通用解析）。 */
    private suspend fun upsert(
        illustId: Long,
        format: UgoiraExportFormat,
        status: String,
        progress: Int = 0,
        localPath: String? = null,
        illust: Illust? = null,
        widthHeight: Pair<Int, Int> = 0 to 0,
    ) {
        runCatching {
            downloadEntryDao.upsert(
                DownloadEntryEntity(
                    targetId = illustId,
                    targetType = "ugoira",
                    title = illust?.title.orEmpty(),
                    coverUrl = illust?.image_urls?.medium ?: illust?.image_urls?.square_medium,
                    localPath = localPath,
                    status = status,
                    progress = progress,
                    pageCount = 1,
                    width = widthHeight.first,
                    height = widthHeight.second,
                    format = format.format,
                    payloadJson = illust?.let { illustPayload(it) },
                ),
            )
        }.onFailure { Log.w(TAG, "写下载索引失败 illustId=$illustId status=$status", it) }
    }

    /** 完整卡片快照（与插画下载/浏览历史同格式）。 */
    private fun illustPayload(illust: Illust): String = org.json.JSONObject().apply {
        put("id", illust.id)
        put("title", illust.title.orEmpty())
        put("coverUrl", illust.image_urls?.medium ?: illust.image_urls?.square_medium)
        put("width", illust.width)
        put("height", illust.height)
        put("bookmarks", illust.total_bookmarks ?: 0)
        put("pageCount", illust.page_count)
        put("isBookmarked", illust.is_bookmarked == true)
    }.toString()

    private companion object {
        const val TAG = "UgoiraExporter"
        const val ZIP_NAME = "ugoira.zip"
        const val FRAMES_DIR = "frames"
        const val DELAYS_FILE = "frame_delays.json"
    }
}
