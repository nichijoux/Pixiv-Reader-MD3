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

    @Query("SELECT * FROM download_entry ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DownloadEntryEntity>>

    @Query("SELECT * FROM download_entry WHERE status = 'done'")
    suspend fun getDone(): List<DownloadEntryEntity>

    @Query("SELECT * FROM download_entry WHERE targetId = :targetId AND targetType = :type LIMIT 1")
    suspend fun get(type: String, targetId: Long): DownloadEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntryEntity)

    @Delete
    suspend fun delete(entity: DownloadEntryEntity)
}
