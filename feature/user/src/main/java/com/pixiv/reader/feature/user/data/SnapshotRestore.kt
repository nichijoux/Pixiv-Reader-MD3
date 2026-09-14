package com.pixiv.reader.feature.user.data

import com.google.gson.Gson
import com.pixiv.api.model.Illust
import com.pixiv.api.model.ImageUrls
import com.pixiv.reader.core.ui.component.card.NovelCardData

/**
 * 快照还原插画（历史 / 下载卡片快照）：payloadJson → [Illust]。
 * 优先解析完整 payloadJson（含宽高，避免固定高度裁剪中间）；解析失败回退 fallback 最小数据。
 * 快照为手写 org.json 字段（id/title/coverUrl/width/height/bookmarks/pageCount/isBookmarked），
 * 逐字段映射，不直接反序列化 Illust。
 *
 * @param payloadJson 快照 JSON（null / 解析失败走 fallback）
 * @param fallbackId 快照不可用时的条目 id
 * @param fallbackTitle 快照不可用 / 缺键时的标题
 * @param fallbackCoverUrl 快照不可用 / 缺键时的封面 URL
 * @param fallbackWidth 快照不可用时的宽度（历史记录无结构宽高，默认 0）
 * @param fallbackHeight 快照不可用时的高度（历史记录无结构宽高，默认 0）
 * @return 还原的插画数据（字段级缺键兜底，渲染安全）
 */
internal fun restoreIllust(
    payloadJson: String?,
    fallbackId: Long,
    fallbackTitle: String?,
    fallbackCoverUrl: String?,
    fallbackWidth: Int = 0,
    fallbackHeight: Int = 0,
): Illust {
    // 优先解析完整 payloadJson（含宽高，避免固定高度裁剪中间）；旧记录回退最小数据
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

/**
 * 快照还原插画（稍后再看完整快照）：payloadJson → [Illust]。
 * 直解完整 `Illust`（Gson 往返安全：字段全为可空/基本类型，缺失落 JVM 默认值）；
 * 解析失败 / id 异常（0）回退最小数据（标题 + 封面）。
 *
 * @param payloadJson 快照 JSON（完整 Illust 的 Gson 序列化）
 * @param gson Gson 实例（调用方持有/复用）
 * @param fallbackId 快照不可用时的条目 id
 * @param fallbackTitle 快照不可用时的标题
 * @param fallbackCoverUrl 快照不可用时的封面 URL
 * @return 还原的插画数据
 */
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
 * 快照还原小说卡：payloadJson（Gson 序列化的 [NovelCardData]）→ 逐字段重建。
 * Gson 对 Kotlin data class 用 UnsafeAllocator 绕过构造器：JSON 缺失的非空字段
 * 会被置为 null 且不抛异常——必须字段级补默认值，否则 NovelCard 渲染 NPE 闪退。
 * 解析失败（或 [requireValidId] 且快照 id 为 0 视为无效）回退 fallback 最小数据；
 * fallback 字段同时用于回填快照中缺失的可空字段（传 null 即不回填，保持快照原值）。
 *
 * @param payloadJson 快照 JSON（null / 解析失败走 fallback）
 * @param gson Gson 实例（调用方持有/复用）
 * @param fallbackId 快照不可用时的条目 id
 * @param fallbackTitle 快照不可用 / 缺键时的标题（调用方已兜底「未命名」文案）
 * @param fallbackCoverUrl 快照不可用 / 缺键时的封面 URL
 * @param fallbackAuthorName 快照缺键回填的作者名（null 不回填）
 * @param fallbackAuthorAvatarUrl 快照缺键回填的作者头像（null 不回填）
 * @param fallbackPublishDate 快照缺键回填的发布日期（null 不回填）
 * @param fallbackSeriesTitle 快照缺键回填的系列标题（null 不回填）
 * @param fallbackSeriesId 快照缺键回填的系列 id（null 不回填）
 * @param fallbackFavoriteCount 快照不可用时的收藏数
 * @param fallbackWordCount 快照不可用时的字数
 * @param requireValidId true 时快照 id 为 0 视为整体无效直接回退（稍后再看的 id!=0 守卫）
 * @return 还原的卡片数据（字段级缺键兜底，渲染安全）
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
    // 快照损坏（非法 JSON）时 runCatching 落 null → 走 fallback；成功但字段缺失由下方逐字段兜底
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
            favoriteCount = parsed.favoriteCount,
            wordCount = parsed.wordCount,
            tags = parsed.tags.orEmpty(),
            isFavorite = parsed.isFavorite,
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
