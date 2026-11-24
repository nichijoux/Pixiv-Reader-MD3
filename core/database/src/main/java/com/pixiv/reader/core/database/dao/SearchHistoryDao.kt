package com.pixiv.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pixiv.reader.core.database.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<SearchHistoryEntity>>

    @Query("DELETE FROM search_history WHERE keyword = :keyword")
    suspend fun deleteByKeyword(keyword: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SearchHistoryEntity)

    /** 原子先删旧再插实现关键词去重置顶，防并发空窗。 */
    @Transaction
    suspend fun replaceTop(entity: SearchHistoryEntity) {
        deleteByKeyword(entity.keyword)
        upsert(entity)
    }

    @Delete
    suspend fun delete(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}
