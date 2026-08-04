package com.pixiv.reader.core.novel

import org.json.JSONArray
import org.json.JSONObject

/**
 * NovelDocument 编解码（JSON）。
 *
 * 用于离线缓存：下载时把解析后的文档序列化到本地文件，
 * 离线阅读时直接反序列化重建 [NovelDocument]，无需网络与重新解析。
 *
 * 说明：使用 Android 内置 org.json（零额外依赖）；图片块保留最终 URL
 * （离线时正文插图若未缓存文件，渲染层加载失败显示占位，文本完整）。
 */
object NovelDocumentCodec {

    /**
     * 序列化 [NovelDocument] 为 JSON 字符串。
     * 块类型编码：`p` 段落 / `h` 标题(带 level) / `q` 引用 / `i` 图片(带 url/caption) / `s` 分隔线。
     *
     * @param document 待序列化文档
     * @return JSON 字符串
     */
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
