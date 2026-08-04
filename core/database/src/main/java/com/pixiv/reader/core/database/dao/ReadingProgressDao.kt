package com.pixiv.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pixiv.reader.core.database.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingProgressDao {

    /** 查询某小说的阅读进度（恢复阅读位置用，字符级 charOffset）。 */
    @Query("SELECT * FROM reading_progress WHERE novelId = :novelId")
    suspend fun getByNovel(novelId: Long): ReadingProgressEntity?

    /** 查询某系列最新阅读的分册进度。 */
    @Query("SELECT * FROM reading_progress WHERE seriesId = :seriesId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestInSeries(seriesId: Long): ReadingProgressEntity?

    /** 观察全部阅读进度（按更新时间倒序）。 */
    @Query("SELECT * FROM reading_progress ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ReadingProgressEntity>>

    /** 全部阅读进度（一次性查询）。 */
    @Query("SELECT * FROM reading_progress ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ReadingProgressEntity>

    /** 插入/覆盖阅读进度（同主键 novelId 更新，阅读器防抖落库）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReadingProgressEntity)

    /** 删除单条阅读进度。 */
    @Delete
    suspend fun delete(entity: ReadingProgressEntity)

    /** 删除某小说阅读进度。 */
    @Query("DELETE FROM reading_progress WHERE novelId = :novelId")
    suspend fun deleteByNovel(novelId: Long)
}
