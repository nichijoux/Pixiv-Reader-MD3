package com.pixiv.reader.core.network.comic

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/** 从 COMIC viewer SSR HTML 的 `__NEXT_DATA__` 提取每次随机生成的 `props.pageProps.salt`（供 read_v4 签名）。 */
object ComicViewerSaltParser {

    private val NEXT_DATA_REGEX = Regex(
        pattern = """<script\s+id="__NEXT_DATA__"[^>]*>(.*?)</script>""",
        options = setOf(RegexOption.DOT_MATCHES_ALL),
    )

    fun parse(html: String): String? = runCatching {
        // 按原始键逐层读取，避免数据模型字段受混淆影响
        val json = NEXT_DATA_REGEX.find(html)?.groupValues?.get(1) ?: return@runCatching null
        val root = JsonParser.parseString(json) as? JsonObject ?: return@runCatching null
        val props = root["props"] as? JsonObject ?: return@runCatching null
        val pageProps = props["pageProps"] as? JsonObject ?: return@runCatching null
        val salt = pageProps["salt"] as? JsonPrimitive ?: return@runCatching null
        // 只接受 JSON 字符串，避免数字/布尔值被 Gson 转成签名输入
        salt.takeIf { it.isString }?.asString?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
