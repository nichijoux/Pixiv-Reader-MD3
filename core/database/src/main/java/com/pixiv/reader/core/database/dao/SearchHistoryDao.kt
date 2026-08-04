package com.pixiv.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pixiv.reader.core.database.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {

    /** 观察最近搜索记录（按时间倒序，最多 [limit] 条）。 */
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<SearchHistoryEntity>>

    /** 删除同关键词旧记录（先删旧再插入，保证同词只保留最新一条并置顶）。 */
    @Query("DELETE FROM search_history WHERE keyword = :keyword")
    suspend fun deleteByKeyword(keyword: String)

    /** 插入/覆盖记录（配合 [deleteByKeyword] 实现去重置顶）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SearchHistoryEntity)

    /** 删除单条记录。 */
    @Delete
    suspend fun delete(entity: SearchHistoryEntity)

    /** 清空全部搜索历史。 */
    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}
