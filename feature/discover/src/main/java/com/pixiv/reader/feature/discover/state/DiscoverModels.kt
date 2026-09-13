package com.pixiv.reader.feature.discover.state

import androidx.annotation.StringRes
import com.pixiv.reader.feature.discover.R

/** 搜索结果显示类型（Tab）。 */
enum class SearchType(@param:StringRes val labelRes: Int) {
    ILLUST(R.string.search_type_illust),
    NOVEL(R.string.search_type_novel),
    USER(R.string.search_type_user),
}

/**
 * 全量搜索筛选（对齐 Pixiv-Shaft V3 维度集；排序单维度：热门预览是排序一档）。
 *
 * sort 取值：popular_preview 热门预览（走 popular-preview 接口，一次性）/ date_desc 最新 /
 * date_asc 最旧 / popular_desc 按热度（默认）/ popular_male_desc、popular_female_desc 男/女性向
 * 人气（插画专属 + 仅会员，见 [DiscoverViewModel] 路由与兜底）。
 */
data class SearchFilters(
    val sort: String = "popular_desc",
    val searchTarget: String = "partial_match_for_tags",
    /** 收藏数下限（官方 bookmark_num_min 参数；UI 预设档，0=不限） */
    val bookmarkNumMin: Int? = null,
    /** 「Xusers入り」关键字后缀档（500/1000/…，null=无）：非会员也可用的收藏量过滤，与 bookmarkNumMin 独立并存 */
    val keywordUsersBucket: Int? = null,
    /** 绘画工具（仅插画，/v1/search/options 拉取；在「其他条件」sheet 设置） */
    val tool: String? = null,
    /** 小说类型（仅小说，/v1/search/options 拉取） */
    val genre: Int? = null,
    /** 语种（仅小说，/v1/search/options 拉取；code 值） */
    val lang: String? = null,
    /** 投稿期间相对预设档（Last24Hours/LastWeek/LastMonth/LastHalfYear/LastYear；与 startDate/endDate 互斥，请求时算 today−N） */
    val durationBucket: String? = null,
    val startDate: String? = null,    // YYYY-MM-DD —— 与 durationBucket 互斥
    val endDate: String? = null,      // YYYY-MM-DD
    /** AI 作品三档（wire 为 UserPreferences 持久化值；请求映射见 DiscoverViewModel） */
    val aiType: AiFilter = AiFilter.ALL,
    /** R18 三档（对齐 Shaft；wire 同为持久化值，客户端过滤） */
    val r18Mode: R18Filter = R18Filter.ALL,
    /** 长宽比（仅插画，官方值 landscape/portrait/square；null=所有） */
    val ratioPattern: String? = null,
    /** 分辨率档位（仅插画）：Above3000 / Between1000And2999 / Below1000；null=全部清晰度 */
    val resolutionBucket: String? = null,
    /** 作品类别（仅插画，5 档官方值）；null = 默认档「插画、漫画、动图」等价不传 */
    val contentType: String? = null,
    /** 正文长度（仅小说）：unit 0 文字数 / 1 单词数 / 2 阅读用时（分钟）；min/max 为区间端 */
    val bodyLengthUnit: Int? = null,
    val bodyLengthMin: Int? = null,
    val bodyLengthMax: Int? = null,
    // 小说专属开关
    val isOriginalOnly: Boolean? = null,
    val isReplaceableOnly: Boolean? = null,
)

/**
 * AI 作品筛选三档（[wire] 为 UserPreferences 持久化值；请求映射见 DiscoverViewModel.search）。
 */
enum class AiFilter(val wire: Int) {
    ALL(0),
    HUMAN_ONLY(1),
    AI_ONLY(2);

    companion object {
        /**
         * 从持久化 wire 值解析。
         *
         * @param wire 持久化值（未知值回退 [ALL]）
         * @return 解析结果（永不失败）
         */
        fun fromWire(wire: Int): AiFilter = entries.firstOrNull { it.wire == wire } ?: ALL
    }
}

/**
 * R18 筛选三档（对齐 Shaft；[wire] 同为持久化值，过滤在客户端按 x_restrict 执行）。
 */
enum class R18Filter(val wire: Int) {
    ALL(0),
    SAFE_ONLY(1),
    R18_ONLY(2);

    companion object {
        /**
         * 从持久化 wire 值解析。
         *
         * @param wire 持久化值（未知值回退 [ALL]）
         * @return 解析结果（永不失败）
         */
        fun fromWire(wire: Int): R18Filter = entries.firstOrNull { it.wire == wire } ?: ALL
    }
}
