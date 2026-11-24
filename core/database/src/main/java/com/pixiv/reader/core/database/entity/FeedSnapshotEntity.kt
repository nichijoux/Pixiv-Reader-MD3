package com.pixiv.reader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 信息流首屏快照，供冷启动秒开；反序列化失败时整行删除自愈。 */
@Entity(tableName = "feed_snapshot")
data class FeedSnapshotEntity(
    @PrimaryKey val feedKey: String,
    val payloadJson: String,
    val nextUrl: String? = null,
    val savedAt: Long = System.currentTimeMillis(),
)
