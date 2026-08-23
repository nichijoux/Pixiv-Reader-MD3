package com.pixiv.reader.core.network.novel

import android.content.Context
import android.util.Log
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.network.BuildConfig
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.core.novel.model.NovelBlock
import com.pixiv.reader.core.novel.model.NovelDocument
import com.pixiv.reader.core.novel.parser.NovelParser
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 小说正文数据加载器（core 共享）：详情元数据 + 正文 HTML + 网页插图映射 → NovelDocument。
 *
 * 供阅读器（feature:reader）与导出（feature:novel）复用——原两处同构管线
 * （getNovel → getNovelHtml → getNovelWeb.textEmbeddedImages → NovelParser.parse →
 * resolvePixivImages）合并于此，解析策略/插图规则只维护一份。
 *
 * 返回 `Result<Pair<Novel?, NovelDocument>>`：详情可空（网络接口未返回时调用方自行决定
 * 兜底文案），正文文档一定解析产出。
 */
@Singleton
class NovelContentLoader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val pixivRepository: PixivRepository,
) {

    /**
     * 抓取并解析小说全文。
     * @return 详情元数据（可能为 null）+ 结构化正文文档；任一环节失败返回失败原因。
     */
    suspend fun load(novelId: Long): Result<Pair<Novel?, NovelDocument>> = runCatching {
        // 阶段 1：详情 + 正文 HTML（OAuth app-api）
        val detail = pixivRepository.api.getNovel(novelId).novel
        val html = withContext(Dispatchers.IO) {
            val raw = pixivRepository.api.getNovelHtml(novelId).string()
            if (BuildConfig.DEBUG) logNovelHtml(novelId, raw)
            raw
        }
        // 阶段 2：网页插图映射（ajax/novel，匿名；失败静默降级为无映射）
        val webNovel = runCatching {
            pixivRepository.webApi.getNovelWeb(novelId).body
        }.getOrNull()
        Log.i(TAG, "novel[$novelId] webNovel=${webNovel != null} tei=${webNovel?.textEmbeddedImages?.size ?: 0}")
        val imageUrlsBase = webNovel?.textEmbeddedImages
            ?.mapNotNull { (file, info) ->
                val urls = info?.urls
                val url = urls?.get("1200x1200")
                    ?: urls?.get("480mw")
                    ?: urls?.get("240mw")
                    ?: urls?.get("original")
                    ?: info?.url
                if (url.isNullOrBlank()) return@mapNotNull null
                "uploadedimage:$file" to url
            }
            ?.toMap()
            ?: emptyMap()
        var imageUrls = imageUrlsBase
        if (imageUrls.isEmpty() && html.contains("[uploadedimage:")) {
            // 正文含上传图标记但映射为空：匿名接口未返回 textEmbeddedImages
            // （R18/受限作品 / 图片已删除）。uploadedimage 无 app-api 等价接口，图片块将显示占位。
            Log.w(TAG, "novel[$novelId] 正文含 [uploadedimage:] 标记但 textEmbeddedImages 为空——上传图无法解析")
        }
        // 阶段 2b：从 webview HTML（OAuth 鉴权）提取上传图映射兜底——
        // 匿名 ajax/novel 对受限作品不返回 tei，但 HTML 本身经 OAuth 获取，
        // 其 novel 对象内嵌 images（上传图）字段可补齐。
        val htmlUploaded = extractHtmlEmbeddedImages(html)
        if (htmlUploaded.isNotEmpty()) {
            // 匿名 tei 优先，HTML 只补缺失键（避免覆盖公开作品的既有尺寸选择）
            val merged = imageUrls.toMutableMap()
            htmlUploaded.forEach { (k, v) -> if (k !in merged) merged[k] = v }
            imageUrls = merged
        }
        Log.i(TAG, "novel[$novelId] HTML嵌入图: uploaded=${htmlUploaded.size} 合并后映射=${imageUrls.size}")
        // 阶段 3：解析 + [pixivimage:ID] 引用解析（内部有逐图日志）
        val document = withContext(Dispatchers.IO) {
            resolvePixivImages(NovelParser.parse(html, imageUrls))
        }
        val imgCount = document.blocks.count { it is NovelBlock.Image }
        val unresolved = document.blocks
            .filterIsInstance<NovelBlock.Image>()
            .count { it.url.startsWith("pixivimage:") || it.url.startsWith("uploadedimage:") }
        Log.i(TAG, "novel[$novelId] 解析块=${document.blocks.size} 图片块=$imgCount 未解析协议串=$unresolved")
        if (BuildConfig.DEBUG) logParseResult(novelId, document)
        detail to document
    }

    /**
     * 把正文 `[pixivimage:ID]` 标记解析为画作首图 URL；解析失败保留原标记。
     *
     * 解析策略（两级）：
     * 1. 匿名网页接口 `ajax/illust/{id}`（公开作品）；
     * 2. 失败或 urls 为空时回退 app-api `/v1/illust/detail`（OAuth token）——
     *    实测匿名 ajax/illust 对 R18 作品返回 urls=null，app-api 登录态可拿（账号允许 R18 时）。
     */
    private suspend fun resolvePixivImages(document: NovelDocument): NovelDocument {
        val pending = document.blocks
            .filterIsInstance<NovelBlock.Image>()
            .filter { it.url.startsWith("pixivimage:") }
        if (pending.isEmpty()) return document
        Log.i(TAG, "resolvePixivImages: 待解析 pixivimage 引用 ${pending.size} 张")
        val resolved = mutableMapOf<String, String>()
        for (img in pending) {
            val id = img.url.removePrefix("pixivimage:").toLongOrNull() ?: continue
            val url = resolveIllustImageUrl(id)
            if (url != null) {
                resolved[img.url] = url
            } else {
                Log.w(TAG, "resolvePixivImages: illust $id 解析失败（作品不存在/已删除/账号无权限）")
            }
        }
        Log.i(TAG, "resolvePixivImages: 解析成功 ${resolved.size}/${pending.size} 张")
        if (resolved.isEmpty()) return document
        val newBlocks = document.blocks.map { block ->
            if (block is NovelBlock.Image) {
                resolved[block.url]?.let { block.copy(url = it) } ?: block
            } else {
                block
            }
        }
        return NovelDocument(blocks = newBlocks, fullText = document.fullText, textLength = document.textLength)
    }

    /**
     * 解析 `[pixivimage:ID]` 引用画作的首图 URL。
     * @return 图片 URL；全部解析路径失败返回 null。
     */
    private suspend fun resolveIllustImageUrl(illustId: Long): String? {
        // 1) 匿名网页接口（公开作品，与历史行为一致）
        runCatching { pixivRepository.webApi.getWebIllust(illustId).body }.getOrNull()?.let { body ->
            body.urls?.get("regular")?.takeIf { it.isNotBlank() }?.let {
                Log.d(TAG, "illust $illustId → 网页接口 regular: ${it.take(100)}")
                return it
            }
            body.urls?.get("original")?.takeIf { it.isNotBlank() }?.let {
                Log.d(TAG, "illust $illustId → 网页接口 original: ${it.take(100)}")
                return it
            }
        }
        // 2) app-api（OAuth token）：R18 等受限作品兜底
        runCatching { pixivRepository.api.getIllust(illustId) }.getOrNull()?.illust?.let { ill ->
            // 大图（master1200，与网页 regular 同级）优先，原图/中图兜底
            ill.image_urls?.large?.takeIf { it.isNotBlank() }?.let {
                Log.d(TAG, "illust $illustId → app-api large: ${it.take(100)}")
                return it
            }
            ill.meta_single_page?.original_image_url?.takeIf { it.isNotBlank() }?.let {
                Log.d(TAG, "illust $illustId → app-api original: ${it.take(100)}")
                return it
            }
            ill.meta_pages?.firstOrNull()?.image_urls?.original?.takeIf { it.isNotBlank() }?.let {
                Log.d(TAG, "illust $illustId → app-api meta_pages original: ${it.take(100)}")
                return it
            }
            ill.image_urls?.medium?.takeIf { it.isNotBlank() }?.let {
                Log.d(TAG, "illust $illustId → app-api medium: ${it.take(100)}")
                return it
            }
        }
        return null
    }

    /**
     * 调试：打印并保存原始 HTML，便于排查"没有正文内容"。
     * HTML 写入 cacheDir/novel_debug/{id}.html，可用 Android Studio Device Explorer 取出。
     */
    private fun logNovelHtml(novelId: Long, html: String) {
        runCatching {
            Log.d(TAG, "novel[$novelId] html length=${html.length}")
            // 分段打印前 1200 字符（避免 logcat 单行截断）
            val preview = html.take(1200)
            Log.d(TAG, "novel[$novelId] html head:\n$preview")
            val dir = File(context.cacheDir, "novel_debug").apply { mkdirs() }
            val file = File(dir, "${novelId}.html")
            file.writeText(html)
            Log.d(TAG, "novel[$novelId] html saved to: ${file.absolutePath}")
        }.onFailure { Log.w(TAG, "logNovelHtml failed", it) }
    }

    /** 调试：打印解析结果（块数 / 全文长度 / 各块类型 / 全文开头）。 */
    private fun logParseResult(novelId: Long, document: NovelDocument) {
        runCatching {
            Log.d(TAG, "parse result: blocks=${document.blocks.size}, textLength=${document.textLength}")
            if (document.blocks.isEmpty()) {
                Log.d(TAG, "parse produced NO blocks (fullText empty)")
            } else {
                val types = document.blocks.take(20).map { it::class.simpleName }
                Log.d(TAG, "first 20 block types: $types")
                Log.d(TAG, "fullText head: ${document.fullText.take(300)}")
            }
        }.onFailure { Log.w(TAG, "logParseResult failed", it) }
    }

    private companion object {
        const val TAG = "NovelContentLoader"
    }
}

/**
 * 从 webview HTML（OAuth 鉴权获取）的 `window.pixiv.novel` 对象提取上传图映射。
 *
 * 背景：`uploadedimage`（小说上传图）的 URL 常规来源是匿名 `ajax/novel` 的
 * textEmbeddedImages；但对受限作品（R18 等）匿名接口返回空映射，正文嵌入图无法解析
 * （URL 保留协议串，阅读器显示占位）。而 webview HTML 本身经 app-api（OAuth token）
 * 获取，其 novel 对象内嵌 `images`（上传图）字段，可作登录态兜底。
 *
 * @return `uploadedimage:{id}` → URL；解析失败返回空映射（不影响既有逻辑）。
 */
internal fun extractHtmlEmbeddedImages(html: String): Map<String, String> {
    if (html.isBlank()) return emptyMap()
    return runCatching {
        val novelJson = extractNovelJson(html) ?: return emptyMap()
        val root = com.google.gson.Gson().fromJson(novelJson, com.google.gson.JsonObject::class.java)
        extractUploadedImages(root.get("images"))
    }.getOrDefault(emptyMap())
}

/**
 * 从 novel 对象 `images` 字段提取「uploadedimage:{id} → URL」映射。
 *
 * 兼容两种结构（实测空时为数组 `[]`，有图时可能为对象或数组）：
 * - 对象：`{id: {novelImageId, sl, urls: {...}}}`
 * - 数组：`[{novelImageId, sl, urls: {...}}, ...]`
 *
 * URL 优先级：1200x1200 > 480mw > 240mw > original > url。
 */
private fun extractUploadedImages(element: com.google.gson.JsonElement?): Map<String, String> {
    if (element == null) return emptyMap()
    val entries: List<Pair<String, com.google.gson.JsonObject>> = when {
        element.isJsonObject -> element.asJsonObject.entrySet().mapNotNull { (k, v) ->
            (v as? com.google.gson.JsonObject)?.let { k to it }
        }
        element.isJsonArray -> element.asJsonArray.mapNotNull { el ->
            val obj = el as? com.google.gson.JsonObject ?: return@mapNotNull null
            val id = obj.get("novelImageId")?.takeIf { it.isJsonPrimitive }?.asString
                ?: obj.get("id")?.takeIf { it.isJsonPrimitive }?.asString
                ?: return@mapNotNull null
            id to obj
        }
        else -> emptyList()
    }
    val result = mutableMapOf<String, String>()
    entries.forEach { (id, info) ->
        val urls = info.get("urls")?.takeIf { it.isJsonObject }?.asJsonObject
        val url = urls?.get("1200x1200")?.takeIf { it.isJsonPrimitive }?.asString
            ?: urls?.get("480mw")?.takeIf { it.isJsonPrimitive }?.asString
            ?: urls?.get("240mw")?.takeIf { it.isJsonPrimitive }?.asString
            ?: urls?.get("original")?.takeIf { it.isJsonPrimitive }?.asString
            ?: info.get("url")?.takeIf { it.isJsonPrimitive }?.asString
        if (!url.isNullOrBlank()) result["uploadedimage:$id"] = url
    }
    return result
}

/**
 * 定位 pixiv 全局对象中的 `novel:` 并做**字符串感知**的花括号匹配，
 * 返回 novel 对象的 JSON 文本。
 *
 * 兼容两种内嵌形式（实测当前为 defineProperty 形式）：
 * 1. `Object.defineProperty(window, 'pixiv', { value: { ..., novel: {...} } })`
 * 2. `window.pixiv = { ..., novel: {...} }`
 *
 * 花括号匹配必须跳过字符串字面量（正文 text 可能含未转义的 `{`/`}`），
 * 且 `\` 转义后的引号不计入字符串结束。
 */
internal fun extractNovelJson(html: String): String? {
    var anchor = -1
    val defIdx = html.indexOf("Object.defineProperty(window")
    if (defIdx >= 0) {
        val key = html.indexOf("'pixiv'", defIdx)
        if (key >= 0) anchor = html.indexOf("novel:", key)
    }
    if (anchor < 0) {
        val pixivIdx = html.indexOf("window.pixiv")
        if (pixivIdx >= 0) anchor = html.indexOf("novel:", pixivIdx)
    }
    if (anchor < 0) return null
    var i = anchor + "novel:".length
    while (i < html.length && html[i] != '{') i++
    if (i >= html.length) return null
    val start = i
    var depth = 0
    var inString = false
    while (i < html.length) {
        val c = html[i]
        if (inString) {
            if (c == '\\') {
                i++ // 跳过转义字符本身
            } else if (c == '"') {
                inString = false
            }
        } else {
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return html.substring(start, i + 1)
                }
            }
        }
        i++
    }
    return null
}
