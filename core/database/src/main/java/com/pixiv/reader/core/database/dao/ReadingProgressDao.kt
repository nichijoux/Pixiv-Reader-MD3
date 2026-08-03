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

    @Query("SELECT * FROM reading_progress WHERE novelId = :novelId")
    suspend fun getByNovel(novelId: Long): ReadingProgressEntity?

    @Query("SELECT * FROM reading_progress WHERE seriesId = :seriesId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestInSeries(seriesId: Long): ReadingProgressEntity?

    @Query("SELECT * FROM reading_progress ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ReadingProgressEntity>>

    @Query("SELECT * FROM reading_progress ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ReadingProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReadingProgressEntity)

    @Delete
    suspend fun delete(entity: ReadingProgressEntity)

    @Query("DELETE FROM reading_progress WHERE novelId = :novelId")
    suspend fun deleteByNovel(novelId: Long)
}
