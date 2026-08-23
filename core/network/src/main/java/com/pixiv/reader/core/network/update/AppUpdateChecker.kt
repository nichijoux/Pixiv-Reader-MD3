package com.pixiv.reader.core.network.update

import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * GitHub Release 信息（检查更新所需字段的最小子集）。
 *
 * @param tagName 标签名（如 v1.2.3）
 * @param name 发布标题（可能为空）
 * @param body 发布说明 markdown 正文（changelog）
 * @param htmlUrl Release 页面地址（浏览器打开下载）
 */
data class AppRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
)

/**
 * 版本号比较工具：语义化三段比较，兼容 `v` 前缀与 `-beta` 等后缀
 * （每段取前导数字参与比较，缺失段按 0 处理）。
 */
object AppUpdateVersion {

    /**
     * 判断远程版本是否比本地新。
     *
     * 连字符后缀（`-beta`/`-rc.1` 等）视为同一版本的预发布标记，比较前剔除——
     * 避免把 `v1.2.3-rc.1` 的 `.1` 误判为更高段。
     *
     * @param local 本地 versionName（如 1.2.3）
     * @param remoteTag 远程 tag（如 v1.2.4）
     * @return remote 解析失败或不高时返回 false（宁可漏报不误报）
     */
    fun isNewer(local: String, remoteTag: String): Boolean {
        val remote = parseSegments(remoteTag) ?: return false
        val localSeg = parseSegments(local) ?: return false
        return compareSegments(remote, localSeg) > 0
    }

    /** 提取版本段：去掉 `v` 前缀与 `-后缀` 后按 `.` 切分，每段取前导数字；整体不含数字视为非法。 */
    internal fun parseSegments(version: String): List<Int>? {
        val base = version.trim().removePrefix("v").removePrefix("V").substringBefore('-')
        val segments = base.split('.')
        // 任一段含前导数字才算合法版本（过滤空串/纯文本）
        if (segments.none { seg -> seg.firstOrNull()?.isDigit() == true }) return null
        return segments.map { seg -> seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
    }

    private fun compareSegments(a: List<Int>, b: List<Int>): Int {
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }
}

/**
 * 检查应用更新：读取本仓库 GitHub Releases 最新发布
 * （CI 推送 v* tag 自动构建并发布，见 .github/workflows/release-apk.yml）。
 *
 * 匿名调用限额 60 次/小时/IP；启动自动检查每进程仅一次 + 手动触发，远低于限额。
 */
@Singleton
class AppUpdateChecker @Inject constructor() {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** 最新 Release 地址（internal 便于单测替换语义说明；实际请求固定走该 URL）。 */
    private val releasesLatestUrl =
        "https://api.github.com/repos/nichijoux/Pixiv-Reader-MD3/releases/latest"

    /**
     * 获取最新 Release。
     *
     * @return `null` = 仓库尚无任何发布（HTTP 404，视为"无可用更新"，非错误）；
     * 其余失败场景（网络不通、限流、JSON 缺字段）以 [Result.failure] 返回，
     * 由调用方决定提示或静默（自动检查静默）。
     */
    suspend fun latestRelease(): Result<AppRelease?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(releasesLatestUrl)
                .header("Accept", "application/vnd.github+json")
                // GitHub API 要求 User-Agent，缺省返回 403
                .header("User-Agent", "PixivReader-Android")
                .build()
            client.newCall(request).execute().use { response ->
                // 404：仓库从未推送 v* tag / 未发布过 Release → 无更新而非故障
                if (response.code == 404) return@runCatching null
                check(response.isSuccessful) { "GitHub API HTTP ${response.code}" }
                parseRelease(response.body?.string().orEmpty())
                    ?: error("Release 响应缺少必要字段")
            }
        }
    }

    /** 解析 /releases/latest 响应体（Gson JsonParser，JVM 单测可用）。 */
    internal fun parseRelease(json: String): AppRelease? {
        if (json.isBlank()) return null
        val obj = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return null
        val tag = obj.get("tag_name")?.takeIf { !it.isJsonNull }?.asString ?: return null
        if (tag.isBlank()) return null
        return AppRelease(
            tagName = tag,
            name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
            body = obj.get("body")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
            htmlUrl = obj.get("html_url")?.takeIf { !it.isJsonNull }?.asString
                ?: "https://github.com/nichijoux/Pixiv-Reader-MD3/releases",
        )
    }
}
