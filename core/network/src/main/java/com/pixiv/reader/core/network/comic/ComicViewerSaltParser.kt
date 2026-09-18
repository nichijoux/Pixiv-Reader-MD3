package com.pixiv.reader.core.network.comic

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

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
     * @return salt 字符串；无 __NEXT_DATA__ 块 / JSON 解析失败 / salt 缺失时返回 null
     */
    fun parse(html: String): String? {
        // 先用正则取出内嵌 JSON 全文，再交 Gson 按需取字段——只关心一条路径，
        // 整棵 props 树没必要建模
        val json = NEXT_DATA_REGEX.find(html)?.groupValues?.get(1) ?: return null
        return runCatching {
            Gson().fromJson(json, NextData::class.java)?.props?.pageProps?.salt?.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    /** __NEXT_DATA__ 的最小建模：只取 props.pageProps.salt 一条路径。 */
    private class NextData(
        val props: Props? = null,
    )

    /** [NextData.props] 层。 */
    private class Props(
        val pageProps: PageProps? = null,
    )

    /** [Props.pageProps] 层。 */
    private class PageProps(
        val salt: String? = null,
    )
}
