package com.pixiv.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pixiv.reader.core.database.entity.BrowseHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowseHistoryDao {

    @Query("SELECT * FROM browse_history ORDER BY viewedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<BrowseHistoryEntity>>

    @Query("SELECT * FROM browse_history WHERE targetType = :type ORDER BY viewedAt DESC LIMIT :limit")
    fun observeByType(type: String, limit: Int = 100): Flow<List<BrowseHistoryEntity>>

    @Query("SELECT * FROM browse_history WHERE targetType = :type AND targetId = :targetId LIMIT 1")
    suspend fun get(type: String, targetId: Long): BrowseHistoryEntity?

    @Query("DELETE FROM browse_history WHERE targetType = :type AND targetId = :targetId")
    suspend fun deleteByTarget(type: String, targetId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BrowseHistoryEntity)

    /** 原子先删旧再插实现去重置顶，避免并发竞态丢记录。 */
    @Transaction
    suspend fun replaceTop(entity: BrowseHistoryEntity) {
        deleteByTarget(entity.targetType, entity.targetId)
        upsert(entity)
    }

    @Delete
    suspend fun delete(entity: BrowseHistoryEntity)

    @Query("DELETE FROM browse_history")
    suspend fun clearAll()
}
