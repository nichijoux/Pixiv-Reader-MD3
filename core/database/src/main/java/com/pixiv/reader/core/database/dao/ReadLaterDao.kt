package com.pixiv.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pixiv.reader.core.database.entity.ReadLaterEntity
import kotlinx.coroutines.flow.Flow

/** 轻量目标键投影，避开 `payloadJson` 大列供卡片集合态判重。 */
data class ReadLaterTargetRow(
    val targetType: String,
    val targetId: Long,
)

@Dao
interface ReadLaterDao {

    @Query("SELECT * FROM read_later ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<ReadLaterEntity>>

    @Query("SELECT targetType, targetId FROM read_later")
    fun observeAllTargets(): Flow<List<ReadLaterTargetRow>>

    @Query("SELECT * FROM read_later WHERE targetType = :type ORDER BY addedAt DESC")
    fun observeByType(type: String): Flow<List<ReadLaterEntity>>

    @Query("SELECT * FROM read_later WHERE targetType = :type AND targetId = :targetId LIMIT 1")
    suspend fun get(type: String, targetId: Long): ReadLaterEntity?

    @Query("DELETE FROM read_later WHERE targetType = :type AND targetId = :targetId")
    suspend fun deleteByTarget(type: String, targetId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReadLaterEntity)

    /** 原子先删旧再插实现去重置顶，防唯一索引冲突与并发空窗。 */
    @Transaction
    suspend fun replaceTop(entity: ReadLaterEntity) {
        deleteByTarget(entity.targetType, entity.targetId)
        upsert(entity)
    }

    @Delete
    suspend fun delete(entity: ReadLaterEntity)

    @Query("DELETE FROM read_later")
    suspend fun clearAll()
}
