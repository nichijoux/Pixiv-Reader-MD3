package com.pixiv.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pixiv.reader.core.database.entity.FeedSnapshotEntity

@Dao
interface FeedSnapshotDao {

    @Query("SELECT * FROM feed_snapshot WHERE feedKey = :feedKey LIMIT 1")
    suspend fun get(feedKey: String): FeedSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FeedSnapshotEntity)

    @Query("DELETE FROM feed_snapshot WHERE feedKey = :feedKey")
    suspend fun delete(feedKey: String)

    @Query("DELETE FROM feed_snapshot")
    suspend fun clearAll()
}
