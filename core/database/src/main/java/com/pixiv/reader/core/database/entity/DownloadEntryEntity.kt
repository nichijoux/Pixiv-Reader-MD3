package com.pixiv.reader.core.database.entity

import androidx.room.Entity

/**
 * 下载索引（P6 使用；本地文件路径 + 状态）。
 *
 * 主键为 (targetType, targetId, format, scopeKey)：同一目标可导出多种格式并存
 * （如一本小说同时导出 TXT/PDF）；scopeKey 区分同一目标的下载范围——
 * 单本=""、整系列="series"、部分分册="partial"，避免系列下载顶替单本下载的索引条目。
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
    val status: String = "pending", // pending / downloading / done / failed
    /** 下载进度（0-100 百分比；插画=字节进度，小说系列=章进度）。 */
    val progress: Int = 0,
    val pageCount: Int = 0,
    /** 插画真实宽高（下载后解析，供历史/下载列表完整显示）。 */
    val width: Int = 0,
    val height: Int = 0,
    /** 所属系列 ID（小说系列导出；>0 时重试需重建系列任务）。 */
    val seriesId: Long? = null,
    /** 导出格式（小说 "TXT"/"EPUB"/"PDF"/"MARKDOWN"/"DOCX"；插画等为空串）。主键列，不可为 null。 */
    val format: String = "",
    /** 小说作者名（下载时从详情快照，下载管理卡片展示；空=未知）。 */
    val authorName: String? = null,
    /** 小说作者头像 URL（下载时快照，卡片作者行展示）。 */
    val authorAvatarUrl: String? = null,
    /** 小说字数（下载时快照，卡片封面角标展示）。 */
    val wordCount: Int = 0,
    /** 小说收藏数（下载时快照；下载卡片暂不展示，保留数据）。 */
    val favoriteCount: Int = 0,
    /** 小说发布日期（ISO，下载时快照，卡片作者行展示）。 */
    val publishDate: String? = null,
    /** 小说所属系列标题（下载时快照，卡片系列行展示）。 */
    val seriesTitle: String? = null,
    /**
     * 完整卡片快照 JSON（与历史 BrowseHistoryEntity.payloadJson 同格式：
     * 插画=org.json 手写字段，小说=Gson(NovelCardData)）。下载管理页优先解析此处
     * 完整展示（宽高/作者/字数等），旧条目为 null 时回退下方结构字段。
     */
    val payloadJson: String? = null,
    /**
     * 下载范围键（主键列，不可为 null）：单本下载=""；整系列导出="series"；
     * 系列部分分册导出="partial"。插画等非小说条目恒为 ""。
     */
    val scopeKey: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
) {

    companion object {
        /** 下载状态取值（status 列规范常量；入队点 / Worker 写入方与下载管理页共用，避免魔法字符串漂移）。 */
        const val STATUS_PENDING = "pending" // 待同步：已入队等网络（Worker 网络约束自动开始）
        const val STATUS_DOWNLOADING = "downloading"
        const val STATUS_DONE = "done"
        const val STATUS_FAILED = "failed"

        /** 导出格式取值（[format] 列的规范常量；写入方 NovelExportWorker/NovelExporter/UgoiraExporter 与
         *  消费方下载管理页共用，避免跨模块魔法字符串漂移。插画等非小说条目为空串）。 */
        const val FORMAT_TXT = "TXT"
        const val FORMAT_EPUB = "EPUB"
        const val FORMAT_PDF = "PDF"
        const val FORMAT_MARKDOWN = "MARKDOWN"
        const val FORMAT_DOCX = "DOCX"

        /** 动图导出格式（UgoiraExporter 写入；下载管理页徽标与系统打开共用）。 */
        const val FORMAT_MP4 = "MP4"
        const val FORMAT_ZIP = "ZIP"
    }
}

/**
 * 下载状态（[DownloadEntryEntity.status] 列的类型化视图，消费方经 [DownloadStatus.from] 解析；
 * 列仍存字符串，写入方沿用 STATUS_* 常量，无迁移成本）。
 */
enum class DownloadStatus(val value: String) {
    PENDING("pending"),
    DOWNLOADING("downloading"),
    DONE("done"),
    FAILED("failed");

    companion object {
        /**
         * 从 status 列原始值解析。
         *
         * @param value 列原始值（null / 未知值回退 [PENDING]）
         * @return 解析结果（永不失败）
         */
        fun from(value: String?): DownloadStatus =
            entries.firstOrNull { it.value == value } ?: PENDING
    }
}

/**
 * 本地文件打开方式（导出格式的行为轴，替代 isParsableLocalFile / isSystemOpenFile 两个布尔判定）。
 */
enum class ExportOpenMethod {
    /** App 内可解析阅读（txt / epub / md）。 */
    IN_APP,

    /** 需系统应用打开（pdf / docx / mp4 / zip）。 */
    SYSTEM,

    /** 无本地打开语义（如插画等 format 为空串的条目）。 */
    NONE,
}

/**
 * 导出格式（[DownloadEntryEntity.format] 列的类型化视图，内聚打开方式与 MIME）。
 *
 * @property value 列存储值（与 FORMAT_* 常量一致）
 * @property openMethod 本地文件打开方式
 * @property mime 系统打开用的 MIME（仅 SYSTEM 类需要；null 时调用方按扩展名回退推断）
 */
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
        /**
         * 从 format 列原始值解析。
         *
         * @param value 列原始值（空串 / 未知值返回 null，调用方按无格式处理）
         * @return 解析结果；无法识别时 null
         */
        fun from(value: String?): ExportFormat? =
            entries.firstOrNull { it.value == value }
    }
}
