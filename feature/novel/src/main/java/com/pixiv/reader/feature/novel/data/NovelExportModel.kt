package com.pixiv.reader.feature.novel.data

/** 导出格式：TXT（纯文本跳过插图） / EPUB（标准电子书内嵌插图） / PDF / MARKDOWN / DOCX。 */
enum class NovelExportFormat { TXT, EPUB, PDF, MARKDOWN, DOCX }

/** 导出范围：当前小说单本 / 整个系列。 */
enum class NovelExportScope { CURRENT, SERIES }

/** EPUB 内嵌图片：ref 为 OEBPS/images 下的相对文件名（如 `img_0_1.jpg` / `cover.jpg`）。 */
internal data class EpubImage(
    val ref: String,
    val bytes: ByteArray,
    val mime: String = "image/jpeg",
)
