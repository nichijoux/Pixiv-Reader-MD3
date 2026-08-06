package com.pixiv.reader.feature.novel.data

import com.pixiv.api.model.Novel
import com.pixiv.reader.core.novel.NovelBlock
import com.pixiv.reader.core.novel.NovelDocument
import com.pixiv.reader.core.novel.NovelParser
import com.pixiv.reader.core.network.session.PixivRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 小说正文获取管线：详情元数据 + 正文 HTML + 网页插图映射 → NovelDocument。
 *
 * 与 ReaderViewModel.load 中的加载链路保持一致（getNovel → getNovelHtml →
 * getNovelWeb.textEmbeddedImages → NovelParser.parse → resolvePixivImages）。
 * TODO(后续)：抽到共享层让阅读器也复用，消除重复实现。
 */
@Singleton
class NovelContentLoader @Inject constructor(
    private val pixivRepository: PixivRepository,
) {
    /**
     * 抓取并解析小说全文。
     * @return 详情元数据 + 结构化正文文档；任一环节失败返回失败原因。
     */
    suspend fun load(novelId: Long): Result<Pair<Novel, NovelDocument>> = runCatching {
        val detail = pixivRepository.api.getNovel(novelId).novel
            ?: error("没有找到该小说")
        val html = withContext(Dispatchers.IO) {
            pixivRepository.api.getNovelHtml(novelId).string()
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
}
