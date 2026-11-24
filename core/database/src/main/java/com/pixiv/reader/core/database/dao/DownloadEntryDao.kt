package com.pixiv.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 轻量状态投影（避开 `payloadJson` 大列）。
 * `format` 与 `scopeKey` 为主键列，通知去重必须包含，否则同作品多格式或单本/系列会撞键漏通知。
 */
data class DownloadStatusRow(
    val targetType: String,
    val targetId: Long,
    val format: String,
    val status: String,
    val title: String?,
    val scopeKey: String = "",
    val progress: Int = 0,
)

@Dao
interface DownloadEntryDao {

    @Query("SELECT * FROM download_entry ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DownloadEntryEntity>>

    @Query("SELECT targetType, targetId, format, status, title, scopeKey, progress FROM download_entry")
    fun observeAllStatuses(): Flow<List<DownloadStatusRow>>

    @Query("SELECT status FROM download_entry WHERE targetType = :targetType AND targetId = :targetId")
    fun observeStatuses(targetType: String, targetId: Long): Flow<List<String>>

    @Query(
        "SELECT status FROM download_entry WHERE targetType = :targetType AND targetId = :targetId " +
            "AND format = :format AND scopeKey = :scopeKey LIMIT 1",
    )
    fun observeStatus(
        targetType: String,
        targetId: Long,
        format: String = "",
        scopeKey: String = "",
    ): Flow<String?>

    /** 仅测试 fake 覆写保持接口一致，主代码未使用。 */
    @Query("SELECT * FROM download_entry WHERE status = 'done'")
    suspend fun getDone(): List<DownloadEntryEntity>

    @Query(
        "SELECT * FROM download_entry WHERE targetId = :targetId AND targetType = :type " +
            "AND format = :format AND scopeKey = :scopeKey LIMIT 1",
    )
    suspend fun get(type: String, targetId: Long, format: String = "", scopeKey: String = ""): DownloadEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntryEntity)

    @Query(
        "UPDATE download_entry SET progress = :progress, updatedAt = :updatedAt " +
            "WHERE targetType = :type AND targetId = :targetId AND format = :format AND scopeKey = :scopeKey",
    )
    suspend fun updateProgress(type: String, targetId: Long, format: String, progress: Int, updatedAt: Long = System.currentTimeMillis(), scopeKey: String = "")

    @Delete
    suspend fun delete(entity: DownloadEntryEntity)
}
