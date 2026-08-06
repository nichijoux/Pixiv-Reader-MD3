package com.pixiv.reader.feature.novel.data

import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** XML 转义（纯函数，可测）。 */
internal fun escapeXml(s: String): String = buildString {
    for (c in s) {
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }
}

/** 文件名清洗：替换文件系统非法字符（纯函数，可测）。 */
internal fun sanitizeFileName(name: String): String =
    name.replace(Regex("""[\\/:*?"<>|\r\n]"""), "_")
        .trim()
        .ifBlank { "novel" }
        .take(80)

internal fun ZipOutputStream.writeEntry(name: String, content: String) {
    writeEntry(name, content.toByteArray(Charsets.UTF_8))
}

internal fun ZipOutputStream.writeEntry(name: String, content: ByteArray) {
    putNextEntry(ZipEntry(name))
    write(content)
    closeEntry()
}

internal fun crc32(bytes: ByteArray): Long {
    val crc = CRC32()
    crc.update(bytes)
    return crc.value
}
