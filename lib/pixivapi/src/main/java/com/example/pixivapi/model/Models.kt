package com.example.pixivapi.model

import com.example.pixivapi.Pageable
import com.google.gson.annotations.SerializedName
import java.io.Serializable

/**
 * Pixiv App API 数据模型（DTO）
 *
 * 字段名与官方 JSON 完全一致（snake_case），配合 Gson/Moshi 无需额外注解。
 * 来源：Pixiv-Shaft `ceui/loxia/Models.kt` + `models/src/main/java/ceui/lisa/models`（`*.java`）
 */

// ── 通用 ─────────────────────────────────────────────────────────────────────

/** 空响应（写操作通用） */
class NullResponse : Serializable

/** 错误响应 */
data class ErrorResponse(
    val error: ErrorBody? = null,
) : Serializable

data class ErrorBody(
    val message: String? = null,
    val reason: String? = null,
    val user_message: String? = null,
    val user_message_details: Any? = null,
) : Serializable {
    fun displayMessage(): String? =
        listOfNotNull(message, reason, user_message).firstOrNull { it.isNotBlank() }
}

// ── OAuth / 账户 ────────────────────────────────────────────────────────────

/** OAuth token 响应 */
data class AccountResponse(
    val access_token: String? = null,
    val expires_in: Int? = null,
    val refresh_token: String? = null,
    val scope: String? = null,
    val token_type: String? = null,
    val user: User? = null,
) : Serializable

// ── 用户 ─────────────────────────────────────────────────────────────────────

data class User(
    val id: Long = 0L,
    val name: String? = null,
    val account: String? = null,
    val profile_image_urls: ImageUrls? = null,
    val is_followed: Boolean? = null,
    val is_premium: Boolean? = null,
    val is_mail_authorized: Boolean? = null,
    val mail_address: String? = null,
    val gender: Int = 0,
    val comment: String? = null,
    val require_policy_agreement: Boolean? = null,
    val x_restrict: Int? = null,
    /** user/detail v2 新增 */
    val is_access_blocking_user: Boolean? = null,
    val is_accept_request: Boolean? = null,
) : Serializable

/** 用户详情（/v2/user/detail） */
data class UserResponse(
    val profile: Profile? = null,
    val profile_publicity: ProfilePublicity? = null,
    val user: User? = null,
    val workspace: Workspace? = null,
    val disabled_links: List<String>? = null,
) : Serializable

data class Profile(
    val total_illusts: Int = 0,
    val total_manga: Int = 0,
    val total_novels: Int? = null,
    val total_illust_series: Int? = null,
    val total_novel_series: Int? = null,
    val total_bookmarks_public: Int = 0,
    val total_follow_users: Int? = null,
    val total_mypixiv_users: Int? = null,
    val is_premium: Boolean? = null,
    val is_using_custom_profile_image: Boolean? = null,
    val background_image_url: String? = null,
    val twitter_account: String? = null,
    val twitter_url: String? = null,
    val webpage: Any? = null,
    val birth: String? = null,
    val birth_day: String? = null,
    val birth_year: Int? = null,
    val region: String? = null,
    val gender: String? = null,
    val job: String? = null,
    val job_id: Int? = null,
    val country_code: String? = null,
    val pawoo_url: Any? = null,
    val address_id: Int? = null,
    val badge: Badge? = null,
) : Serializable

data class Badge(
    val type: String? = null,
    val url: String? = null,
) : Serializable

data class ProfilePublicity(
    val gender: String? = null,
    val region: String? = null,
    val birth_day: String? = null,
    val birth_year: String? = null,
    val job: String? = null,
    val pawoo: Boolean? = null,
) : Serializable

data class Workspace(
    val pc: String? = null,
    val monitor: String? = null,
    val mouse: String? = null,
    val keyboard: String? = null,
    val printer: String? = null,
    val scanner: String? = null,
    val tablet: String? = null,
    val mousepad: String? = null,
    val chair: String? = null,
    val desk: String? = null,
    val tool: String? = null,
    val music: String? = null,
    val desktop: String? = null,
    val comment: String? = null,
    val workspace_image_url: Any? = null,
) : Serializable

/** /v1/user/me/state */
data class SelfProfile(
    val profile: User? = null,
    val user_state: UserState? = null,
) : Serializable

data class UserState(
    val is_mail_authorized: Boolean = false,
    val has_mail_address: Boolean = false,
    val has_changed_pixiv_id: Boolean = false,
    val can_change_pixiv_id: Boolean = false,
    val has_password: Boolean = false,
    val require_policy_agreement: Boolean = false,
    val no_login_method: Boolean = false,
    val is_user_restricted: Boolean = false,
) : Serializable

/** 推荐用户列表项：user + 代表作预览 */
data class UserPreview(
    val user: User? = null,
    val illusts: List<Illust> = emptyList(),
    val novels: List<Any>? = null,
    val is_muted: Boolean? = null,
) : Serializable

data class UserPreviewResponse(
    val user_previews: List<UserPreview> = emptyList(),
    val next_url: String? = null,
) : Serializable, Pageable<UserPreview> {
    override val items: List<UserPreview> get() = user_previews
    override val nextPageUrl: String? get() = next_url
}

data class UserFollowDetail(
    val followed: Boolean = false,
) : Serializable

/** 屏蔽列表 */
data class MutedHistory(
    val mute_tags: List<MuteTag>? = null,
    val mute_users: List<MuteUser>? = null,
) : Serializable

data class MuteTag(
    val tag_name: String? = null,
    val translated_name: String? = null,
    val tag_type: Int = 0,
) : Serializable

data class MuteUser(
    val user: User? = null,
) : Serializable

// ── 图片 URL ────────────────────────────────────────────────────────────────

data class ImageUrls(
    val url: String? = null,
    val large: String? = null,
    val medium: String? = null,
    val original: String? = null,
    val small: String? = null,
    val square_medium: String? = null,
    val px_16x16: String? = null,
    val px_50x50: String? = null,
    val px_170x170: String? = null,
) : Serializable {

    /** 返回可用的最大尺寸 URL */
    fun best(): String? = url ?: original ?: large ?: medium ?: square_medium ?: small
        ?: px_170x170 ?: px_50x50 ?: px_16x16
}

// ── 标签 ────────────────────────────────────────────────────────────────────

data class Tag(
    val name: String? = null,
    val translated_name: String? = null,
) : Serializable {
    val displayName: String? get() = name ?: translated_name
}

data class TrendingTag(
    val tag: String? = null,
    val translated_name: String? = null,
    val illust: Illust? = null,
) : Serializable

data class TrendingTagsResponse(
    val trend_tags: List<TrendingTag> = emptyList(),
    val next_url: String? = null,
) : Serializable, Pageable<TrendingTag> {
    override val items: List<TrendingTag> get() = trend_tags
    override val nextPageUrl: String? get() = next_url
}

// ── 插画 / 作品 ─────────────────────────────────────────────────────────────

data class Illust(
    val id: Long = 0L,
    val title: String? = null,
    val type: String? = null,
    val caption: String? = null,
    val image_urls: ImageUrls? = null,
    val meta_pages: List<MetaPage>? = null,
    val meta_single_page: MetaSinglePage? = null,
    val user: User? = null,
    val tags: List<Tag>? = null,
    val create_date: String? = null,
    val page_count: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val sanity_level: Int? = null,
    val x_restrict: Int? = null,
    val restrict: Int? = null,
    val series: Series? = null,
    val total_view: Int? = null,
    val total_bookmarks: Int? = null,
    val is_bookmarked: Boolean? = null,
    val visible: Boolean? = null,
    val is_muted: Boolean? = null,
    val illust_ai_type: Int = 0,
    val tools: List<String>? = null,
) : Serializable {

    /** 是否为动图 */
    fun isGif(): Boolean = type == "ugoira"

    /** 是否为漫画 */
    fun isManga(): Boolean = type == "manga"

    /** 是否为 AI 生成（0=未知 1=人类 2=AI） */
    fun isAi(): Boolean = illust_ai_type == 2

    /** 多图首张/单图原图 URL */
    fun bestOriginalUrl(): String? {
        if (page_count > 1) {
            return meta_pages?.firstOrNull()?.image_urls?.original
        }
        return meta_single_page?.original_image_url ?: image_urls?.original
    }
}

data class MetaPage(
    val image_urls: ImageUrls? = null,
) : Serializable

data class MetaSinglePage(
    val original_image_url: String? = null,
) : Serializable

/** 列表响应 */
data class IllustResponse(
    val illusts: List<Illust> = emptyList(),
    val next_url: String? = null,
) : Serializable, Pageable<Illust> {
    override val items: List<Illust> get() = illusts
    override val nextPageUrl: String? get() = next_url
}

/** 推荐列表（首屏带排行榜预览） */
data class HomeIllustResponse(
    val illusts: List<Illust> = emptyList(),
    val ranking_illusts: List<Illust> = emptyList(),
    val next_url: String? = null,
) : Serializable, Pageable<Illust> {
    override val items: List<Illust> get() = illusts
    override val nextPageUrl: String? get() = next_url
}

/** 作品详情 */
data class SingleIllustResponse(
    val illust: Illust? = null,
) : Serializable

/** 动图元数据 */
data class GifResponse(
    val illust_id: Long = 0L,
    val ugoira_metadata: UgoiraMetadata? = null,
) : Serializable

data class UgoiraMetadata(
    val zip_urls: ZipUrls? = null,
    val frames: List<GifFrame>? = null,
) : Serializable

data class ZipUrls(
    val medium: String? = null,
) : Serializable

data class GifFrame(
    val file: String? = null,
    val delay: Int? = null,
) : Serializable

// ── 小说 ────────────────────────────────────────────────────────────────────

data class Novel(
    val id: Long = 0L,
    val title: String? = null,
    val caption: String? = null,
    val image_urls: ImageUrls? = null,
    val user: User? = null,
    val tags: List<Tag>? = null,
    val create_date: String? = null,
    val series: Series? = null,
    val page_count: Int? = null,
    val text_length: Int? = null,
    val restrict: Int? = null,
    val x_restrict: Int? = null,
    val is_bookmarked: Boolean? = null,
    val is_muted: Boolean? = null,
    val is_mypixiv_only: Boolean? = null,
    val is_original: Boolean? = null,
    val is_x_restricted: Boolean? = null,
    val total_view: Int? = null,
    val total_comments: Int? = null,
    val total_bookmarks: Int? = null,
    val visible: Boolean? = null,
    val novel_ai_type: Int = 0,
) : Serializable

data class NovelResponse(
    val novels: List<Novel> = emptyList(),
    val next_url: String? = null,
) : Serializable, Pageable<Novel> {
    override val items: List<Novel> get() = novels
    override val nextPageUrl: String? get() = next_url
}

data class SingleNovelResponse(
    val novel: Novel? = null,
) : Serializable

/** 推荐小说（首屏带排行榜预览） */
data class NovelRecommendResponse(
    val novels: List<Novel> = emptyList(),
    val ranking_novels: List<Novel> = emptyList(),
    val next_url: String? = null,
) : Serializable, Pageable<Novel> {
    override val items: List<Novel> get() = novels
    override val nextPageUrl: String? get() = next_url
}

/** 小说正文 HTML（/webview/v2/novel 返回 ResponseBody，这里保留 URL 模型） */
data class NovelDetailV2(
    val id: Long = 0L,
    val html: String? = null,
) : Serializable

// ── 系列 ────────────────────────────────────────────────────────────────────

data class Series(
    val id: Long = 0L,
    val title: String? = null,
) : Serializable

data class NovelSeriesDetail(
    val id: Long = 0L,
    val title: String? = null,
    val caption: String? = null,
    val display_text: String? = null,
    val user: User? = null,
    val is_original: Boolean? = null,
    val is_concluded: Boolean? = null,
    val watchlist_added: Boolean? = null,
    val content_count: Int = 0,
    val series_work_count: Int = 0,
    val novel_ai_type: Int = 0,
    val total_character_count: Int = 0,
) : Serializable

data class NovelSeriesResp(
    val novel_series_detail: NovelSeriesDetail? = null,
    val novel_series_first_novel: Novel? = null,
    val novel_series_latest_novel: Novel? = null,
    val novels: List<Novel>? = null,
    val next_url: String? = null,
) : Serializable, Pageable<Novel> {
    override val items: List<Novel> get() = novels ?: emptyList()
    override val nextPageUrl: String? get() = next_url
}

data class IllustSeriesResp(
    val illust_series_detail: NovelSeriesDetail? = null,
    val illust_series_first_illust: Illust? = null,
    val illust_series_latest_illust: Illust? = null,
    val illusts: List<Illust>? = null,
    val next_url: String? = null,
) : Serializable, Pageable<Illust> {
    override val items: List<Illust> get() = illusts ?: emptyList()
    override val nextPageUrl: String? get() = next_url
}

// ── 追更 ────────────────────────────────────────────────────────────────────

data class WatchlistSeries(
    val id: Long = 0L,
    val title: String = "",
    val url: String? = null,
    val mask_text: String? = null,
    val published_content_count: Int = 0,
    val last_published_content_datetime: String? = null,
    val latest_content_id: Long? = null,
    val user: User? = null,
) : Serializable {
    val isMasked: Boolean
        get() = title.isEmpty() && url == null && mask_text != null && (user?.id ?: 0L) == 0L
}

data class WatchlistResponse(
    val series: List<WatchlistSeries> = emptyList(),
    val next_url: String? = null,
) : Serializable, Pageable<WatchlistSeries> {
    override val items: List<WatchlistSeries> get() = series
    override val nextPageUrl: String? get() = next_url
}

// ── 评论 ────────────────────────────────────────────────────────────────────

data class Comment(
    val id: Long = 0L,
    val comment: String? = null,
    val date: String? = null,
    val user: User? = null,
    val has_replies: Boolean = false,
    val stamp: Stamp? = null,
    val replies: List<Comment>? = null,
    val parent_comment: Comment? = null,
) : Serializable

data class Stamp(
    val stamp_id: Long = 0L,
    val stamp_url: String? = null,
) : Serializable

data class StampsResponse(
    val stamps: List<Stamp> = emptyList(),
) : Serializable

data class CommentResponse(
    val comments: List<Comment> = emptyList(),
    val next_url: String? = null,
) : Serializable, Pageable<Comment> {
    override val items: List<Comment> get() = comments
    override val nextPageUrl: String? get() = next_url
}

data class PostCommentResponse(
    val comment: Comment? = null,
) : Serializable

// ── 收藏标签 / 热门标签 ──────────────────────────────────────────────────────

data class BookmarkTag(
    val name: String? = null,
    val count: Int = 0,
    val is_registered: Boolean = false,
) : Serializable

/** /v2/illust/bookmark/detail 与 /v2/novel/bookmark/detail 的响应 */
data class ListBookmarkTag(
    val bookmark_detail: BookmarkDetail? = null,
    val next_url: String? = null,
) : Serializable, Pageable<BookmarkTag> {
    override val items: List<BookmarkTag> get() = bookmark_detail?.tags ?: emptyList()
    override val nextPageUrl: String? get() = next_url
}

data class BookmarkDetail(
    val is_bookmarked: Boolean = false,
    val restrict: String? = null,
    val tags: List<BookmarkTag> = emptyList(),
) : Serializable

data class BookmarkTagResponse(
    val tags: List<BookmarkTag> = emptyList(),
    val next_url: String? = null,
) : Serializable, Pageable<BookmarkTag> {
    override val items: List<BookmarkTag> get() = tags
    override val nextPageUrl: String? get() = next_url
}

/** 小说阅读书签列表（/v2/novel/markers） */
data class NovelMarker(
    val is_cancelled: Boolean = false,
    val page: Int = 1,
) : Serializable

data class MarkedNovel(
    val novel: Novel? = null,
    val novel_marker: NovelMarker? = null,
) : Serializable

data class NovelMarkerResponse(
    val marked_novels: List<MarkedNovel> = emptyList(),
    val next_url: String? = null,
) : Serializable, Pageable<MarkedNovel> {
    override val items: List<MarkedNovel> get() = marked_novels
    override val nextPageUrl: String? get() = next_url
}

/** 用户系列列表项（小说 /v1/user/novel-series） */
data class NovelSeriesItem(
    val id: Long = 0L,
    val title: String? = null,
    val caption: String? = null,
    val is_original: Boolean = false,
    val is_concluded: Boolean = false,
    val content_count: Int = 0,
    val total_character_count: Int = 0,
    val display_text: String? = null,
    val watchlist_added: Boolean = false,
    val user: User? = null,
) : Serializable

data class NovelSeriesListResponse(
    val novel_series_details: List<NovelSeriesItem> = emptyList(),
    val next_url: String? = null,
) : Serializable, Pageable<NovelSeriesItem> {
    override val items: List<NovelSeriesItem> get() = novel_series_details
    override val nextPageUrl: String? get() = next_url
}

/** 用户系列列表项（漫画 /v1/user/illust-series） */
data class MangaSeriesItem(
    val id: Long = 0L,
    val title: String? = null,
    val caption: String? = null,
    val cover_image_urls: ImageUrls? = null,
    val series_work_count: Int = 0,
    val create_date: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val user: User? = null,
) : Serializable

data class MangaSeriesListResponse(
    val illust_series_details: List<MangaSeriesItem> = emptyList(),
    val next_url: String? = null,
) : Serializable, Pageable<MangaSeriesItem> {
    override val items: List<MangaSeriesItem> get() = illust_series_details
    override val nextPageUrl: String? get() = next_url
}

// ── 特辑文章 ────────────────────────────────────────────────────────────────

data class Article(
    val id: Long = 0L,
    val title: String? = null,
    val pure_title: String? = null,
    val thumbnail: String? = null,
    val article_url: String? = null,
    val publish_date: String? = null,
    val category: String? = null,
    val subcategory_label: String? = null,
) : Serializable

data class ArticlesResponse(
    val spotlight_articles: List<Article> = emptyList(),
    val next_url: String? = null,
) : Serializable, Pageable<Article> {
    override val items: List<Article> get() = spotlight_articles
    override val nextPageUrl: String? get() = next_url
}

// ── 通知 / 信息 ─────────────────────────────────────────────────────────────

/** /v1/notification/list 与 /v1/notification/view-more 共用 */
data class NotificationListResponse(
    val notifications: List<NotificationItem> = emptyList(),
    val next_url: String? = null,
) : Serializable, Pageable<NotificationItem> {
    override val items: List<NotificationItem> get() = notifications
    override val nextPageUrl: String? get() = next_url
}

data class NotificationItem(
    val id: Long = 0L,
    val created_datetime: String? = null,
    /** iOS 抓包见过 7(收藏)/8(关注)，仅作 hint */
    val type: Int = 0,
    val content: NotificationContent? = null,
    /** 非空 = group 头，点 view-more 调 /v1/notification/view-more 拉子列表 */
    val view_more: NotificationViewMore? = null,
    /** 永远是 pixiv:// scheme，in-app 路由用 */
    val target_url: String? = null,
    val is_read: Boolean = true,
) : Serializable

data class NotificationContent(
    val text: String? = null,
    val left_icon: String? = null,
    val left_image: String? = null,
    val right_icon: String? = null,
    val right_image: String? = null,
) : Serializable

data class NotificationViewMore(
    val unread_exists: Boolean = false,
    val title: String? = null,
) : Serializable

/** /v1/info/latest —— 按 category 分块，无 next_url */
data class InfoLatestResponse(
    val categorized_infos: List<CategorizedInfo> = emptyList(),
) : Serializable, Pageable<CategorizedInfo> {
    override val items: List<CategorizedInfo> get() = categorized_infos
    override val nextPageUrl: String? get() = null
}

/** /v1/info/list?cid=N —— 单一分类完整列表，可翻页（注意字段是单数 categorized_info） */
data class InfoListResponse(
    val categorized_info: CategorizedInfo? = null,
    val next_url: String? = null,
) : Serializable, Pageable<InfoItem> {
    override val items: List<InfoItem> get() = categorized_info?.info_list ?: emptyList()
    override val nextPageUrl: String? get() = next_url
}

data class CategorizedInfo(
    val category_id: Int = 0,
    val category_title: String? = null,
    val info_list: List<InfoItem> = emptyList(),
) : Serializable

data class InfoItem(
    val id: Long = 0L,
    val title: String? = null,
    val date: String? = null,
    val url: String? = null,
    val is_recent: Boolean = false,
) : Serializable

// ── 举报 ────────────────────────────────────────────────────────────────────

data class IllustReportTopicListResponse(
    val topic_list: List<IllustReportTopic> = emptyList(),
) : Serializable

data class IllustReportTopic(
    val topic_id: Int = 0,
    val topic_title: String? = null,
) : Serializable

// ── 搜索联想 ────────────────────────────────────────────────────────────────

data class AutocompleteTag(
    val name: String? = null,
    val translated_name: String? = null,
) : Serializable

data class AutocompleteResponse(
    val tags: List<AutocompleteTag> = emptyList(),
) : Serializable

// ── IDP 配置（/idp-urls）──────────────────────────────────────────────────────

/**
 * /idp-urls 响应 —— pixiv 官方账号体系相关 URL 配置。
 * 字段名是带连字符的（account-edit 等），需 @SerializedName。
 */
data class IdpUrlsResponse(
    @SerializedName("account-edit") val accountEdit: String? = null,
    @SerializedName("account-leave-prepare") val accountLeavePrepare: String? = null,
    @SerializedName("account-leave-status") val accountLeaveStatus: String? = null,
    @SerializedName("account-setting-prepare") val accountSettingPrepare: String? = null,
    @SerializedName("auth-token") val authToken: String? = null,
    @SerializedName("auth-token-redirect-uri") val authTokenRedirectUri: String? = null,
) : Serializable

// ── 约稿方案（/v1/user/request-plans）────────────────────────────────────────

/** 约稿方案列表（单页返回，无 next_url） */
data class UserRequestPlansResponse(
    val request_plans: List<RequestPlan>? = null,
    val user: User? = null,
    val user_profile: RequestPlanUserProfile? = null,
) : Serializable

data class RequestPlanUserProfile(
    val background_image_url: String? = null,
) : Serializable

/** 单个约稿方案。standard_price 单位日元；ai_type: 1=非AI 2=AI */
data class RequestPlan(
    val id: Long = 0L,
    val standard_price: Int = 0,
    val accept_flags: RequestPlanAcceptFlags? = null,
    val ai_type: Int = 0,
    val title: RequestPlanText? = null,
    val description: RequestPlanText? = null,
    val image_urls: RequestPlanImageUrls? = null,
) : Serializable

data class RequestPlanAcceptFlags(
    val adult: Boolean = false,
    val anonymous: Boolean = false,
    val illust: Boolean = false,
    val ugoira: Boolean = false,
    val manga: Boolean = false,
    val novel: Boolean = false,
) : Serializable

/** 服务端把标题/说明拆成原文 + 译文 */
data class RequestPlanText(
    val original: String? = null,
    val original_lang: String? = null,
    val translation: String? = null,
) : Serializable {
    fun display(): String = translation?.takeIf { it.isNotBlank() }
        ?: original?.takeIf { it.isNotBlank() }
        ?: ""
}

data class RequestPlanImageUrls(
    val cover: String? = null,
    val card: String? = null,
) : Serializable {
    fun cardOrCover(): String? = card?.takeIf { it.isNotBlank() } ?: cover
}

// ── 搜索选项（/v1/search/options）────────────────────────────────────────────

/**
 * /v1/search/options 响应 —— 官方新版 iOS app 用来动态拉「当前账号可选的筛选选项」。
 * illust / novel 两个 scope 各自带一份（illust 多 tool，novel 多 genre）。
 */
data class SearchOptionsResponse(
    val illust: SearchOptionsScope? = null,
    val novel: SearchOptionsScope? = null,
) : Serializable

data class SearchOptionsScope(
    val bookmark_ranges: List<BookmarkRange>? = null,
    val show_ai_condition: Boolean = false,
    val tool: SearchToolOptions? = null,
    val genre: SearchGenreOptions? = null,
    val lang: SearchLangOptions? = null,
    val word_count_supported_languages: String? = null,
) : Serializable

/** 收藏数范围；`"*"` 是「不限」哨兵 */
data class BookmarkRange(
    val bookmark_num_min: String? = null,
    val bookmark_num_max: String? = null,
) : Serializable {
    fun minInt(): Int? = bookmark_num_min?.takeIf { it != "*" }?.toIntOrNull()
    fun maxInt(): Int? = bookmark_num_max?.takeIf { it != "*" }?.toIntOrNull()
}

data class SearchToolOptions(
    val options: List<String> = emptyList(),
) : Serializable

data class SearchGenreOptions(
    val options: List<SearchGenreOption> = emptyList(),
) : Serializable

data class SearchGenreOption(
    val id: Int = 0,
    val label: String? = null,
) : Serializable

data class SearchLangOptions(
    val options: List<SearchLangOption> = emptyList(),
) : Serializable

data class SearchLangOption(
    val code: String? = null,
    val name: String? = null,
) : Serializable

// ── 个人资料预设（/v1/user/profile/presets）──────────────────────────────────

data class Preset(
    val profile_presets: ProfilePresets? = null,
) : Serializable

data class ProfilePresets(
    val default_profile_image_urls: ImageUrls? = null,
    val addresses: List<PresetAddress> = emptyList(),
    val countries: List<PresetCountry> = emptyList(),
    val jobs: List<PresetJob> = emptyList(),
) : Serializable

data class PresetAddress(
    val id: Int = 0,
    val name: String? = null,
    val is_global: Boolean = false,
) : Serializable

data class PresetCountry(
    val code: String? = null,
    val name: String? = null,
) : Serializable

data class PresetJob(
    val id: Int = 0,
    val name: String? = null,
) : Serializable

// ── 简单用户列表（收藏该作品的用户等）───────────────────────────────────────

/** 收藏该作品的用户等简单用户列表 */
data class SimpleUserResponse(
    val users: List<User> = emptyList(),
    val next_url: String? = null,
) : Serializable, Pageable<User> {
    override val items: List<User> get() = users
    override val nextPageUrl: String? get() = next_url
}
