package com.pixiv.reader.core.network.offline

import android.content.Context
import com.example.pixivapi.model.ImageUrls
import com.example.pixivapi.model.Novel
import com.example.pixivapi.model.Series
import com.example.pixivapi.model.User
import com.pixiv.reader.core.novel.NovelDocument
import com.pixiv.reader.core.novel.NovelDocumentCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 小说离线缓存仓库：把解析后的 [NovelDocument] + 最小元数据写入应用私有目录，
 * 离线阅读时优先读取（无需网络与重新解析）。
 *
 * 文件结构：`filesDir/offline/novels/{id}.json`（文档）+ `{id}_meta.json`（元数据）
 */
@Singleton
class OfflineNovelRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val offlineDir: File
        get() = File(context.filesDir, "offline/novels").apply { mkdirs() }

    private fun docFile(novelId: Long): File = File(offlineDir, "$novelId.json")
    private fun metaFile(novelId: Long): File = File(offlineDir, "${novelId}_meta.json")

    /** 保存小说文档 + 最小元数据。 */
    suspend fun save(novel: Novel, document: NovelDocument) = withContext(Dispatchers.IO) {
        docFile(novel.id).writeText(NovelDocumentCodec.encode(document), Charsets.UTF_8)
        metaFile(novel.id).writeText(encodeMeta(novel), Charsets.UTF_8)
    }

    /** 读取离线文档（无缓存返回 null）。 */
    suspend fun loadDocument(novelId: Long): NovelDocument? = withContext(Dispatchers.IO) {
        val f = docFile(novelId)
        if (!f.exists()) null else NovelDocumentCodec.decode(f.readText(Charsets.UTF_8))
    }

    /** 读取离线元数据（无缓存返回 null）。 */
    suspend fun loadNovel(novelId: Long): Novel? = withContext(Dispatchers.IO) {
        val f = metaFile(novelId)
        if (!f.exists()) null else decodeMeta(f.readText(Charsets.UTF_8))
    }

    /** 是否有离线缓存。 */
    suspend fun exists(novelId: Long): Boolean = withContext(Dispatchers.IO) {
        docFile(novelId).exists() && metaFile(novelId).exists()
    }

    /** 删除离线缓存。 */
    suspend fun delete(novelId: Long) = withContext(Dispatchers.IO) {
        docFile(novelId).delete()
        metaFile(novelId).delete()
    }

    // ── meta 编解码（org.json，仅保留阅读器离线所需字段） ──────────────────

    private fun encodeMeta(novel: Novel): String {
        val obj = JSONObject()
        obj.put("id", novel.id)
        obj.put("title", novel.title.orEmpty())
        novel.image_urls?.medium?.let { obj.put("cover", it) }
        novel.series?.let {
            obj.put("seriesId", it.id)
            obj.put("seriesTitle", it.title.orEmpty())
        }
        novel.user?.let {
            obj.put("userId", it.id)
            obj.put("userName", it.name.orEmpty())
        }
        novel.text_length?.let { obj.put("textLength", it) }
        novel.page_count?.let { obj.put("pageCount", it) }
        obj.put("isBookmarked", novel.is_bookmarked == true)
        return obj.toString()
    }

    private fun decodeMeta(json: String): Novel? = runCatching {
        val obj = JSONObject(json)
        val id = obj.optLong("id")
        Novel(
            id = id,
            title = obj.optString("title"),
            image_urls = ImageUrls(medium = obj.optString("cover").ifEmpty { null }),
            series = if (obj.has("seriesId")) {
                Series(id = obj.optLong("seriesId"), title = obj.optString("seriesTitle"))
            } else {
                null
            },
            user = if (obj.has("userId")) {
                User(id = obj.optLong("userId"), name = obj.optString("userName"))
            } else {
                null
            },
            text_length = if (obj.has("textLength")) obj.optInt("textLength") else null,
            page_count = if (obj.has("pageCount")) obj.optInt("pageCount") else null,
            is_bookmarked = if (obj.has("isBookmarked")) obj.optBoolean("isBookmarked") else null,
        )
    }.getOrNull()
}
