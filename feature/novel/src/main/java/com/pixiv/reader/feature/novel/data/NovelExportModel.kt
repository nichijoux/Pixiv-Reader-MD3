package com.pixiv.reader.feature.novel.data

enum class NovelExportFormat { TXT, EPUB, PDF, MARKDOWN, DOCX }

/** [width]/[height] 仅 DOCX 按页宽缩放使用，EPUB/Markdown 传 0。 */
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
