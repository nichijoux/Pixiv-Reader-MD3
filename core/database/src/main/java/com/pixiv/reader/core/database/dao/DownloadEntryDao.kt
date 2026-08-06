package com.pixiv.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadEntryDao {

    /** 观察全部下载索引（按更新时间倒序，下载管理页数据源）。 */
    @Query("SELECT * FROM download_entry ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DownloadEntryEntity>>

    /** 查询所有已完成的下载（status='done'）。 */
    @Query("SELECT * FROM download_entry WHERE status = 'done'")
    suspend fun getDone(): List<DownloadEntryEntity>

    /** 查询某类型某目标的下载记录。 */
    @Query("SELECT * FROM download_entry WHERE targetId = :targetId AND targetType = :type LIMIT 1")
    suspend fun get(type: String, targetId: Long): DownloadEntryEntity?

    /** 按类型批量删除（如清除离线缓存时删除所有 novel_offline 索引）。 */
    @Query("DELETE FROM download_entry WHERE targetType = :type")
    suspend fun deleteByType(type: String)

    /** 插入/覆盖下载索引（同主键 targetId 更新）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntryEntity)

    /** 更新单条下载进度（轻量 UPDATE，供分块下载节流写）。 */
    @Query("UPDATE download_entry SET progress = :progress, updatedAt = :updatedAt WHERE targetId = :targetId")
    suspend fun updateProgress(targetId: Long, progress: Int, updatedAt: Long = System.currentTimeMillis())

    /** 删除单条下载索引。 */
    @Delete
    suspend fun delete(entity: DownloadEntryEntity)
}
