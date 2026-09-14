package com.pixiv.reader.core.network.ugoira

import com.pixiv.api.model.GifFrame
import java.io.File

/**
 * ugoira 帧 zip 解压共享实现（导出器 [com.pixiv.reader.core.network.download.UgoiraExporter]
 * 与在线加载器 [UgoiraLoader] 共用）：
 * 逐帧解压到目标目录，已存在的帧文件跳过（zip 重下后天然断点续解压）。
 * 帧文件名在 zip 内为平铺文件名（`substringAfterLast('/')` 截断路径）。
 *
 * 错误策略由调用方包装：缺 entry / zip 损坏时本对象统一抛异常，
 * 导出器删 zip 后重抛（转 failed 状态），加载器删 zip 后返回 null（下次重新下载）。
 */
internal object UgoiraFrameExtractor {

    /**
     * 解压 [zip] 内 [frames] 声明的各帧文件到 [destDir]。
     *
     * @param zip ugoira 帧包 zip 文件
     * @param destDir 帧输出目录（调用方保证已创建）
     * @param frames 元数据声明的帧列表（文件名 + 延时毫秒）
     * @return 帧列表（本地文件 + 生效延时，下限 10ms；`frame.file` 为 null 的帧跳过）
     * @throws IllegalStateException zip 内缺少 [frames] 声明的 entry（静默跳过会导致后续缺帧，不容忍）
     * @throws Exception zip 损坏或写文件 IO 失败（原样抛出，错误策略由调用方决定）
     */
    fun unzip(zip: File, destDir: File, frames: List<GifFrame>): List<UgoiraFrame> =
        java.util.zip.ZipFile(zip).use { zf ->
            frames.mapNotNull { frame ->
                val entryName = frame.file ?: return@mapNotNull null
                val out = File(destDir, entryName.substringAfterLast('/'))
                if (!out.exists()) {
                    // 缺 entry 与损坏 zip 同等对待（抛错 → 调用方删 zip），避免静默跳过导致后续缺帧
                    val entry = zf.getEntry(entryName)
                        ?: throw IllegalStateException("missing zip entry: $entryName")
                    zf.getInputStream(entry).use { it.copyTo(out.outputStream()) }
                }
                UgoiraFrame(file = out, delayMs = (frame.delay ?: 80).coerceAtLeast(10))
            }
        }
}
