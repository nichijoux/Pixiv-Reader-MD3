package com.pixiv.reader.feature.reader.data

import android.content.Context
import android.util.Log
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.core.novel.NovelBlock
import com.pixiv.reader.core.novel.NovelDocument
import com.pixiv.reader.core.novel.NovelParser
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 小说正文数据加载器：拉取详情 + HTML + 网页图片映射 + 解析 + [pixivimage:ID] 引用解析。
 *
 * 纯数据层（不持有 UI 状态），从 [loadNovel] 返回 `Result<Pair<Novel, NovelDocument>>`，
 * 由 ReaderViewModel 负责状态流转。
 */
class ReaderDataLoader(
    private val pixivRepository: PixivRepository,
    private val context: Context,
) {
    /** 加载小说详情与解析正文；失败返回 Result.failure（含网络/解析异常）。 */
    suspend fun loadNovel(novelId: Long): Result<Pair<Novel?, NovelDocument>> = runCatching {
        val detail = pixivRepository.api.getNovel(novelId).novel
        val html = withContext(Dispatchers.IO) {
            val raw = pixivRepository.api.getNovelHtml(novelId).string()
            logNovelHtml(novelId, raw)
            raw
        }
        // 网页小说详情：拿 textEmbeddedImages（正文嵌入图片映射，key 为 novelImageId）
        val webNovel = runCatching {
            pixivRepository.webApi.getNovelWeb(novelId).body
        }.getOrNull()
        val imageUrls = webNovel?.textEmbeddedImages
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
        val document = withContext(Dispatchers.IO) {
            // 解析 + [pixivimage:ID] 引用画作 → ajax/illust/{id} 解析首图 URL
            resolvePixivImages(NovelParser.parse(html, imageUrls))
        }
        logParseResult(novelId, document)
        detail to document
    }

    /**
     * 把正文中的 `[pixivimage:ID]` 标记解析为画作首图 URL。
     * 通过网页接口 `ajax/illust/{id}` 的 `urls.regular/original` 获取（带 Cookie，正常可访问）；
     * 解析失败的标记保留原文（渲染层按图片块显示但加载失败占位）。
     */
    private suspend fun resolvePixivImages(document: NovelDocument): NovelDocument {
        val pending = document.blocks
            .filterIsInstance<NovelBlock.Image>()
            .filter { it.url.startsWith("pixivimage:") }
        if (pending.isEmpty()) return document
        val resolved = mutableMapOf<String, String>()
        for (img in pending) {
            val id = img.url.removePrefix("pixivimage:").toLongOrNull() ?: continue
            val body = runCatching { pixivRepository.webApi.getWebIllust(id).body }.getOrNull() ?: continue
            val url = body.urls?.get("regular") ?: body.urls?.get("original")
            if (!url.isNullOrBlank()) resolved[img.url] = url
        }
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
        const val TAG = "ReaderDataLoader"
    }
}
