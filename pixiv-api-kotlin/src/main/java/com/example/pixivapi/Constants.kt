package com.example.pixivapi

/**
 * Pixiv API 常量定义
 *
 * 数据来源：对 Pixiv-Shaft 项目源码分析整理（app/src/main/java/ceui/loxia 与 ceui/lisa/http）
 */
object PixivConstants {

    // ── Base URLs ────────────────────────────────────────────────────────────
    const val APP_API_HOST = "https://app-api.pixiv.net/"
    const val WEB_API_HOST = "https://www.pixiv.net/"
    const val OAUTH_HOST = "https://oauth.secure.pixiv.net/"
    const val ACCOUNT_HOST = "https://accounts.pixiv.net/"
    const val IMAGE_CDN_HOST = "https://i.pximg.net/"

    // ── 客户端身份（对齐 Shaft 新版，iOS 官方客户端抓包值）──────────────────
    const val APP_VERSION = "8.6.10"
    const val APP_OS_VERSION = "26.5"
    const val DEVICE_MODEL = "iPhone16,2"
    const val APP_USER_AGENT = "PixivIOSApp/$APP_VERSION (iOS $APP_OS_VERSION; $DEVICE_MODEL)"

    // 网页端 UA（cf_clearance cookie 绑定该 UA，WebView 与 OkHttp 必须一致）
    const val WEB_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.39 Mobile Safari/537.36"

    // ── 请求签名 ──────────────────────────────────────────────────────────────
    // 签名密钥 HASH_SECRET 与 pixiv-login 库（com.github.SoxiaLiSA:pixiv-login）
    // PixivOAuthClient.HASH_SECRET 完全一致（官方客户端抓包）。官方客户端更新后
    // 可能失效，需重新抓包同步到 pixiv-login 库与本文件。
    const val CLIENT_TIME_SECRET =
        "28c1fdd170a5204386cb1313c7077b34f83e4aaf4aa829ce78c231e05b0bae2c"

    // ── 鉴权头 ────────────────────────────────────────────────────────────────
    const val HEADER_AUTH = "authorization"
    const val TOKEN_HEAD = "Bearer "

    // ── 网络超时 ──────────────────────────────────────────────────────────────
    const val TIMEOUT_SECONDS = 10L

    // ── 作品类型 ──────────────────────────────────────────────────────────────
    const val TYPE_ILLUST = "illust"
    const val TYPE_MANGA = "manga"
    const val TYPE_GIF = "ugoira"
    const val TYPE_NOVEL = "novel"

    // ── 收藏/关注可见性 ───────────────────────────────────────────────────────
    const val RESTRICT_PUBLIC = "public"
    const val RESTRICT_PRIVATE = "private"
    const val RESTRICT_ALL = "all"

    // ── 排行榜模式 ────────────────────────────────────────────────────────────
    const val RANK_DAY = "day"
    const val RANK_WEEK = "week"
    const val RANK_MONTH = "month"
    const val RANK_DAY_MALE = "day_male"
    const val RANK_DAY_FEMALE = "day_female"
    const val RANK_WEEK_ORIGINAL = "week_original"
    const val RANK_WEEK_ROOKIE = "week_rookie"
    const val RANK_DAY_MANGA = "day_manga"
    const val RANK_DAY_R18 = "day_r18"
    const val RANK_WEEK_R18 = "week_r18"
    const val RANK_DAY_R18G = "day_r18g"

    // ── 搜索 ──────────────────────────────────────────────────────────────────
    const val SEARCH_TARGET_EXACT = "exact_match_for_tags"
    const val SEARCH_TARGET_PARTIAL = "partial_match_for_tags"
    const val SEARCH_TARGET_TITLE_CAPTION = "title_and_caption"
    const val SORT_DATE_DESC = "date_desc"
    const val SORT_DATE_ASC = "date_asc"

    // ── 图片变体 ──────────────────────────────────────────────────────────────
    const val IMG_ORIGINAL = "original"
    const val IMG_LARGE = "large"
    const val IMG_MEDIUM = "medium"
    const val IMG_SQUARE_MEDIUM = "square_medium"

    // 图片请求必须带 Referer，否则 403
    const val IMAGE_REFERER = "https://app-api.pixiv.net/"

    // 常用官方账号（用于识别官方运营/志愿者内容）
    object OfficialUsers {
        const val PIXIV = 11L
        const val PXV_SENSEI = 17391869L
        const val MANGAPIXIV = 14792128L
        const val PIXIVISION = 12848282L
        const val PXV_SKETCH = 15241365L
        const val PIXIV_MARKET = 1085317L
        const val FANBOX = 20390859L

        val all = listOf(PIXIV, PXV_SENSEI, MANGAPIXIV, PIXIVISION, PXV_SKETCH, PIXIV_MARKET, FANBOX)
    }
}

/**
 * 通用分页响应接口：所有列表接口都返回 [nextPageUrl] 做游标翻页
 */
interface Pageable<T> {
    val items: List<T>
    val nextPageUrl: String?

    val hasMore: Boolean get() = nextPageUrl != null
}
