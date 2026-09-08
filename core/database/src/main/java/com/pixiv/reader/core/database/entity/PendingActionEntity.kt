package com.pixiv.reader.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 离线操作队列（断网时的收藏 / 关注 / 追更暂存，联网后自动补发）。
 *
 * `family`+`targetId` 唯一索引：同一目标的重复操作直接覆盖（REPLACE）——
 * 断网期间连点「收藏→取消」自动收敛为终态，不堆积重复任务。
 * `paramsJson` 携带操作参数（restrict / tags），补发时还原完整语义；
 * `payloadJson` 为可选展示快照（标题 / 封面，供待同步列表页展示）。
 */
@Entity(
    tableName = "pending_action",
    indices = [Index(value = ["family", "targetId"], unique = true)],
)
data class PendingActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** 操作家族（见 [FAMILY_ILLUST_BOOKMARK] 等常量），决定补发时调用的 API。 */
    val family: String,
    /** 目标 id（插画 / 小说 / 用户 / 系列 id）。 */
    val targetId: Long,
    /** 目标状态：true=加入（收藏 / 关注 / 追更），false=移除。 */
    val targetState: Boolean,
    /** 操作参数 JSON（如 {"restrict":"public","tags":["原始"]}；无参数为 null）。 */
    val paramsJson: String? = null,
    /** 展示快照 JSON（标题 / 封面等，可选；待同步列表页展示用）。 */
    val payloadJson: String? = null,
    /** 补发尝试次数（网络类失败不计，客户端类错误 +1）。 */
    val attempts: Int = 0,
    /** 队列状态：pending=等待补发，failed=补发失败（保留待用户重试 / 删除）。 */
    val status: String = STATUS_PENDING,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        /** 收藏 / 取消收藏插画。 */
        const val FAMILY_ILLUST_BOOKMARK = "illust_bookmark"

        /** 收藏 / 取消收藏小说。 */
        const val FAMILY_NOVEL_BOOKMARK = "novel_bookmark"

        /** 关注 / 取关用户。 */
        const val FAMILY_FOLLOW_USER = "follow_user"

        /** 追更 / 取消追更漫画系列。 */
        const val FAMILY_MANGA_WATCHLIST = "manga_watchlist"

        /** 追更 / 取消追更小说系列。 */
        const val FAMILY_NOVEL_WATCHLIST = "novel_watchlist"

        /** 等待补发。 */
        const val STATUS_PENDING = "pending"

        /** 补发失败（服务端明确拒绝；保留待用户处理）。 */
        const val STATUS_FAILED = "failed"
    }
}
