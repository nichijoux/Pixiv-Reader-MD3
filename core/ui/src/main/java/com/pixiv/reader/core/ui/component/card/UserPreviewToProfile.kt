package com.pixiv.reader.core.ui.component.card

import com.pixiv.api.model.UserPreview

fun UserPreview.toCreatorProfile(): CreatorProfile = CreatorProfile(
    id = user?.id ?: 0L,
    name = user?.name.orEmpty(),
    avatarUrl = user?.profile_image_urls?.best(),
    covers = illusts.take(3).mapNotNull {
        it.image_urls?.square_medium ?: it.image_urls?.medium
    },
    isFollowed = user?.is_followed == true,
)
