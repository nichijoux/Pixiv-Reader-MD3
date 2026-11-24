package com.pixiv.reader.core.network.download

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.common.media.Mp4FrameEncoder
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.network.R
import com.pixiv.reader.core.network.model.bestCoverUrl
import com.pixiv.reader.core.network.model.snapshotPayload
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.core.network.ugoira.UgoiraFrame
import com.pixiv.reader.core.network.ugoira.UgoiraFrameExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class UgoiraExportFormat(val format: String) {
    MP4(DownloadEntryEntity.FORMAT_MP4),
    ZIP(DownloadEntryEntity.FORMAT_ZIP);

    companion object {
        fun from(raw: String?): UgoiraExportFormat = entries.firstOrNull { it.format == raw } ?: MP4
    }
}

/** 动图导出器：Range 续传帧包并导出 MP4/ZIP，全程写下载索引；MP4 成功后清理中间产物。 */
@Singleton
class UgoiraExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pixivRepository: PixivRepository,
    private val progressDownloader: ProgressDownloader,
    private val downloadEntryDao: DownloadEntryDao,
) {

    suspend fun export(
        illustId: Long,
        format: UgoiraExportFormat,
        illust: Illust?,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "Downloads/ugoira_$illustId").apply { mkdirs() }
            upsert(illustId, format, status = DownloadEntryEntity.STATUS_DOWNLOADING, progress = 0, localPath = dir.path, illust = illust)

            val meta = pixivRepository.api.getUgoiraMetadata(illustId).ugoira_metadata
                ?: error(context.getString(R.string.ugoira_error_no_metadata))
            val zipUrl = meta.zip_urls?.large ?: meta.zip_urls?.medium
                ?: error(context.getString(R.string.ugoira_error_no_zip))
            val frames = meta.frames.orEmpty()
            if (frames.isEmpty()) error(context.getString(R.string.ugoira_error_no_frames))

            val downloadWeight = if (format == UgoiraExportFormat.ZIP) 100 else 50
            // MP4 中间帧包须独立命名，避免覆盖用户已导出的 ZIP 或在完成后误删
            val zipName = if (format == UgoiraExportFormat.ZIP) ZIP_NAME else MP4_SOURCE_ZIP_NAME
            var lastWrittenPct = -1
            val zipFile = progressDownloader.download(zipUrl, "ugoira_$illustId/$zipName") { done, total ->
                val pct = if (total > 0) (done * 100 / total).toInt() else 0
                // 每 8KB 回调一次，仅百分比变化才落库以减少写入
                if (pct != lastWrittenPct) {
                    lastWrittenPct = pct
                    updateProgress(illustId, format, (pct * downloadWeight / 100).coerceIn(0, downloadWeight))
                }
            }.getOrThrow()

            val frameDir = File(dir, FRAMES_DIR).apply { mkdirs() }
            val entries = try {
                UgoiraFrameExtractor.unzip(zipFile, frameDir, frames)
            } catch (e: Exception) {
                // 删除损坏/缺帧 zip，确保下次重试重新下载
                runCatching { zipFile.delete() }
                throw e
            }

            val resultPath = when (format) {
                UgoiraExportFormat.ZIP -> {
                    writeFrameDelays(dir, entries)
                    zipFile.path
                }

                UgoiraExportFormat.MP4 -> encodeMp4(illustId, format, entries, dir, illust)
            }

            upsert(
                illustId, format, status = DownloadEntryEntity.STATUS_DONE, progress = 100,
                localPath = resultPath, illust = illust, widthHeightOf(entries),
            )
            if (format == UgoiraExportFormat.MP4) {
                runCatching { frameDir.deleteRecursively() }
                runCatching { zipFile.delete() }
            }
            resultPath
        }.onFailure { e ->
            // 取消时不落 failed，保留断点现场供续传
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "ugoira 导出失败 illustId=$illustId format=$format", e)
            // 失败仍写快照保留卡片信息，进度归零后重试走断点续传
            runCatching { upsert(illustId, format, status = DownloadEntryEntity.STATUS_FAILED, illust = illust) }
        }
    }

    private suspend fun encodeMp4(
        illustId: Long,
        format: UgoiraExportFormat,
        entries: List<UgoiraFrame>,
        dir: File,
        illust: Illust?,
    ): String {
        val out = File(dir, "ugoira_$illustId.mp4")
        val (width, height) = decodeBounds(entries.first().file)
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

    private fun writeFrameDelays(dir: File, entries: List<UgoiraFrame>) {
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

    private fun widthHeightOf(entries: List<UgoiraFrame>): Pair<Int, Int> = decodeBounds(entries.first().file)

    private fun decodeBounds(file: File): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, opts)
        return opts.outWidth to opts.outHeight
    }

    private suspend fun updateProgress(illustId: Long, format: UgoiraExportFormat, progress: Int) {
        runCatching { downloadEntryDao.updateProgress("ugoira", illustId, format.format, progress.coerceIn(0, 100)) }
    }

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
                    coverUrl = illust?.bestCoverUrl,
                    localPath = localPath,
                    status = status,
                    progress = progress,
                    pageCount = 1,
                    width = widthHeight.first,
                    height = widthHeight.second,
                    format = format.format,
                    payloadJson = illust?.snapshotPayload(),
                ),
            )
        }.onFailure { Log.w(TAG, "写下载索引失败 illustId=$illustId status=$status", it) }
    }

    private companion object {
        const val TAG = "UgoiraExporter"

        const val ZIP_NAME = "ugoira.zip"

        const val MP4_SOURCE_ZIP_NAME = "ugoira_mp4_source.zip"
        const val FRAMES_DIR = "frames"
        const val DELAYS_FILE = "frame_delays.json"
    }
}
