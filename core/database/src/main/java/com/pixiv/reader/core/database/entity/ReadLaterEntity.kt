package com.pixiv.reader.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** `payloadJson` 存完整卡片快照（插画=`Illust`、小说=`NovelCardData`），供免网络还原卡片。 */
@Entity(
    tableName = "read_later",
    indices = [Index(value = ["targetType", "targetId"], unique = true)],
)
data class ReadLaterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val targetType: String,          // illust / novel
    val targetId: Long,
    val title: String? = null,
    val coverUrl: String? = null,
    val payloadJson: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
)
