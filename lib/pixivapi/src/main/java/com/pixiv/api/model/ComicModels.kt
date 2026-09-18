package com.pixiv.api.model

import com.google.gson.annotations.SerializedName

/**
 * pixiv COMIC（comic.pixiv.net）数据模型，字段对齐服务端 JSON（`/api/app/` 下
 * 各端点，2026-09 实测抓包）。
 *
 * 约定（防 Gson UnsafeAllocator 对缺失键给 null 打穿 Kotlin 非空类型）：
 * - 字符串 / 集合 / 对象字段一律可空（默认 null），使用处 `.orEmpty()` 兜底；
 * - 基础类型（Int / Long / Boolean）保持非空——JVM 原始类型缺失时是 0 / false，不会 NPE。
 *
 * 各端点响应统一为 `{"data": {...}}` 外壳；搜索结果复用 [ComicWork]（缺
 * magazine / tags / first_episode / is_following 等字段时服务端直接不给，解析为 null）。
 */

// ── 排行榜 ───────────────────────────────────────────────────────────────────

/** rankings/{weekly,popularity}/labels 响应外壳。 */
data class ComicLabelsResponse(
    val data: ComicLabels? = null,
)

/** 排行分类标签（総合 / 男性向け / … 共 12 个日文标签，请求榜单时需原样回传）。 */
data class ComicLabels(
    val labels: List<String>? = null,
)

/** rankings/weekly/v3?label= / rankings/popularity?label= 响应外壳。 */
data class ComicRankingResponse(
    val data: ComicRanking? = null,
)

/** 一份榜单（无翻页游标，服务端整榜下发）。 */
data class ComicRanking(
    val ranking: List<ComicWorkSummary>? = null,
)

/**
 * 榜单 / 最近更新共用的作品摘要。缩略图 CDN 在 `public-img-comic.pximg.net`，
 * 请求需 `Referer: https://comic.pixiv.net/`（ImageInterceptor 已按域名特判）。
 */
data class ComicWorkSummary(
    val id: Long = 0,
    val title: String? = null,
    val author: String? = null,
    @SerializedName("like_count") val likeCount: Int = 0,
    @SerializedName("is_new_work") val isNewWork: Boolean = false,
    @SerializedName("pixiv_comic_badge") val pixivComicBadge: Boolean = false,
    /** 最近一次更新章节的开始阅读时刻（毫秒时间戳），用于「最近更新」排序展示。 */
    @SerializedName("last_story_read_start_at") val lastStoryReadStartAt: Long = 0,
    @SerializedName("thumbnail_image_url") val thumbnailImageUrl: String? = null,
    @SerializedName("main_image_url") val mainImageUrl: String? = null,
    @SerializedName("stories_count") val storiesCount: Int = 0,
)

// ── 首页 ─────────────────────────────────────────────────────────────────────

/** top/v8 响应外壳。 */
data class ComicTopResponse(
    val data: ComicTopPage? = null,
)

/** COMIC 首页数据：banner 位 + 最近更新作品（notices 服务端当前不下发，忽略）。 */
data class ComicTopPage(
    val banners: List<ComicBanner>? = null,
    @SerializedName("recent_updated_official_works") val recentUpdatedOfficialWorks: List<ComicWorkSummary>? = null,
)

/** 首页 banner 位。[url] 为站内作品页（`https://comic.pixiv.net/works/{id}`）或活动页。 */
data class ComicBanner(
    val id: Long = 0,
    @SerializedName("image_url") val imageUrl: String? = null,
    val url: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    @SerializedName("in_app") val inApp: Boolean = false,
)

// ── 作品 ─────────────────────────────────────────────────────────────────────

/** works/v5/{workId} 响应外壳。 */
data class ComicWorkResponse(
    val data: ComicWorkData? = null,
)

/** works/v5 响应体。 */
data class ComicWorkData(
    @SerializedName("has_ad_books") val hasAdBooks: Boolean = false,
    @SerializedName("has_variants") val hasVariants: Boolean = false,
    @SerializedName("free_variants_count") val freeVariantsCount: Int = 0,
    @SerializedName("official_work") val officialWork: ComicWork? = null,
)

/**
 * 作品（官方漫画一部）。搜索结果同构但缺 [magazine] / [tags] / [firstEpisode] /
 * [isFollowing]，解析为 null。
 */
data class ComicWork(
    val id: Long = 0,
    val name: String? = null,
    val author: String? = null,
    /** 富文本简介，内嵌 `<br>` 换行，展示前需轻量清洗。 */
    val description: String? = null,
    @SerializedName("like_count") val likeCount: Int = 0,
    @SerializedName("is_new_work") val isNewWork: Boolean = false,
    @SerializedName("pixiv_comic_badge") val pixivComicBadge: Boolean = false,
    @SerializedName("last_story_read_start_at") val lastStoryReadStartAt: Long = 0,
    @SerializedName("stories_count") val storiesCount: Int = 0,
    @SerializedName("is_following") val isFollowing: Boolean = false,
    val image: ComicWorkImage? = null,
    val categories: List<ComicCategory>? = null,
    val magazine: ComicMagazine? = null,
    val tags: List<ComicTag>? = null,
    /** 第 1 话（works/v5 顺带下发，可省一次章节请求直接进阅读器）。 */
    @SerializedName("first_episode") val firstEpisode: ComicEpisode? = null,
) {
    /** 封面大图（无变换串原图，详情页头部用；列表场景用 [ComicWorkImage.thumbnail]）。 */
    val coverUrl: String
        get() = image?.mainBig?.takeIf { it.isNotEmpty() } ?: image?.main.orEmpty()
}

/** 作品图组（main=列表尺寸 / main_big=无变换大图 / thumbnail=缩略）。 */
data class ComicWorkImage(
    val main: String? = null,
    @SerializedName("main_big") val mainBig: String? = null,
    val thumbnail: String? = null,
)

/** 作品分类（fantasy / love 等，带主题色与图标）。 */
data class ComicCategory(
    val id: Long = 0,
    val name: String? = null,
    val color: String? = null,
    @SerializedName("icon_url") val iconUrl: String? = null,
    @SerializedName("glyph_name") val glyphName: String? = null,
)

/** 连载杂志。 */
data class ComicMagazine(
    val id: Long = 0,
    val name: String? = null,
    val image: ComicWorkImage? = null,
)

/** 作品标签。 */
data class ComicTag(
    val id: Long = 0,
    val name: String? = null,
)

// ── 章节 ─────────────────────────────────────────────────────────────────────

/** works/{workId}/episodes/v2 响应外壳。 */
data class ComicEpisodesResponse(
    val data: ComicEpisodes? = null,
)

/** 章节列表。 */
data class ComicEpisodes(
    val episodes: List<ComicEpisodeEntry>? = null,
)

/**
 * 章节条目外壳：[state] 是阅读门控（`readable` 可读 / `not_publishing` 已下架），
 * [episode] 才是章节数据本体。
 */
data class ComicEpisodeEntry(
    val state: String? = null,
    val episode: ComicEpisode? = null,
)

/**
 * 一话章节。[salesType] 为 `free` 才能免费阅读（其余为付费/预览，v1 显示锁定态）；
 * [viewerPath] 形如 `/viewer/stories/{id}`，是阅读两步流的入口。
 */
data class ComicEpisode(
    val id: Long = 0,
    /** 话数标题（「第1話①」）。 */
    @SerializedName("numbering_title") val numberingTitle: String? = null,
    /** 本话副标题。 */
    @SerializedName("sub_title") val subTitle: String? = null,
    @SerializedName("read_start_at") val readStartAt: Long = 0,
    @SerializedName("thumbnail_image_url") val thumbnailImageUrl: String? = null,
    @SerializedName("viewer_path") val viewerPath: String? = null,
    @SerializedName("is_tateyomi") val isTateyomi: Boolean = false,
    @SerializedName("sales_type") val salesType: String? = null,
    @SerializedName("is_purchased") val isPurchased: Boolean = false,
    val state: String? = null,
) {
    /** 是否可直接免费阅读。 */
    val isReadable: Boolean
        get() = state == "readable" && (salesType == "free" || isPurchased)
}

// ── 搜索 ─────────────────────────────────────────────────────────────────────

/** works/search/v2/{keyword}?page= 响应外壳（页号翻页，无 next_url 游标）。 */
data class ComicSearchResponse(
    val data: ComicSearchResult? = null,
)

/** 搜索结果（每页 30 条；下一页是否为空由调用方按返回条数判断）。 */
data class ComicSearchResult(
    @SerializedName("official_works") val officialWorks: List<ComicWork>? = null,
)

// ── 阅读 ─────────────────────────────────────────────────────────────────────

/** episodes/{id}/read_v4 响应外壳（需 X-Client-Time / X-Client-Hash 签名头）。 */
data class ComicReadResponse(
    val data: ComicReadData? = null,
)

/** read_v4 响应体。 */
data class ComicReadData(
    @SerializedName("reading_episode") val readingEpisode: ComicReadEpisode? = null,
)

/**
 * 阅读页数据。[pages] 内图片 URL 指向 `img-comic.pximg.net`，下载到的是
 * gridshuffle 打乱图，须经 ComicGridUnscrambler 还原后展示（详见 core:network
 * comic 包）。
 */
data class ComicReadEpisode(
    val id: Long = 0,
    @SerializedName("numbering_title") val numberingTitle: String? = null,
    @SerializedName("sub_title") val subTitle: String? = null,
    /** 展示用标题（服务端已拼好话数 + 副标题）。 */
    val title: String? = null,
    @SerializedName("work_id") val workId: Long = 0,
    @SerializedName("work_title") val workTitle: String? = null,
    @SerializedName("work_like_count") val workLikeCount: Int = 0,
    @SerializedName("is_tateyomi") val isTateyomi: Boolean = false,
    /** 跨页（拉页/双页spread）标记，v1 照单页渲染，仅用于提示。 */
    @SerializedName("two_page_layout") val twoPageLayout: Boolean = false,
    @SerializedName("sales_type") val salesType: String? = null,
    val pages: List<ComicPage>? = null,
    /** 下一话（阅读到底后「继续下一话」入口；无下一话为 null）。 */
    @SerializedName("next_episode") val nextEpisode: ComicEpisode? = null,
)

/** 阅读页中的一页。[gridsize] 为打乱方块边长（实测 32），[key] 参与还原种子派生。 */
data class ComicPage(
    val url: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val gridsize: Int = 0,
    val key: String? = null,
)
