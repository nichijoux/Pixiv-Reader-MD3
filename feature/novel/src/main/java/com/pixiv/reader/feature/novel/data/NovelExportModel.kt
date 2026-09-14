package com.pixiv.reader.feature.novel.data

/** 导出格式：TXT（纯文本跳过插图） / EPUB（标准电子书内嵌插图） / PDF / MARKDOWN / DOCX。 */
enum class NovelExportFormat { TXT, EPUB, PDF, MARKDOWN, DOCX }

/**
 * 导出内嵌插图（EPUB / DOCX / Markdown 共用）：
 * [ref] 为容器内相对文件名（EPUB `OEBPS/Images/` 下、DOCX `word/media/` 下）；
 * [width]/[height] 为原始像素（DOCX 按可用页宽缩放用；EPUB / Markdown 不需要，默认 0）。
 */
internal data class ExportImage(
    val ref: String,
    val bytes: ByteArray,
    val mime: String = "image/jpeg",
    val width: Int = 0,
    val height: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ExportImage

        if (width != other.width) return false
        if (height != other.height) return false
        if (ref != other.ref) return false
        if (!bytes.contentEquals(other.bytes)) return false
        if (mime != other.mime) return false

        return true
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + ref.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + mime.hashCode()
        return result
    }
}
