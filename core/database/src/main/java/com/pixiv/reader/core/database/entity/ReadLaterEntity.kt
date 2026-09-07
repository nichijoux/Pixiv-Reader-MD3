package com.pixiv.reader.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 稍后再看（本地暂存，无官方接口）：卡片长按加入，稍后再看页消费，查看后手动移除。
 * `payloadJson` 存完整卡片快照（与浏览历史同格式：插画存 `Illust`、小说存 `NovelCardData`），
 * 供稍后再看页免网络还原卡片。
 */
@Entity(
    tableName = "read_later",
    // 同一目标只存一条（长按重复加入不产生重复行）
    indices = [Index(value = ["targetType", "targetId"], unique = true)],
)
data class ReadLaterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val targetType: String,          // illust / novel
    val targetId: Long,
    val title: String? = null,
    val coverUrl: String? = null,
    val payloadJson: String? = null, // 冗余的完整卡片快照（离线还原展示用）
    val addedAt: Long = System.currentTimeMillis(),
)
