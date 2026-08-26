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
        /** 导出格式取值（[format] 列的规范常量；写入方 NovelExportWorker/NovelExporter 与
         *  消费方下载管理页共用，避免跨模块魔法字符串漂移。插画等非小说条目为空串）。 */
        const val FORMAT_TXT = "TXT"
        const val FORMAT_EPUB = "EPUB"
        const val FORMAT_PDF = "PDF"
        const val FORMAT_MARKDOWN = "MARKDOWN"
        const val FORMAT_DOCX = "DOCX"
    }
}
