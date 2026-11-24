package com.pixiv.reader.core.common.parse

import java.net.URI

enum class PixivLinkType {
    NOVEL,
    SERIES,
    ILLUST,
    USER,
}

data class PixivLink(val type: PixivLinkType, val id: Long)

/**
 * 从任意文本中提取第一个有效 pixiv 链接（小说/系列/插画/漫画/用户），
 * 兼容旧式 php 链接与 `/en/` 前缀，跳过非法 URL/ID。
 */
object PixivUrlParser {

    // 匹配完整 URL 或独立 pixiv.net/ 路径，防止把站外链接中的路径片段当成无协议链接
    private val URL_REGEX = Regex(
        """(?:([a-z][a-z0-9+.-]*://)|(?<![\p{L}\p{N}_.@/:%?&#=+~\\。．｡-])(?:www\.)?pixiv\.net/)[^\s\p{Z}<>"“”‘’，。！？；：、（）【】《》]+""",
        RegexOption.IGNORE_CASE,
    )
    private val ID_PATH_REGEX = Regex("""(novel/series|novel|artworks|manga|i|users?)/(\d+)(?:/.*)?""")

    fun parse(text: String): PixivLink? {
        return URL_REGEX.findAll(text).firstNotNullOfOrNull { match ->
            val candidate = match.value.trimEnd('.', ',', ';', '!', ')', ']', '*', '\'')
            val url = if (match.groups[1] == null) "https://$candidate" else candidate
            val uri = runCatching { URI(url) }.getOrNull() ?: return@firstNotNullOfOrNull null
            parseUrl(uri)
        }
    }

    private fun parseUrl(uri: URI): PixivLink? {
        // 校验 URI 解析出的真实 host 并拒绝 userInfo，防止伪造域名
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if ((scheme != "http" && scheme != "https") || uri.userInfo != null) return null
        if (host != "www.pixiv.net" && host != "pixiv.net") return null
        val path = uri.rawPath.removePrefix("/").removePrefix("en/")
        val modern = ID_PATH_REGEX.matchEntire(path)
        val query = uri.rawQuery?.split('&')?.associate {
            it.substringBefore('=') to it.substringAfter('=', "")
        }.orEmpty()

        // 旧式 member_illust.php 可同时含 illust_id 与 id，作品 id 优先于作者 id
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

fun parseNumericId(text: String): Long? = text.trim().toLongOrNull()?.takeIf { it > 0L }
