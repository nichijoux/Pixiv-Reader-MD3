package com.pixiv.reader.core.ui.component.card

import com.pixiv.api.model.UserPreview

/**
 * UserPreview → [CreatorProfile] 映射（core 共享）。
 *
 * 此前用户关注列表（feature:user）与用户搜索结果（feature:discover）
 * 各内联相同映射，统一收口此处。
 */
fun UserPreview.toCreatorProfile(): CreatorProfile = CreatorProfile(
    id = user?.id ?: 0L,
    name = user?.name.orEmpty(),
    avatarUrl = user?.profile_image_urls?.best(),
    covers = illusts.take(3).mapNotNull {
        it.image_urls?.square_medium ?: it.image_urls?.medium
    },
    isFollowed = user?.is_followed == true,
)
