package com.pixiv.reader.feature.novel.data

/** 导出格式：TXT（纯文本跳过插图） / EPUB（标准电子书内嵌插图） / PDF / MARKDOWN / DOCX。 */
enum class NovelExportFormat { TXT, EPUB, PDF, MARKDOWN, DOCX }

/** EPUB 内嵌图片：ref 为 OEBPS/images 下的相对文件名（如 `img_0_1.jpg` / `cover.jpg`）。 */
internal data class EpubImage(
    val ref: String,
    val bytes: ByteArray,
    val mime: String = "image/jpeg",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EpubImage

        if (ref != other.ref) return false
        if (!bytes.contentEquals(other.bytes)) return false
        if (mime != other.mime) return false

        return true
    }

    override fun hashCode(): Int {
        var result = ref.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + mime.hashCode()
        return result
    }
}
