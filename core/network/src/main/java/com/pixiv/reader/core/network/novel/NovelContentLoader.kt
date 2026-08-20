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
    @ApplicationContext private val context: Context,
    private val pixivRepository: PixivRepository,
) {

    /**
     * 抓取并解析小说全文。
     * @return 详情元数据（可能为 null）+ 结构化正文文档；任一环节失败返回失败原因。
     */
    suspend fun load(novelId: Long): Result<Pair<Novel?, NovelDocument>> = runCatching {
        val detail = pixivRepository.api.getNovel(novelId).novel
        val html = withContext(Dispatchers.IO) {
            val raw = pixivRepository.api.getNovelHtml(novelId).string()
            if (BuildConfig.DEBUG) logNovelHtml(novelId, raw)
            raw
        }
        // 网页小说详情：正文嵌入图片映射（uploadedimage:file → 真实 URL）
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
            resolvePixivImages(NovelParser.parse(html, imageUrls))
        }
        if (BuildConfig.DEBUG) logParseResult(novelId, document)
        detail to document
    }

    /** 把正文 `[pixivimage:ID]` 标记解析为画作首图 URL；解析失败保留原标记。 */
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
        const val TAG = "NovelContentLoader"
    }
}
