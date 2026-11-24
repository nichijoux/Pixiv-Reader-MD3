package com.pixiv.reader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "browse_history")
data class BrowseHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val targetType: String,          // illust / novel / manga / user
    val targetId: Long,
    val title: String? = null,
    val coverUrl: String? = null,
    val payloadJson: String? = null,
    val viewedAt: Long = System.currentTimeMillis(),
)
