package com.pixiv.reader.feature.discover.state

import androidx.annotation.StringRes
import com.pixiv.reader.feature.discover.R

enum class SearchType(@param:StringRes val labelRes: Int) {
    ILLUST(R.string.search_type_illust),
    NOVEL(R.string.search_type_novel),
    USER(R.string.search_type_user),
}

/**
 * sort: popular_preview（一次性热门预览）/ date_desc / date_asc / popular_desc /
 * popular_male_desc / popular_female_desc（后两者仅插画且需会员，见 DiscoverViewModel 降级）。
 */
data class SearchFilters(
    val sort: String = "popular_desc",
    val searchTarget: String = "partial_match_for_tags",
    val bookmarkNumMin: Int? = null,
    /** 「Xusers入り」关键词后缀档，与 bookmarkNumMin 独立并存（非会员可用）。 */
    val keywordUsersBucket: Int? = null,
    val tool: String? = null,
    val genre: Int? = null,
    val lang: String? = null,
    /** 投稿期间相对预设档，与 startDate/endDate（YYYY-MM-DD）互斥，请求时按 today−N 计算。 */
    val durationBucket: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val aiType: AiFilter = AiFilter.ALL,
    val r18Mode: R18Filter = R18Filter.ALL,
    val ratioPattern: String? = null,
    val resolutionBucket: String? = null,
    /** null 与 "illust_and_manga_and_ugoira" 等价，请求侧不传。 */
    val contentType: String? = null,
    /** 正文长度单位（仅小说）：0=文字数 / 1=单词数 / 2=阅读用时（分钟）。 */
    val bodyLengthUnit: Int? = null,
    val bodyLengthMin: Int? = null,
    val bodyLengthMax: Int? = null,
    val isOriginalOnly: Boolean? = null,
    val isReplaceableOnly: Boolean? = null,
)

/** [wire] 为 UserPreferences 持久化值，变更需兼容存量数据。 */
enum class AiFilter(val wire: Int) {
    ALL(0),
    HUMAN_ONLY(1),
    AI_ONLY(2);

    companion object {
        fun fromWire(wire: Int): AiFilter = entries.firstOrNull { it.wire == wire } ?: ALL
    }
}

/** [wire] 为持久化值；服务端无该参数，由客户端按 x_restrict 过滤。 */
enum class R18Filter(val wire: Int) {
    ALL(0),
    SAFE_ONLY(1),
    R18_ONLY(2);

    companion object {
        fun fromWire(wire: Int): R18Filter = entries.firstOrNull { it.wire == wire } ?: ALL
    }
}
