package com.pixiv.reader.core.common.model

import com.pixiv.api.model.Novel

/** 字段名与历史/下载 payloadJson 的 Gson 序列化绑定，勿改名。 */
data class NovelCardData(
    val id: Long,
    val title: String,
    val coverUrl: String?,
    val authorId: Long,
    val authorName: String,
    val authorAvatarUrl: String?,
    val publishDate: String?,
    val seriesTitle: String?,
    val seriesId: Long? = null,
    val favoriteCount: Int,
    val wordCount: Int,
    val tags: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val serverMuted: Boolean = false,
)

fun Novel.toCardData(): NovelCardData = NovelCardData(
    id = id,
    title = title.orEmpty(),
    coverUrl = image_urls?.square_medium ?: image_urls?.medium,
    authorId = user?.id ?: 0L,
    authorName = user?.name.orEmpty(),
    authorAvatarUrl = user?.profile_image_urls?.best(),
    publishDate = create_date,
    seriesTitle = series?.title,
    seriesId = series?.id,
    favoriteCount = total_bookmarks ?: 0,
    wordCount = text_length ?: 0,
    tags = tags.orEmpty()
        .take(6)
        .map { it.translated_name ?: it.name ?: "" }
        .filter { it.isNotBlank() },
    isFavorite = is_bookmarked == true,
    serverMuted = is_muted == true,
)
