package com.pixiv.reader.core.common.parse

/** pixiv 网页链接可导航的内容类型。 */
enum class PixivLinkType {
    /** 小说详情（novel/show.php?id=…） */
    NOVEL,

    /** 小说系列（novel/series/…） */
    SERIES,

    /** 插画 / 漫画（artworks/…、manga/…，详情路由同为 illust/{id}） */
    ILLUST,

    /** 用户主页（users/…、user/…/…） */
    USER,
}

/** 从 pixiv 网页链接解析出的导航目标。 */
data class PixivLink(val type: PixivLinkType, val id: Long)

/**
 * pixiv 网页链接解析器：从任意剪贴板文本中提取第一个 pixiv 详情链接并解析为导航目标。
 *
 * 支持（均兼容 `www.` / 裸 host、http/https）：
 * - 小说详情：`pixiv.net/novel/show.php?id={id}`（旧式）、`pixiv.net/novel/{id}`（新式，含 `#页码`）
 * - 小说系列：`pixiv.net/novel/series/{id}`
 * - 插画 / 漫画：`pixiv.net/artworks/{id}`、`pixiv.net/manga/{id}`、`pixiv.net/i/{id}`、`pixiv.net/illust_id={id}`（旧式）
 * - 用户主页：`pixiv.net/users/{id}`、`pixiv.net/user/{id}/…`、`pixiv.net/member.php?id={id}`（旧式）
 *
 * 纯函数无 Android 依赖，可用 JVM 单测。
 */
object PixivUrlParser {

    /** 匹配 pixiv.net 下各类详情链接，捕获数字 id。 */
    private val LINK_REGEX = Regex(
        """(?:https?://)?(?:www\.)?pixiv\.net/(?:novel/show\.php\?(?:[^#\s]*&)?id=|novel/series/|novel/|artworks/|manga/|i/|users?/|member\.php\?(?:[^#\s]*&)?id=|illust_id=)(\d+)""",
    )

    /**
     * 解析文本中的 pixiv 链接。
     *
     * @param text 剪贴板原文（可含多余文字/多个链接，取第一个有效匹配）
     * @return 解析出的导航目标；未找到或 id 非法返回 null
     */
    fun parse(text: String): PixivLink? {
        val match = LINK_REGEX.find(text) ?: return null
        val id = match.groupValues[1].toLongOrNull() ?: return null
        if (id <= 0L) return null
        val type = when {
            "/novel/series/" in match.value -> PixivLinkType.SERIES
            "/novel/show.php" in match.value || "/novel/" in match.value -> PixivLinkType.NOVEL
            "/artworks/" in match.value || "/manga/" in match.value ||
                "/i/" in match.value || "illust_id=" in match.value -> PixivLinkType.ILLUST
            else -> PixivLinkType.USER
        }
        return PixivLink(type, id)
    }
}
