package com.pixiv.api.model

import com.pixiv.api.Pageable
import com.google.gson.annotations.SerializedName

/**
 * FANBOX（api.fanbox.cc）数据模型，字段对齐服务端 JSON（参照 Pixiv-Shaft classic 分支
 * `ceui/pixiv/api/FanboxApi.kt` 的模型定义）。
 *
 * 约定（防 Gson UnsafeAllocator 对缺失键给 null 打穿 Kotlin 非空类型）：
 * - 字符串 / 集合 / 对象字段一律可空（默认 null），使用处 `.orEmpty()` 兜底；
 * - 基础类型（Int / Long / Boolean）保持非空——JVM 原始类型缺失时是 0 / false，不会 NPE。
 */
/** post.get / post.info 响应外壳（`body.post` 为帖子对象，两者同构）。 */
data class FanboxPostDetailResponse(
    val body: FanboxPostWrapper? = null,
)

/** [FanboxPostDetailResponse.body] 的外壳。 */
data class FanboxPostWrapper(
    val post: FanboxPost? = null,
)

/** post.listHome 响应外壳。 */
data class FanboxPostListResponse(
    val body: FanboxPostList? = null,
)

/**
 * 投稿列表（post.listHome）。实现 [Pageable] 以配合 core:network `PagedState` 游标分页：
 * 服务端在 `nextUrl` 里给的是 api.fanbox.cc 绝对 URL，直接交给 `@Url` 翻页方法。
 */
data class FanboxPostList(
    @SerializedName("items") private val posts: List<FanboxPost>? = null,
    @SerializedName("nextUrl") private val url: String? = null,
) : Pageable<FanboxPost> {
    override val items: List<FanboxPost> get() = posts.orEmpty()
    override val nextPageUrl: String? get() = url
}

/** creator.listRecommended 响应外壳。 */
data class FanboxCreatorListResponse(
    val body: FanboxCreatorList? = null,
)

/** 推荐创作者列表——响应里没有翻页游标，就是单页。 */
data class FanboxCreatorList(
    val creators: List<FanboxCreator>? = null,
)

/** post.getComments 响应外壳。 */
data class FanboxCommentResponse(
    val body: FanboxCommentBody? = null,
)

/** 评论响应体（viewMode 服务端给 `tree` / `flat` 两种，这里只按内嵌 replies 渲染）。 */
data class FanboxCommentBody(
    val viewMode: String? = null,
    val commentList: FanboxCommentList? = null,
)

/** 评论列表（含 nextUrl，但详情页场景单次取一页即可）。 */
data class FanboxCommentList(
    val items: List<FanboxComment>? = null,
    val nextUrl: String? = null,
)

/** 评论条目：[replies] 是楼中楼，服务端已内嵌，不需要再请求。 */
data class FanboxComment(
    val id: String = "",
    val body: String? = null,
    val createdDatetime: String? = null,
    val likeCount: Int = 0,
    val isLiked: Boolean = false,
    val user: FanboxUser? = null,
    val replies: List<FanboxComment>? = null,
)

/** plan.listCreator 响应外壳。 */
data class FanboxPlanListResponse(
    val body: FanboxPlanList? = null,
)

/** 赞助方案列表（网页版付费墙「方案列表」背后的数据）。 */
data class FanboxPlanList(
    val plans: List<FanboxPlan>? = null,
)

/** 一档赞助方案。 */
data class FanboxPlan(
    val id: String = "",
    val title: String? = null,
    val fee: Int = 0,
    val description: String? = null,
    val coverImageUrl: String? = null,
    val creatorId: String? = null,
    val hasAdultContent: Boolean = false,
)

/**
 * 一条 FANBOX 投稿。
 *
 * 列表接口（post.listHome）与元数据兜底接口（post.get）**都不返回正文**（[type]/[body]
 * 为 null），付费墙后的帖子（[isRestricted] 为 true）服务端只给封面和标题；[type] / [body]
 * / [coverImageUrl] 只有经 WebView 打 post.info 才有。
 */
data class FanboxPost(
    val id: String = "",
    val title: String? = null,
    val feeRequired: Int = 0,
    val publishedDatetime: String? = null,
    val tags: List<String>? = null,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val isRestricted: Boolean = false,
    val user: FanboxUser? = null,
    val creatorId: String? = null,
    val hasAdultContent: Boolean = false,
    val cover: FanboxCover? = null,
    val excerpt: String? = null,
    val type: String? = null,
    val body: FanboxPostBody? = null,
    val coverImageUrl: String? = null,
) {
    /** 封面：列表接口给结构化 [cover]，post.info 给平铺的 [coverImageUrl]，取值统一走这里。 */
    val coverUrl: String
        get() = cover?.url?.takeIf { it.isNotEmpty() } ?: coverImageUrl.orEmpty()
}

/**
 * 正文。字段按 [FanboxPost.type] 分家：
 * - `article`：[blocks] + 四张 map（块里只存 id，资源去 map 里查）；
 * - `image`：[text] + [images]；`file`：[text] + [files]；`text`：只有 [text]；
 * - `video`：[text] + 外链视频（原生不播，仅展示文字与占位）；
 * - `entry`：[html] 整段富文本。
 *
 * 受限帖（未赞助）服务端整块给 null——有 post 无 body 是正常态，不是解析失败。
 */
data class FanboxPostBody(
    val text: String? = null,
    val html: String? = null,
    val images: List<FanboxImage>? = null,
    val files: List<FanboxFile>? = null,
    val blocks: List<FanboxBlock>? = null,
    val imageMap: Map<String, FanboxImage>? = null,
    val fileMap: Map<String, FanboxFile>? = null,
    val embedMap: Map<String, FanboxEmbed>? = null,
    val urlEmbedMap: Map<String, FanboxUrlEmbed>? = null,
)

/**
 * 正文块（article 类型）。[type] 取值 `p` / `header` / `image` / `file` / `embed` /
 * `url_embed`，每种只填自己那一个资源 id 字段。
 */
data class FanboxBlock(
    val type: String? = null,
    val text: String? = null,
    val imageId: String? = null,
    val fileId: String? = null,
    val embedId: String? = null,
    val urlEmbedId: String? = null,
    val links: List<FanboxBlockLink>? = null,
    val styles: List<FanboxBlockStyle>? = null,
)

/** 段落内的一段字符装饰（服务端目前只发 `bold` 一种）。 */
data class FanboxBlockStyle(
    val type: String? = null,
    val offset: Int = 0,
    val length: Int = 0,
)

/** 段落内的一段超链接（offset / length 为 UTF-16 码元下标）。 */
data class FanboxBlockLink(
    val offset: Int = 0,
    val length: Int = 0,
    val url: String? = null,
)

/** 正文图片资源。 */
data class FanboxImage(
    val id: String? = null,
    val extension: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val originalUrl: String? = null,
    val thumbnailUrl: String? = null,
)

/** 正文附件资源。 */
data class FanboxFile(
    val id: String? = null,
    val name: String? = null,
    val extension: String? = null,
    val size: Long = 0,
    val url: String? = null,
)

/** 旧式站外嵌入（twitter / youtube 等），只给服务商和内容 id，链接要自己拼。 */
data class FanboxEmbed(
    val id: String? = null,
    val serviceProvider: String? = null,
    val contentId: String? = null,
)

/**
 * 新式嵌入。[type] 为 `default` 时才有 [url]；`fanbox.post` / `fanbox.creator` 指向站内，
 * `html` / `html.card` 是一段 HTML——后几种统一按「打不开的卡片」占位处理。
 */
data class FanboxUrlEmbed(
    val id: String? = null,
    val type: String? = null,
    val url: String? = null,
    val host: String? = null,
)

/** FANBOX 用户（帖子作者 / 评论者共用的轻量档案）。 */
data class FanboxUser(
    val userId: String? = null,
    val name: String? = null,
    val iconUrl: String? = null,
)

/** 列表接口的结构化封面。 */
data class FanboxCover(
    val type: String? = null,
    val url: String? = null,
)

/** 推荐位上的创作者（profileItems 是主页展示图；category 服务端经常给 null，勿当必有字段）。 */
data class FanboxCreator(
    val user: FanboxUser? = null,
    val creatorId: String? = null,
    val description: String? = null,
    val hasAdultContent: Boolean = false,
    val coverImageUrl: String? = null,
    val profileItems: List<FanboxProfileItem>? = null,
    val isFollowed: Boolean = false,
    val isSupported: Boolean = false,
    val hasPublishedPost: Boolean = false,
    val category: String? = null,
)

/** 创作者主页展示图。 */
data class FanboxProfileItem(
    val id: String? = null,
    val type: String? = null,
    val imageUrl: String? = null,
    val thumbnailUrl: String? = null,
)
