package com.pixiv.reader.feature.discover.state

import androidx.annotation.StringRes
import com.pixiv.reader.feature.discover.R

/** 搜索结果显示类型（Tab）。 */
enum class SearchType(@StringRes val labelRes: Int) {
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
    /** AI 作品三档：0 全部 / 1 仅人绘（search_ai_type=1）/ 2 仅看 AI（search_ai_type=0 + 客户端按 ai_type==2 过滤） */
    val aiType: Int = 0,
    /** R18 三档（对齐 Shaft）：0 全部 / 1 仅安全（x_restrict<=0）/ 2 仅 R18（x_restrict>0），客户端过滤 */
    val r18Mode: Int = 0,
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
