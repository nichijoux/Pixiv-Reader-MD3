package com.pixiv.reader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 小说阅读进度（字符级）。
 * 同一系列下按 chapter_order 排序，char_offset 记录当前章节内滚动到的字符位置。
 */
@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey val novelId: Long,
    val seriesId: Long = 0L,
    val title: String? = null,
    val coverUrl: String? = null,
    val chapterOrder: Int = 0,
    val charOffset: Int = 0,
    val percentage: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)
