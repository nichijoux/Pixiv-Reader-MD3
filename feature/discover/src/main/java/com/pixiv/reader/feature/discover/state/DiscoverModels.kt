package com.pixiv.reader.feature.discover.state

import androidx.annotation.StringRes
import com.pixiv.reader.feature.discover.R

/** 搜索结果显示类型（Tab）。 */
enum class SearchType(@StringRes val labelRes: Int) {
    ILLUST(R.string.search_type_illust),
    NOVEL(R.string.search_type_novel),
    USER(R.string.search_type_user),
}

/** 搜索模式：最新（正常搜索，sort=date_desc）/ 热门（仅显示热门作品，一次性）。 */
enum class SearchMode { LATEST, HOT }

/** 全量搜索筛选（sort 采用 pixiv 标准值 popular_desc）。 */
data class SearchFilters(
    val mode: SearchMode = SearchMode.LATEST,
    val sort: String = "date_desc",               // date_desc 最新 / date_asc 最旧 / popular_desc 收藏多
    val searchTarget: String = "partial_match_for_tags",
    val startDate: String? = null,
    val endDate: String? = null,
    val bookmarkNumMin: Int? = null,
    val aiType: Int = 0,                          // 0 全部 1 仅人绘 2 仅 AI
    // 插画专属
    val tool: String? = null,
    val ratioPattern: String? = null,             // square / wide / tall
    val contentType: String? = null,              // illust / manga / ugoira
    val widthMin: Int? = null,
    val widthMax: Int? = null,
    val heightMin: Int? = null,
    val heightMax: Int? = null,
    // 小说专属
    val genre: Int? = null,
    val isOriginalOnly: Boolean? = null,
    val isReplaceableOnly: Boolean? = null,
    val textLengthMin: Int? = null,
    val textLengthMax: Int? = null,
    val wordCountMin: Int? = null,
    val wordCountMax: Int? = null,
    val readingTimeMin: Int? = null,
    val readingTimeMax: Int? = null,
)
