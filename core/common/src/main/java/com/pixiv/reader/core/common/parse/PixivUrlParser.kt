package com.pixiv.reader.core.common.parse

import java.net.URI

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
 * 支持（兼容 www / 裸 host、http/https 及 /en/ 前缀）：
 * - 小说详情：`pixiv.net/novel/show.php?id={id}`（旧式）、`pixiv.net/novel/{id}`（新式，含 `#页码`）
 * - 小说系列：`pixiv.net/novel/series/{id}`
 * - 插画 / 漫画：`pixiv.net/artworks/{id}`、`pixiv.net/manga/{id}`、`pixiv.net/i/{id}`、`pixiv.net/illust_id={id}`（旧式）
 * - 用户主页：`pixiv.net/users/{id}`、`pixiv.net/user/{id}/…`、`pixiv.net/member.php?id={id}`（旧式）
 * - 旧式插画页面：member_illust.php 使用 illust_id 指向插画，使用 id 指向用户
 *
 * 纯函数无 Android 依赖，可用 JVM 单测。
 */
object PixivUrlParser {

    /** 提取完整的候选 URL，避免把站外链接中的 host 后缀或路径当成无协议链接。 */
    private val URL_REGEX = Regex(
        """(?:([a-z][a-z0-9+.-]*://)|(?<![\p{L}\p{N}_.@/:%?&#=+~\\。．｡-])(?:www\.)?pixiv\.net/)[^\s\p{Z}<>"“”‘’，。！？；：、（）【】《》]+""",
        RegexOption.IGNORE_CASE,
    )
    private val ID_PATH_REGEX = Regex("""(novel/series|novel|artworks|manga|i|users?)/(\d+)(?:/.*)?""")

    /**
     * 解析文本中的 pixiv 链接。
     *
     * @param text 剪贴板原文（可含多余文字/多个链接，取第一个有效匹配）
     * @return 解析出的导航目标；未找到或 id 非法返回 null
     */
    fun parse(text: String): PixivLink? {
        // 跳过非法 URL 或 id，继续找文本中的第一个可导航链接。
        return URL_REGEX.findAll(text).firstNotNullOfOrNull { match ->
            val candidate = match.value.trimEnd('.', ',', ';', '!', ')', ']', '*', '\'')
            val url = if (match.groups[1] == null) "https://$candidate" else candidate
            val uri = runCatching { URI(url) }.getOrNull() ?: return@firstNotNullOfOrNull null
            parseUrl(uri)
        }
    }

    /**
     * 校验站点并解析现代路径或旧式查询参数。
     *
     * @param uri 从剪贴板提取的单个候选地址
     * @return 对应的导航目标，域名、路径或 id 不符合规则时为 null
     */
    private fun parseUrl(uri: URI): PixivLink? {
        // 使用 URI 解析的真实 host，不从任意 URL 文本中截取站名。
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if ((scheme != "http" && scheme != "https") || uri.userInfo != null) return null
        if (host != "www.pixiv.net" && host != "pixiv.net") return null
        val path = uri.rawPath.removePrefix("/").removePrefix("en/")
        val modern = ID_PATH_REGEX.matchEntire(path)
        val query = uri.rawQuery?.split('&')?.associate {
            it.substringBefore('=') to it.substringAfter('=', "")
        }.orEmpty()

        // 旧式插画页可能同时包含两个参数，作品 id 优先于作者 id。
        val (type, rawId) = when {
            modern != null -> {
                val (section, id) = modern.destructured
                val linkType = when (section) {
                    "novel/series" -> PixivLinkType.SERIES
                    "novel" -> PixivLinkType.NOVEL
                    "users", "user" -> PixivLinkType.USER
                    else -> PixivLinkType.ILLUST
                }
                linkType to id
            }
            path == "novel/show.php" -> PixivLinkType.NOVEL to query["id"]
            path == "member.php" -> PixivLinkType.USER to query["id"]
            path == "member_illust.php" && "illust_id" in query -> PixivLinkType.ILLUST to query["illust_id"]
            path == "member_illust.php" -> PixivLinkType.USER to query["id"]
            path.startsWith("illust_id=") -> PixivLinkType.ILLUST to path.removePrefix("illust_id=")
            else -> return null
        }
        val id = rawId?.takeIf { value -> value.all { it in '0'..'9' } }
            ?.toLongOrNull()?.takeIf { it > 0L } ?: return null
        return PixivLink(type, id)
    }
}

/**
 * 解析纯数字 ID 输入（搜索框 ID 直达用）。
 *
 * @param text 用户原始输入
 * @return trim 后非空、可转 Long 且 > 0 时返回该 ID；其余情况（空白、含非数字、0、负数、Long 溢出）返回 null
 */
fun parseNumericId(text: String): Long? = text.trim().toLongOrNull()?.takeIf { it > 0L }
