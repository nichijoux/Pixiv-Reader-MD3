package com.pixiv.reader.core.database.entity

import androidx.room.Entity

/**
 * 主键 `(targetType, targetId, format, scopeKey)`：允许同作品多格式并存，
 * `scopeKey`（`""` 单本 / `"series"` 整系列 / `"partial"` 部分分册）防止系列导出顶替单本条目。
 */
@Entity(
    tableName = "download_entry",
    primaryKeys = ["targetType", "targetId", "format", "scopeKey"],
)
data class DownloadEntryEntity(
    val targetId: Long,
    val targetType: String,        // illust / ugoira / novel
    val title: String? = null,
    val coverUrl: String? = null,
    val localPath: String? = null,
    val status: String = "pending",
    /** 0-100 百分比（插画=字节进度，小说系列=章进度）。 */
    val progress: Int = 0,
    val pageCount: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    /** >0 时重试需重建系列任务。 */
    val seriesId: Long? = null,
    /** 主键列不可为 null；插画等非小说条目为空串。 */
    val format: String = "",
    val authorName: String? = null,
    val authorAvatarUrl: String? = null,
    val wordCount: Int = 0,
    val favoriteCount: Int = 0,
    val publishDate: String? = null,
    val seriesTitle: String? = null,
    /** 完整卡片快照 JSON（插画=org.json，小说=Gson(NovelCardData)）；旧条目为 null 时回退结构字段。 */
    val payloadJson: String? = null,
    /** 主键列不可为 null；非小说条目恒为 `""`。 */
    val scopeKey: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
) {

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_DOWNLOADING = "downloading"
        const val STATUS_DONE = "done"
        const val STATUS_FAILED = "failed"

        const val FORMAT_TXT = "TXT"
        const val FORMAT_EPUB = "EPUB"
        const val FORMAT_PDF = "PDF"
        const val FORMAT_MARKDOWN = "MARKDOWN"
        const val FORMAT_DOCX = "DOCX"

        const val FORMAT_MP4 = "MP4"
        const val FORMAT_ZIP = "ZIP"
    }
}

enum class DownloadStatus(val value: String) {
    PENDING("pending"),
    DOWNLOADING("downloading"),
    DONE("done"),
    FAILED("failed");

    companion object {
        fun from(value: String?): DownloadStatus =
            entries.firstOrNull { it.value == value } ?: PENDING
    }
}

enum class ExportOpenMethod {
    IN_APP,
    SYSTEM,
    NONE,
}

enum class ExportFormat(
    val value: String,
    val openMethod: ExportOpenMethod,
    val mime: String?,
) {
    TXT("TXT", ExportOpenMethod.IN_APP, null),
    EPUB("EPUB", ExportOpenMethod.IN_APP, null),
    PDF("PDF", ExportOpenMethod.SYSTEM, "application/pdf"),
    MARKDOWN("MARKDOWN", ExportOpenMethod.IN_APP, null),
    DOCX("DOCX", ExportOpenMethod.SYSTEM, "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    MP4("MP4", ExportOpenMethod.SYSTEM, "video/mp4"),
    ZIP("ZIP", ExportOpenMethod.SYSTEM, "application/zip");

    companion object {
        fun from(value: String?): ExportFormat? =
            entries.firstOrNull { it.value == value }
    }
}
