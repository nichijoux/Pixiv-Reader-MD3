package com.pixiv.reader.core.novel.codec

import com.pixiv.reader.core.novel.model.NovelBlock
import com.pixiv.reader.core.novel.model.NovelDocument
import org.json.JSONArray
import org.json.JSONObject

/**
 * NovelDocument 编解码（JSON）。导出小说时把解析后的文档序列化进下载条目的 payloadJson，重试/恢复时直接反序列化重建，
 * 无需重新拉取解析。用 Android 内置 org.json（零额外依赖）；图片块只存最终 URL，图片文件未缓存时渲染层显示占位。
 */
object NovelDocumentCodec {

    /** 块类型编码：`p` 段落 / `h` 标题(带 level) / `q` 引用 / `i` 图片(带 url/caption) / `s` 分隔线。 */
    fun encode(document: NovelDocument): String {
        val root = JSONObject()
        root.put("textLength", document.textLength)
        root.put("fullText", document.fullText)
        val arr = JSONArray()
        document.blocks.forEach { block ->
            val obj = JSONObject()
            when (block) {
                is NovelBlock.Paragraph -> {
                    obj.put("type", "p")
                    obj.put("text", block.text)
                }
                is NovelBlock.Heading -> {
                    obj.put("type", "h")
                    obj.put("text", block.text)
                    obj.put("level", block.level)
                }
                is NovelBlock.Quote -> {
                    obj.put("type", "q")
                    obj.put("text", block.text)
                }
                is NovelBlock.Image -> {
                    obj.put("type", "i")
                    obj.put("url", block.url)
                    if (!block.caption.isNullOrBlank()) obj.put("caption", block.caption)
                }
                is NovelBlock.Separator -> {
                    obj.put("type", "s")
                    obj.put("symbol", block.symbol)
                }
            }
            arr.put(obj)
        }
        root.put("blocks", arr)
        return root.toString()
    }

    fun decode(json: String): NovelDocument? = runCatching {
        val root = JSONObject(json)
        val fullText = root.optString("fullText", "")
        val textLength = root.optInt("textLength", fullText.length)
        val blocks = buildList {
            val arr = root.optJSONArray("blocks")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    when (obj.optString("type")) {
                        "p" -> add(NovelBlock.Paragraph(obj.optString("text")))
                        "h" -> add(
                            NovelBlock.Heading(
                                text = obj.optString("text"),
                                level = obj.optInt("level", 2),
                            ),
                        )
                        "q" -> add(NovelBlock.Quote(obj.optString("text")))
                        "i" -> add(
                            NovelBlock.Image(
                                url = obj.optString("url"),
                                caption = obj.optString("caption").ifEmpty { null },
                            ),
                        )
                        "s" -> add(NovelBlock.Separator(obj.optString("symbol")))
                    }
                }
            }
        }
        NovelDocument(blocks = blocks, fullText = fullText, textLength = textLength)
    }.getOrNull()
}
