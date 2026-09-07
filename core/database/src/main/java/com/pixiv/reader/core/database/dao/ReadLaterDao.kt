package com.pixiv.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pixiv.reader.core.database.entity.ReadLaterEntity
import kotlinx.coroutines.flow.Flow

/** 稍后再看 DAO：Flow 观察 + 先删后插去重 + 单条删除/清空（形态对齐 [BrowseHistoryDao]）。 */
@Dao
interface ReadLaterDao {

    /** 观察全量稍后再看（按加入时间倒序）。 */
    @Query("SELECT * FROM read_later ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<ReadLaterEntity>>

    /** 观察某类型（illust / novel）稍后再看（稍后再看页类型筛选用）。 */
    @Query("SELECT * FROM read_later WHERE targetType = :type ORDER BY addedAt DESC")
    fun observeByType(type: String): Flow<List<ReadLaterEntity>>

    /** 查询某类型某目标是否已加入（长按菜单动态文案用）。 */
    @Query("SELECT * FROM read_later WHERE targetType = :type AND targetId = :targetId LIMIT 1")
    suspend fun get(type: String, targetId: Long): ReadLaterEntity?

    /** 删除同类型同目标的旧记录（加入前先删旧再插入，防唯一索引冲突）。 */
    @Query("DELETE FROM read_later WHERE targetType = :type AND targetId = :targetId")
    suspend fun deleteByTarget(type: String, targetId: Long)

    /** 插入/覆盖记录（配合 [deleteByTarget] 使用实现去重置顶）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReadLaterEntity)

    /** 删除单条记录（稍后再看页长按移除 / 打开后移除）。 */
    @Delete
    suspend fun delete(entity: ReadLaterEntity)

    /** 清空全部稍后再看。 */
    @Query("DELETE FROM read_later")
    suspend fun clearAll()
}
