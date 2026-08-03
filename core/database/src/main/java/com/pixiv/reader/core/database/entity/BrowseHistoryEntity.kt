package com.pixiv.reader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 浏览历史（本地，无官方接口；可选 pixshaft 云同步二期）。 */
@Entity(tableName = "browse_history")
data class BrowseHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val targetType: String,          // illust / novel / manga / user
    val targetId: Long,
    val title: String? = null,
    val coverUrl: String? = null,
    val payloadJson: String? = null, // 冗余的快照（标题/作者/进度等）
    val viewedAt: Long = System.currentTimeMillis(),
)
