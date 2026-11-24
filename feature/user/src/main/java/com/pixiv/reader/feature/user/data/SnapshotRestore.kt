package com.pixiv.reader.feature.user.data

import com.google.gson.Gson
import com.pixiv.api.model.Illust
import com.pixiv.api.model.ImageUrls
import com.pixiv.reader.core.ui.component.card.NovelCardData

/**
 * 快照是手写的 org.json 扁平字段，须逐字段映射（不能直接反序列化 Illust）：
 * 缺宽高会让卡片固定高度裁剪中间，故解析失败一律回退 fallback 最小数据。
 */
internal fun restoreIllust(
    payloadJson: String?,
    fallbackId: Long,
    fallbackTitle: String?,
    fallbackCoverUrl: String?,
    fallbackWidth: Int = 0,
    fallbackHeight: Int = 0,
): Illust {
    val parsed = payloadJson?.let {
        runCatching { org.json.JSONObject(it) }.getOrNull()
    }
    if (parsed != null) {
        return Illust(
            id = parsed.optLong("id", fallbackId),
            title = parsed.optString("title").ifEmpty { fallbackTitle.orEmpty() },
            image_urls = ImageUrls(medium = parsed.optString("coverUrl").ifEmpty { fallbackCoverUrl.orEmpty() }),
            width = parsed.optInt("width") ?: 0,
            height = parsed.optInt("height") ?: 0,
            total_bookmarks = parsed.optInt("bookmarks").takeIf { it != 0 },
            page_count = parsed.optInt("pageCount") ?: 0,
            is_bookmarked = if (parsed.has("isBookmarked")) parsed.optBoolean("isBookmarked") else null,
        )
    }
    return Illust(
        id = fallbackId,
        title = fallbackTitle,
        image_urls = ImageUrls(medium = fallbackCoverUrl),
        width = fallbackWidth,
        height = fallbackHeight,
    )
}

/** 稍后再看存的是完整 Illust 的 Gson 快照（字段全可空/基本类型，缺失落 JVM 默认值），可直接反序列化。 */
internal fun restoreIllust(
    payloadJson: String?,
    gson: Gson,
    fallbackId: Long,
    fallbackTitle: String?,
    fallbackCoverUrl: String?,
): Illust {
    val parsed = payloadJson?.let {
        runCatching { gson.fromJson(it, Illust::class.java) }.getOrNull()
    }
    if (parsed != null && parsed.id != 0L) return parsed
    return Illust(id = fallbackId, title = fallbackTitle, image_urls = ImageUrls(medium = fallbackCoverUrl))
}

/**
 * Gson 用 UnsafeAllocator 绕过 Kotlin 构造器：JSON 缺失的非空字段会被置 null 且不抛异常，
 * 因此必须字段级补默认值，否则 NovelCard 渲染 NPE 闪退。fallback 里可空参数传 null 表示不回填。
 */
internal fun restoreNovelCardData(
    payloadJson: String?,
    gson: Gson,
    fallbackId: Long,
    fallbackTitle: String?,
    fallbackCoverUrl: String?,
    fallbackAuthorName: String? = null,
    fallbackAuthorAvatarUrl: String? = null,
    fallbackPublishDate: String? = null,
    fallbackSeriesTitle: String? = null,
    fallbackSeriesId: Long? = null,
    fallbackFavoriteCount: Int = 0,
    fallbackWordCount: Int = 0,
    requireValidId: Boolean = false,
): NovelCardData {
    val parsed = payloadJson?.let {
        runCatching { gson.fromJson(it, NovelCardData::class.java) }.getOrNull()
    }
    if (parsed != null && (!requireValidId || parsed.id != 0L)) {
        return NovelCardData(
            id = if (parsed.id != 0L) parsed.id else fallbackId,
            title = parsed.title?.takeIf { it.isNotBlank() } ?: fallbackTitle.orEmpty(),
            coverUrl = parsed.coverUrl ?: fallbackCoverUrl,
            authorId = parsed.authorId,
            authorName = parsed.authorName ?: fallbackAuthorName ?: "",
            authorAvatarUrl = parsed.authorAvatarUrl ?: fallbackAuthorAvatarUrl,
            publishDate = parsed.publishDate ?: fallbackPublishDate,
            seriesTitle = parsed.seriesTitle ?: fallbackSeriesTitle,
            seriesId = parsed.seriesId ?: fallbackSeriesId,
            // 旧快照缺失整数字段时 Gson 填 0，仅大于 0 才覆盖历史实体兜底计数。
            favoriteCount = parsed.favoriteCount.takeIf { it > 0 } ?: fallbackFavoriteCount,
            wordCount = parsed.wordCount.takeIf { it > 0 } ?: fallbackWordCount,
            tags = parsed.tags.orEmpty(),
            isFavorite = parsed.isFavorite,
            serverMuted = parsed.serverMuted,
        )
    }
    return NovelCardData(
        id = fallbackId,
        title = fallbackTitle.orEmpty(),
        coverUrl = fallbackCoverUrl,
        authorId = 0,
        authorName = fallbackAuthorName ?: "",
        authorAvatarUrl = fallbackAuthorAvatarUrl,
        publishDate = fallbackPublishDate,
        seriesTitle = fallbackSeriesTitle,
        seriesId = fallbackSeriesId,
        favoriteCount = fallbackFavoriteCount,
        wordCount = fallbackWordCount,
    )
}
