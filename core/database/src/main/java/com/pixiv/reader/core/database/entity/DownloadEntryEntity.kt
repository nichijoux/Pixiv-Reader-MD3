package com.pixiv.reader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 下载索引（P6 使用；本地文件路径 + 状态）。 */
@Entity(tableName = "download_entry")
data class DownloadEntryEntity(
    @PrimaryKey val targetId: Long,
    val targetType: String,        // illust / ugoira / novel / novel_offline
    val title: String? = null,
    val coverUrl: String? = null,
    val localPath: String? = null,
    val status: String = "pending", // pending / downloading / done / failed
    /** 下载进度（0-100 百分比；插画=字节进度，小说系列/离线=章进度）。 */
    val progress: Int = 0,
    val pageCount: Int = 0,
    /** 插画真实宽高（下载后解析，供历史/下载列表完整显示）。 */
    val width: Int = 0,
    val height: Int = 0,
    /** 所属系列 ID（小说系列导出/离线；>0 时重试需重建系列任务）。 */
    val seriesId: Long? = null,
    /** 导出格式（小说导出 "TXT"/"EPUB"；供重试重建任务）。 */
    val format: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)
