package com.pixiv.reader.feature.follow.data

import com.pixiv.api.model.Illust
import com.pixiv.api.model.Novel

/**
 * 右列滑动 Tab 的类型段。
 */
enum class FollowType {
    ALL,
    NOVEL,
    ILLUST,
}

/**
 * 插画（含漫画）与小说统一抽象。两个关注接口各自按时间倒序返回，
 * 客户端合并为单一时间线（官方无混合 feed 接口）。
 */
sealed interface FollowFeedItem {
    /** 发布时间（ISO 字符串，同格式可直接字典序比较）；null 表示未知。 */
    val createDate: String?

    /** 缺失记 0，与 [FollowFeedMerger.filter] 的按用户筛选约定一致。 */
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
 * 纯函数，JVM 单测覆盖。
 */
object FollowFeedMerger {

    /**
     * 按 createDate 倒序；null 排最后，同时间保持插画在前（稳定排序）。
     */
    fun merge(illusts: List<Illust>, novels: List<Novel>): List<FollowFeedItem> {
        val items = illusts.map(FollowFeedItem::IllustItem) +
                novels.map(FollowFeedItem::NovelItem)
        return items.sortedWith(compareByDescending { it.createDate ?: "" })
    }

    /**
     * userId 为 null 表示「全部」不过滤。
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
