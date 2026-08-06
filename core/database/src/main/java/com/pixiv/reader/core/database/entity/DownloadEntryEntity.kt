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
    val updatedAt: Long = System.currentTimeMillis(),
)
