package com.pixiv.reader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

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
