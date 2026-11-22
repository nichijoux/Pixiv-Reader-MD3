package com.pixiv.reader.core.network.comic

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/**
 * 从 COMIC viewer 页 HTML（Next.js SSR 输出）解析阅读签名所需的随机 salt。
 *
 * viewer 页 `https://comic.pixiv.net/viewer/stories/{episodeId}` 返回的 HTML 里，
 * `<script id="__NEXT_DATA__" type="application/json">` 内嵌了页面 props，其中
 * `props.pageProps.salt` 是**每次页面加载随机生成**的 43 位 URL-safe base64，
 * 用于计算 read_v4 的 `X-Client-Hash` 签名头。
 *
 * 纯 JVM 实现（正则 + Gson），无 Android 依赖，可单测。
 */
object ComicViewerSaltParser {

    /** __NEXT_DATA__ 块提取（id 定位、其余属性不关心，DOT_MATCHES_ALL 跨行匹配 JSON 全文）。 */
    private val NEXT_DATA_REGEX = Regex(
        pattern = """<script\s+id="__NEXT_DATA__"[^>]*>(.*?)</script>""",
        options = setOf(RegexOption.DOT_MATCHES_ALL),
    )

    /**
     * 解析 viewer 页 HTML 中的 salt。
     *
     * @param html viewer 页完整 HTML
     * @return 非空白 salt 字符串。页面块缺失、解析失败或字段无效时返回 null。
     */
    fun parse(html: String): String? = runCatching {
        // 提取内嵌 JSON 后按原始键逐层读取，避免字段名受混淆影响。
        val json = NEXT_DATA_REGEX.find(html)?.groupValues?.get(1) ?: return@runCatching null
        val root = JsonParser.parseString(json) as? JsonObject ?: return@runCatching null
        val props = root["props"] as? JsonObject ?: return@runCatching null
        val pageProps = props["pageProps"] as? JsonObject ?: return@runCatching null
        val salt = pageProps["salt"] as? JsonPrimitive ?: return@runCatching null
        // 只接受 JSON 字符串，避免数字和布尔值被 Gson 转成签名输入。
        salt.takeIf { it.isString }?.asString?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
