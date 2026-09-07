package com.pixiv.api.model

import com.google.gson.annotations.SerializedName
import com.pixiv.api.Pageable
import java.io.Serializable

/**
 * FANBOX API 模型（api.fanbox.cc，响应统一为 `{"body": ...}` 包装，字段以实际抓包为准）。
 */

/** 帖子列表响应（post.listFollowing / post.listCreator） */
data class FanboxPostListResponse(
    @SerializedName("body") val body: FanboxPostListBody? = null,
) : Serializable, Pageable<FanboxPost> {
    override val items: List<FanboxPost> get() = body?.items ?: emptyList()
    override val nextPageUrl: String? get() = body?.nextUrl
}

/** 帖子列表体：items + nextUrl（下一页游标 = 传回 parentId 的字符串） */
data class FanboxPostListBody(
    @SerializedName("items") val items: List<FanboxPost>? = null,
    @SerializedName("nextUrl") val nextUrl: String? = null,
)

/** 帖子详情响应（post.info） */
data class FanboxPostDetailResponse(
    @SerializedName("body") val body: FanboxPost? = null,
)

/** 关注创作者列表响应（user.listFollowing） */
data class FanboxCreatorListResponse(
    @SerializedName("body") val body: FanboxCreatorListBody? = null,
) : Serializable, Pageable<FanboxCreator> {
    override val items: List<FanboxCreator> get() = body?.creators ?: emptyList()
    override val nextPageUrl: String? get() = body?.nextUrl
}

/** 关注创作者列表体 */
data class FanboxCreatorListBody(
    @SerializedName("creators") val creators: List<FanboxCreator>? = null,
    @SerializedName("nextUrl") val nextUrl: String? = null,
)

/** 创作者条目（列表项聚合创作者信息） */
data class FanboxCreator(
    @SerializedName("creator") val creator: FanboxUser? = null,
)

/** FANBOX 用户（创作者） */
data class FanboxUser(
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("profileImageUrl") val profileImageUrl: String? = null,
)

/**
 * FANBOX 帖子（列表形态含摘要；post.info 详情形态带 [body]）。
 *
 * @param feeRequired 阅读所需计划价格（0 = 免费）
 * @param isAccessible 当前账号是否可访问正文（付费墙判定）
 */
data class FanboxPost(
    @SerializedName("id") val id: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("creatorId") val creatorId: String? = null,
    @SerializedName("user") val user: FanboxUser? = null,
    @SerializedName("coverImageUrl") val coverImageUrl: String? = null,
    @SerializedName("excerpt") val excerpt: String? = null,
    @SerializedName("publishedDateTime") val publishedDateTime: String? = null,
    @SerializedName("feeRequired") val feeRequired: Int = 0,
    @SerializedName("isAccessible") val isAccessible: Boolean = false,
    @SerializedName("isLiked") val isLiked: Boolean = false,
    @SerializedName("likeCount") val likeCount: Int = 0,
    @SerializedName("commentCount") val commentCount: Int = 0,
    @SerializedName("body") val body: FanboxPostBody? = null,
    @SerializedName("body_text") val bodyText: String? = null,
    @SerializedName("tags") val tags: List<String>? = null,
)

/** 帖子正文（类型化：纯文本 / 图片 / 文章 blocks；按非空字段分流渲染） */
data class FanboxPostBody(
    @SerializedName("text") val text: String? = null,
    @SerializedName("blocks") val blocks: List<FanboxBlock>? = null,
    @SerializedName("imageMap") val imageMap: Map<String, FanboxImage>? = null,
    @SerializedName("imageOrder") val imageOrder: List<String>? = null,
)

/** 文章型帖子内容块（type = p / header / image / file …） */
data class FanboxBlock(
    @SerializedName("type") val type: String? = null,
    @SerializedName("text") val text: String? = null,
    @SerializedName("imageId") val imageId: String? = null,
)

/** FANBOX 图片资源 */
data class FanboxImage(
    @SerializedName("originalUrl") val originalUrl: String? = null,
    @SerializedName("thumbnailUrl") val thumbnailUrl: String? = null,
    @SerializedName("width") val width: Int = 0,
    @SerializedName("height") val height: Int = 0,
)
