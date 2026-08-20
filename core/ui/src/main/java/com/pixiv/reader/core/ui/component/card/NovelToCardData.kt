package com.pixiv.reader.core.ui.component.card

import com.pixiv.api.model.Novel

/**
 * Novel → [NovelCardData] 映射（core 共享）。
 *
 * 此前 8+ 处 feature 各自内联相同映射（加字段需多处同步），统一收口此处。
 * 标签取前 6 个、优先 translated_name（显示语言），空标签过滤。
 */
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
)
