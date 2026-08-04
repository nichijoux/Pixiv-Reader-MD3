package com.pixiv.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pixiv.reader.core.database.entity.BrowseHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowseHistoryDao {

    /** 观察最近浏览记录（按时间倒序，最多 [limit] 条）。 */
    @Query("SELECT * FROM browse_history ORDER BY viewedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<BrowseHistoryEntity>>

    /** 按类型观察最近浏览记录（如历史页插画/小说/用户筛选）。 */
    @Query("SELECT * FROM browse_history WHERE targetType = :type ORDER BY viewedAt DESC LIMIT :limit")
    fun observeByType(type: String, limit: Int = 100): Flow<List<BrowseHistoryEntity>>

    /** 查询某类型某目标的历史记录（是否已浏览过）。 */
    @Query("SELECT * FROM browse_history WHERE targetType = :type AND targetId = :targetId LIMIT 1")
    suspend fun get(type: String, targetId: Long): BrowseHistoryEntity?

    /** 删除同类型同目标的旧记录（浏览详情前先删旧再插入，避免历史重复）。 */
    @Query("DELETE FROM browse_history WHERE targetType = :type AND targetId = :targetId")
    suspend fun deleteByTarget(type: String, targetId: Long)

    /** 插入/覆盖记录（配合 [deleteByTarget] 使用实现去重置顶）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BrowseHistoryEntity)

    /** 删除单条记录。 */
    @Delete
    suspend fun delete(entity: BrowseHistoryEntity)

    /** 清空全部浏览历史。 */
    @Query("DELETE FROM browse_history")
    suspend fun clearAll()
}
