package com.pixiv.reader.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 下载状态轻量投影（供只关心状态/标题的订阅方使用，避开 `payloadJson` 等大列）。
 *
 * 字段取舍：`format` 参与完成通知的去重键（同一作品多格式各算一条任务）；
 * `title` 供完成/失败通知文案使用，避免再查一次完整实体。
 *
 * @property targetType 目标类型（illust / ugoira / novel）
 * @property targetId 目标 ID
 * @property format 导出格式（小说为 TXT/EPUB/…，插画等为空串）
 * @property status 状态列原始值（pending / downloading / done / failed）
 * @property title 标题（通知文案；可为 null，调用方以空串兜底）
 */
data class DownloadStatusRow(
    val targetType: String,
    val targetId: Long,
    val format: String,
    val status: String,
    val title: String?,
)

@Dao
interface DownloadEntryDao {

    /** 观察全部下载索引（按更新时间倒序，下载管理页数据源；含大列，仅列表展示用）。 */
    @Query("SELECT * FROM download_entry ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DownloadEntryEntity>>

    /** 观察全表状态投影（无大列；全局完成通知器做状态迁移快照对比用）。 */
    @Query("SELECT targetType, targetId, format, status, title FROM download_entry")
    fun observeAllStatuses(): Flow<List<DownloadStatusRow>>

    /**
     * 定向观察某类型某目标的全部条目状态（同目标多格式/多范围会返回多行）。
     *
     * @param targetType 目标类型（illust / ugoira / novel）
     * @param targetId 目标 ID
     * @return 该目标全部条目状态列原始值的流（无匹配时为空列表）
     */
    @Query("SELECT status FROM download_entry WHERE targetType = :targetType AND targetId = :targetId")
    fun observeStatuses(targetType: String, targetId: Long): Flow<List<String>>

    /** 查询所有已完成的下载（status='done'；主代码未用，仅测试 fake 覆写保持接口一致）。 */
    @Query("SELECT * FROM download_entry WHERE status = 'done'")
    suspend fun getDone(): List<DownloadEntryEntity>

    /** 查询某类型某目标某格式某范围的下载记录。 */
    @Query(
        "SELECT * FROM download_entry WHERE targetId = :targetId AND targetType = :type " +
            "AND format = :format AND scopeKey = :scopeKey LIMIT 1",
    )
    suspend fun get(type: String, targetId: Long, format: String = "", scopeKey: String = ""): DownloadEntryEntity?

    /** 插入/覆盖下载索引（复合主键 targetType+targetId+format+scopeKey 更新）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntryEntity)

    /** 更新单条下载进度（轻量 UPDATE，供分块下载节流写）。 */
    @Query(
        "UPDATE download_entry SET progress = :progress, updatedAt = :updatedAt " +
            "WHERE targetType = :type AND targetId = :targetId AND format = :format AND scopeKey = :scopeKey",
    )
    suspend fun updateProgress(type: String, targetId: Long, format: String, progress: Int, updatedAt: Long = System.currentTimeMillis(), scopeKey: String = "")

    /** 删除单条下载索引。 */
    @Delete
    suspend fun delete(entity: DownloadEntryEntity)
}
