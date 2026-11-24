package com.pixiv.reader.core.network.update

import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class AppRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
)

/** 版本号语义化三段比较：兼容 `v` 前缀与 `-beta` 等后缀，每段取前导数字，缺失段按 0。 */
object AppUpdateVersion {

    /**
     * 连字符后缀视为同版本的预发布标记，比较前剔除，避免 `v1.2.3-rc.1` 的 `.1` 被当成更高段；
     * 远程解析失败或不高时返回 false（宁可漏报不误报）。
     */
    fun isNewer(local: String, remoteTag: String): Boolean {
        val remote = parseSegments(remoteTag) ?: return false
        val localSeg = parseSegments(local) ?: return false
        return compareSegments(remote, localSeg) > 0
    }

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
 * 读 GitHub Releases latest（CI 打 v* tag 自动发布）；匿名限额 60 次/小时/IP，
 * 故自动检查每进程仅一次，远低于限额。
 */
@Singleton
class AppUpdateChecker @Inject constructor() {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** 最新 Release 地址（internal 便于单测替换语义）。 */
    private val releasesLatestUrl =
        "https://api.github.com/repos/nichijoux/Pixiv-Reader-MD3/releases/latest"

    /**
     * `null` = HTTP 404（仓库尚无发布，非错误）；其余失败走 [Result.failure]，
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
