package com.pixiv.reader.core.database.entity

import androidx.room.Entity

/**
 * 下载索引（P6 使用；本地文件路径 + 状态）。
 *
 * 主键为 (targetType, targetId, format)：同一目标可导出多种格式并存
 * （如一本小说同时导出 TXT/PDF），插画等无格式条目 format 存空串。
 */
@Entity(tableName = "download_entry", primaryKeys = ["targetType", "targetId", "format"])
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
    val updatedAt: Long = System.currentTimeMillis(),
)
