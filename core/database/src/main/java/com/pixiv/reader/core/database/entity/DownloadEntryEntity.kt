package com.pixiv.reader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 下载索引（P6 使用；本地文件路径 + 状态）。 */
@Entity(tableName = "download_entry")
data class DownloadEntryEntity(
    @PrimaryKey val targetId: Long,
    val targetType: String,        // illust / ugoira / novel
    val title: String? = null,
    val coverUrl: String? = null,
    val localPath: String? = null,
    val status: String = "pending", // pending / downloading / done / failed
    val pageCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)
