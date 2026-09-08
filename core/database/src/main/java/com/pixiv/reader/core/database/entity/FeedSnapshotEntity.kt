package com.pixiv.reader.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 信息流首屏快照（首页秒开）：上次成功加载的流第一页内容 + 分页游标。
 * 冷启动先展示快照内容（秒开），后台刷新成功后无感替换；解析失败时整行删除自愈。
 */
@Entity(tableName = "feed_snapshot")
data class FeedSnapshotEntity(
    /** 流标识（[com.pixiv.reader.core.network.feed.FeedSnapshotStore] 的 KEY_* 常量）。 */
    @PrimaryKey val feedKey: String,
    /** 第一页 items 的 Gson 序列化 JSON（如 List<Illust>）。 */
    val payloadJson: String,
    /** 分页游标（next_url；保存后触底加载可从快照位置继续）。 */
    val nextUrl: String? = null,
    val savedAt: Long = System.currentTimeMillis(),
)
