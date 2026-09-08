package com.pixiv.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pixiv.reader.core.database.entity.FeedSnapshotEntity

/** 信息流首屏快照 DAO：读取 / 覆盖插入 / 单键删除 / 清空（「清除缓存」联动）。 */
@Dao
interface FeedSnapshotDao {

    /** 读取指定流的快照（未保存返回 null）。 */
    @Query("SELECT * FROM feed_snapshot WHERE feedKey = :feedKey LIMIT 1")
    suspend fun get(feedKey: String): FeedSnapshotEntity?

    /** 插入 / 覆盖快照（feedKey 主键）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FeedSnapshotEntity)

    /** 删除指定流快照（解析失败自愈）。 */
    @Query("DELETE FROM feed_snapshot WHERE feedKey = :feedKey")
    suspend fun delete(feedKey: String)

    /** 清空全部快照（「清除缓存」联动）。 */
    @Query("DELETE FROM feed_snapshot")
    suspend fun clearAll()
}
