package com.pixiv.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pixiv.reader.core.database.entity.PendingActionEntity
import kotlinx.coroutines.flow.Flow

/** 离线操作队列 DAO：观察 / 全量拉取 / 覆盖插入（family+targetId 唯一）/ 状态更新 / 删除。 */
@Dao
interface PendingActionDao {

    /** 观察全部待同步操作（按入队时间正序，管理页数据源）。 */
    @Query("SELECT * FROM pending_action ORDER BY updatedAt ASC")
    fun observeAll(): Flow<List<PendingActionEntity>>

    /** 观察待同步条数（Me 页角标用）。 */
    @Query("SELECT COUNT(*) FROM pending_action")
    fun observeCount(): Flow<Int>

    /** 拉取全部待补发操作（drain 泵逐条串行执行）。 */
    @Query("SELECT * FROM pending_action ORDER BY updatedAt ASC")
    suspend fun getAll(): List<PendingActionEntity>

    /** 插入 / 覆盖（family+targetId 唯一索引冲突时 REPLACE，同目标重复操作收敛为终态）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PendingActionEntity)

    /** 删除同家族同目标的队列记录（直连成功后清除过期队列项）。 */
    @Query("DELETE FROM pending_action WHERE family = :family AND targetId = :targetId")
    suspend fun deleteByTarget(family: String, targetId: Long)

    /** 补发成功后删除该条。 */
    @Delete
    suspend fun delete(entity: PendingActionEntity)

    /** 更新尝试次数与状态（补发失败时标记；重试时复位）。 */
    @Query("UPDATE pending_action SET attempts = :attempts, status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, attempts: Int, status: String, updatedAt: Long = System.currentTimeMillis())

    /** 清空全部待同步操作。 */
    @Query("DELETE FROM pending_action")
    suspend fun clearAll()
}
