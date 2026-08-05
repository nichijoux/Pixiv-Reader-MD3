package com.pixiv.api.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * 网页 API（www.pixiv.net ajax）数据模型
 * 来源：Pixiv-Shaft `ceui/loxia/SquareResponse.kt` / `StreetResponse.kt` / `PixivWebApi.kt`
 */

/** 网页接口统一响应包装 */
data class WebResponse<T>(
    val error: Boolean? = null,
    val message: String? = null,
    val body: T? = null,
) : Serializable

/** 网页作品（ajax 版精简结构） */
data class WebIllust(
    val id: Long = 0L,
    val title: String? = null,
    val alt: String? = null,
    val description: String? = null,
    val createDate: String? = null,
    val updateDate: String? = null,
    val illustType: Int? = null,
    val pageCount: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val url: String? = null,
    val url_s: String? = null,
    val url_sm: String? = null,
    val url_w: String? = null,
    val urls: Map<String, String?>? = null,
    val images: Any? = null,
    val userId: Long = 0L,
    val userName: String? = null,
    val profileImageUrl: String? = null,
    val restrict: Int? = null,
    val xRestrict: Int? = null,
    val sl: Int? = null,
    val aiType: Int = 0,
    val isBookmarkable: Boolean? = null,
    val isUnlisted: Boolean? = null,
    val isMasked: Boolean? = null,
    val bookmarkData: Any? = null,
) : Serializable

/** 网页用户详情 */
data class WebUserDetail(
    val userId: String? = null,
    val name: String? = null,
    val image: String? = null,
    val imageBig: String? = null,
    val premium: Boolean? = null,
    val isFollowed: Boolean? = null,
    val isMypixiv: Boolean? = null,
    val isBlocking: Boolean? = null,
    val followedBack: Boolean? = null,
    val canSendMessage: Boolean? = null,
    val background: WebUserBackground? = null,
    val following: Int? = null,
    val mypixivCount: Int? = null,
    val comment: String? = null,
    val webpage: String? = null,
    val social: Map<String, WebSocialLink>? = null,
    val official: Boolean? = null,
    val publisher: Boolean? = null,
) : Serializable

data class WebUserBackground(
    val url: String? = null,
    val isPrivate: Boolean? = null,
) : Serializable

data class WebSocialLink(
    val url: String? = null,
) : Serializable

/** 每 P 真实原图宽高（/ajax/illust/{id}/pages） */
data class WebIllustPage(
    val width: Int = 0,
    val height: Int = 0,
) : Serializable

// ── 首页方块（Square）────────────────────────────────────────────────────────

data class SquareResponse(
    val error: Boolean? = null,
    val message: String? = null,
    val body: Square? = null,
) : Serializable

data class Square(
    val tagTranslation: Map<String, TranslatedTags>? = null,
    val page: SquarePage? = null,
    val total: Int? = null,
    val thumbnails: SquareThumbnails? = null,
) : Serializable

data class TranslatedTags(
    val en: String? = null,
    val ko: String? = null,
    val zh: String? = null,
    val zh_tw: String? = null,
    val romaji: String? = null,
) : Serializable

data class SquarePage(
    val recommendByTag: List<WebTag>? = null,
    val trendingTags: List<WebTag>? = null,
    val tags: List<WebTag>? = null,
    val follow: List<Long>? = null,
    val recommend: IdsHolder? = null,
    val ranking: RankingHolder? = null,
    val editorRecommend: List<EditorRecommend>? = null,
) : Serializable

data class WebTag(
    val tag: String? = null,
    val tag_translation: String? = null,
    val cnt: Int? = null,
    val ids: List<Long>? = null,
) : Serializable {
    val displayName: String? get() = tag ?: tag_translation
}

data class EditorRecommend(
    val illustId: Long? = null,
    val comment: String? = null,
) : Serializable

data class IdsHolder(
    val ids: List<Long>? = null,
) : Serializable

data class SquareThumbnails(
    val illust: List<WebIllust>? = null,
) : Serializable

data class RankingHolder(
    val date: String? = null,
    val items: List<RankingItem>? = null,
) : Serializable

data class RankingItem(
    val rank: Int = 0,
    val id: Long = 0L,
) : Serializable

/** 搜索（圈子）响应 */
data class CircleResponse(
    val error: Boolean? = null,
    val message: String? = null,
    val body: Circle? = null,
) : Serializable

data class Circle(
    val illusts: List<WebIllust>? = null,
    val total: Int = 0,
    val lastPage: Int = 0,
    val meta: CircleMeta? = null,
) : Serializable

data class CircleMeta(
    val tag: String? = null,
    val translatedTag: String? = null,
    val pixpedia: Pixpedia? = null,
    val words: List<String>? = null,
    val relatedTags: List<WebTag>? = null,
) : Serializable

data class Pixpedia(
    val tag: String? = null,
    val abstract: String? = null,
    val illust: WebIllust? = null,
    val parent_tag: String? = null,
    val siblings_tags: List<String>? = null,
    val children_tags: List<String>? = null,
    val breadcrumbs: List<String>? = null,
) : Serializable

// ── Street（街拍式发现流）────────────────────────────────────────────────────

data class StreetResponse(
    val error: Boolean? = null,
    val message: String? = null,
    val body: StreetBody? = null,
) : Serializable

data class StreetBody(
    val contents: List<StreetContent>? = null,
    val nextParams: StreetNextParams? = null,
) : Serializable

data class StreetContent(
    val kind: String? = null,
    val thumbnails: List<StreetThumbnail>? = null,
    val pickup: StreetPickup? = null,
    val trendTags: List<StreetTrendTag>? = null,
    val id: String? = null,
) : Serializable

data class StreetThumbnail(
    val type: String? = null,
    val id: String? = null,
    val title: String? = null,
    val tags: List<StreetTag>? = null,
    val restrict: Int? = null,
    val xRestrict: Int? = null,
    val userId: String? = null,
    val userName: String? = null,
    val profileImageUrl: String? = null,
    val createDate: String? = null,
    val updateDate: String? = null,
    val aiType: Int? = null,
    val bookmarkable: Boolean? = null,
    val pageCount: Int? = null,
    val pages: List<StreetPage>? = null,
    val episodeCount: Int? = null,
    val url: String? = null,
    val description: String? = null,
    val text: String? = null,
    val wordCount: Int? = null,
    val bookmarkCount: Int? = null,
    val isOriginal: Boolean? = null,
) : Serializable

data class StreetTag(
    val name: String? = null,
    val translatedName: String? = null,
) : Serializable

data class StreetPage(
    val width: Int? = null,
    val height: Int? = null,
    val urls: StreetPageUrls? = null,
) : Serializable

data class StreetPageUrls(
    @SerializedName("1200x1200_standard") val standard: String? = null,
    @SerializedName("540x540") val medium: String? = null,
    @SerializedName("360x360") val small: String? = null,
) : Serializable {
    val best: String? get() = standard ?: medium ?: small
}

data class StreetPickup(
    val type: String? = null,
    val userId: String? = null,
    val userName: String? = null,
    val profileImageUrl: String? = null,
    val comment: String? = null,
    val commentCount: Int? = null,
) : Serializable

data class StreetTrendTag(
    val name: String? = null,
    val translatedName: String? = null,
    val taggedCount: Int? = null,
    val url: String? = null,
) : Serializable

data class StreetNextParams(
    val page: Int? = null,
    val content_index_prev: Int? = null,
    val li: String? = null,
    val lm: String? = null,
    val ln: String? = null,
    val lc: String? = null,
) : Serializable

data class StreetRequest(
    val k: String? = null,
    val vhi: String? = null,
    val vhm: String? = null,
    val vhn: String? = null,
    val vhc: String? = null,
) : Serializable

// ── 拉黑 ────────────────────────────────────────────────────────────────────

data class BlockListBody(
    val block_items: List<BlockItem>? = null,
    val has_more_blocks: Boolean = false,
) : Serializable

data class BlockItem(
    val userId: String? = null,
    val label: String? = null,
    val isBlocked: Boolean = false,
    val isTarget: Boolean = false,
) : Serializable

/** /ajax/block/save 请求体，action 只接受 block / unblock */
data class BlockSaveRequest(
    val user_id: String,
    val action: String,
) : Serializable

// ── 按 Tag 筛选画师作品 ─────────────────────────────────────────────────────

data class UserTagIllustBody(
    val works: List<UserTagIllust>? = null,
    val total: Int = 0,
) : Serializable

data class UserTagIllust(
    val id: Long = 0L,
    val title: String? = null,
    val illustType: Int = 0,
    val xRestrict: Int = 0,
    val aiType: Int = 0,
    val url: String? = null,
    val tags: List<String>? = null,
    val userId: Long = 0L,
    val userName: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val pageCount: Int = 0,
    val createDate: String? = null,
    val profileImageUrl: String? = null,
) : Serializable

// ── 网页小说详情（/ajax/novel/{id}） ──────────────────────────────────────────

/** 网页小说详情（正文 + 嵌入图片映射）。 */
data class WebNovel(
    val id: Long = 0L,
    val title: String? = null,
    val userId: Long = 0L,
    val userName: String? = null,
    val content: String? = null,
    val coverUrl: String? = null,
    val xRestrict: Int = 0,
    /** 正文嵌入图片：key 为正文标记内容（如 `01.png`），value 为图片信息 */
    val textEmbeddedImages: Map<String, WebEmbeddedImage?>? = null,
    val characterCount: Int = 0,
    val wordCount: Int = 0,
    val readingTime: Int = 0,
) : Serializable

/** 正文嵌入图片信息（key 为 novelImageId，如 `21921763`）。 */
data class WebEmbeddedImage(
    val novelImageId: String? = null,
    val sl: String? = null,
    /** 各尺寸 URL：240mw / 480mw / 1200x1200 / 128x128 / original */
    val urls: Map<String, String?>? = null,
    /** 部分响应直接给 url 字段（兼容） */
    val url: String? = null,
    val width: Int = 0,
    val height: Int = 0,
) : Serializable
