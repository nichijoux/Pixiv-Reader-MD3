package com.pixiv.reader.core.network.ugoira

import com.pixiv.api.model.GifFrame
import java.io.File

/**
 * ugoira 帧 zip 解压（导出器与在线加载器共用）：已存在的帧跳过，zip 重下后自然断点续解压。
 * 本对象统一抛异常，错误策略由调用方决定（导出器删 zip 后重抛 → failed；加载器删 zip 后返回 null）。
 */
internal object UgoiraFrameExtractor {

    /** 帧延时下限 10ms；`frame.file` 为 null 的帧跳过。 */
    fun unzip(zip: File, destDir: File, frames: List<GifFrame>): List<UgoiraFrame> =
        java.util.zip.ZipFile(zip).use { zf ->
            frames.mapNotNull { frame ->
                val entryName = frame.file ?: return@mapNotNull null
                val out = File(destDir, entryName.substringAfterLast('/'))
                if (!out.exists()) {
                    // 缺 entry 与损坏 zip 同等对待（抛错 → 调用方删 zip），避免静默跳过导致后续缺帧
                    val entry = zf.getEntry(entryName)
                        ?: throw IllegalStateException("missing zip entry: $entryName")
                    // 每帧写完即关闭输出流，避免长动图累积文件描述符。
                    zf.getInputStream(entry).use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                UgoiraFrame(file = out, delayMs = (frame.delay ?: 80).coerceAtLeast(10))
            }
        }
}
