package com.pixiv.reader.feature.follow.data

import com.pixiv.api.model.Illust
import com.pixiv.api.model.Novel

/**
 * 关注动态类型段（对应右列滑动 Tab：全部 / 小说 / 插画）。
 */
enum class FollowType {
    ALL,
    NOVEL,
    ILLUST,
}

/**
 * 混合动态流条目：插画（含漫画）与小说统一抽象。
 *
 * 两个关注流接口（v2/illust/follow 与 v1/novel/follow）各自按时间倒序返回，
 * 客户端合并为单一时间线展示（官方无混合 feed 接口）。
 */
sealed interface FollowFeedItem {
    /** 发布时间（ISO 字符串，同格式可直接字典序比较）；null 表示未知。 */
    val createDate: String?

    /** 发布者用户 ID（用于按用户筛选）；缺失为 0。 */
    val userId: Long

    data class IllustItem(val illust: Illust) : FollowFeedItem {
        override val createDate: String? get() = illust.create_date
        override val userId: Long get() = illust.user?.id ?: 0L
    }

    data class NovelItem(val novel: Novel) : FollowFeedItem {
        override val createDate: String? get() = novel.create_date
        override val userId: Long get() = novel.user?.id ?: 0L
    }
}

/**
 * 混合流合并 / 排序 / 过滤（纯函数，JVM 单测覆盖）。
 */
object FollowFeedMerger {

    /**
     * 合并插画流与小说流，按 [FollowFeedItem.createDate] 倒序（新→旧）。
     * createDate 为 null 的条目排最后；同时间保持插画在前（稳定排序）。
     */
    fun merge(illusts: List<Illust>, novels: List<Novel>): List<FollowFeedItem> {
        val items = illusts.map(FollowFeedItem::IllustItem) +
            novels.map(FollowFeedItem::NovelItem)
        return items.sortedWith(compareByDescending<FollowFeedItem> { it.createDate ?: "" })
    }

    /**
     * 按类型与用户过滤。
     *
     * @param type 类型段（ALL 不过滤类型）
     * @param userId 指定用户 ID；null 表示「全部」不过滤
     */
    fun filter(
        items: List<FollowFeedItem>,
        type: FollowType,
        userId: Long?,
    ): List<FollowFeedItem> = items.filter { item ->
        val typeOk = when (type) {
            FollowType.ALL -> true
            FollowType.NOVEL -> item is FollowFeedItem.NovelItem
            FollowType.ILLUST -> item is FollowFeedItem.IllustItem
        }
        typeOk && (userId == null || item.userId == userId)
    }
}
