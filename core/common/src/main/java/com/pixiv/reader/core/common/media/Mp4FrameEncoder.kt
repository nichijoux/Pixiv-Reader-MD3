package com.pixiv.reader.core.common.media

import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaMuxer.OutputFormat
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal fun rgbIntToYuv(pixel: Int): IntArray = IntArray(3).also { rgbIntToYuvInto(pixel, it) }

/** 零分配变体：结果写入调用方复用的 [out]（长度 ≥3）。 */
internal fun rgbIntToYuvInto(pixel: Int, out: IntArray) {
    val r = (pixel shr 16) and 0xFF
    val g = (pixel shr 8) and 0xFF
    val b = pixel and 0xFF
    // BT.601 limited range 整数近似（libyuv 同款系数，+128 四舍五入）
    out[0] = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255)
    out[1] = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
    out[2] = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
}

/** ugoira 帧延时不均，用累计 pts 表达变速帧；延时 ≤0 按 1ms 兜底防 pts 不增导致丢帧。 */
internal fun buildFramePresentationTimesUs(delaysMs: IntArray): LongArray {
    val pts = LongArray(delaysMs.size)
    var acc = 0L
    for (i in delaysMs.indices) {
        pts[i] = acc * 1000L
        acc += delaysMs[i].coerceAtLeast(1)
    }
    return pts
}

/**
 * 纯系统 API（MediaCodec ByteBuffer YUV_420_888 + MediaMuxer）H.264 编码器，避免 Surface/EGL 管线。
 * H.264 要求偶数尺寸，奇数宽高自动补黑边（Y=16 / UV=128）。
 */
class Mp4FrameEncoder {

    private class MuxerSession(val muxer: MediaMuxer) {
        var trackIndex = -1
        var started = false
    }

    /**
     * [frameAt] 返回的位图用完即 recycle（返回 null 视为失败）；
     * 编码失败会清理残留文件，协程取消时释放资源并向上抛 [CancellationException]。
     */
    suspend fun encode(
        width: Int,
        height: Int,
        delaysMs: IntArray,
        frameAt: (index: Int) -> Bitmap?,
        out: File,
        onProgress: suspend (encoded: Int, total: Int) -> Unit = { _, _ -> },
    ): Result<File> = withContext(Dispatchers.Default) {
        runCatching {
            require(width > 0 && height > 0) { "invalid frame size ${width}x$height" }
            require(delaysMs.isNotEmpty()) { "no frames to encode" }
            run(width, height, delaysMs, frameAt, out, onProgress)
        }.onFailure { e ->
            // runCatching 会吞掉 CancellationException，需显式重抛；其余失败清理半截产物
            if (e is CancellationException) throw e
            runCatching { out.delete() }
        }
    }

    private suspend fun run(
        width: Int,
        height: Int,
        delaysMs: IntArray,
        frameAt: (index: Int) -> Bitmap?,
        out: File,
        onProgress: suspend (encoded: Int, total: Int) -> Unit,
    ): File {
        // H.264 要求偶数尺寸，奇数向上补偶
        val encW = (width + 1) / 2 * 2
        val encH = (height + 1) / 2 * 2
        val pts = buildFramePresentationTimesUs(delaysMs)
        val bufferInfo = MediaCodec.BufferInfo()
        var codec: MediaCodec? = null
        var muxerSession: MuxerSession? = null
        try {
            codec = MediaCodec.createEncoderByType(MIME_AVC).also { c ->
                val format = MediaFormat.createVideoFormat(MIME_AVC, encW, encH).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                    setInteger(MediaFormat.KEY_BIT_RATE, computeBitRate(encW, encH))
                    setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE_HINT)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SEC)
                }
                c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                c.start()
            }
            muxerSession = MuxerSession(MediaMuxer(out.path, OutputFormat.MUXER_OUTPUT_MPEG_4))

            val total = delaysMs.size
            // 跨帧复用缓冲，避免编码热路径逐帧分配
            val yuv = IntArray(3)
            val fillBuf = ByteArray(encW * encH * 2)
            for (i in 0 until total) {
                coroutineContext.ensureActive()
                val bitmap = frameAt(i) ?: error("frame $i unavailable")
                try {
                    val inputIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
                    check(inputIndex >= 0) { "no input buffer for frame $i" }
                    val image = codec.getInputImage(inputIndex)
                        ?: error("encoder input image unavailable (frame $i)")
                    fillYuvImage(image, bitmap, yuv, fillBuf)
                    val size = encW * encH * 3 / 2
                    codec.queueInputBuffer(inputIndex, 0, size, pts[i], 0)
                } finally {
                    bitmap.recycle()
                }
                // 每帧后非阻塞抽干输出，避免输出缓冲区耗尽阻塞后续输入
                drainOutputs(codec, muxerSession, bufferInfo, endOfStream = false)
                onProgress(i + 1, total)
            }
            val eosIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
            check(eosIndex >= 0) { "no input buffer for EOS" }
            codec.queueInputBuffer(eosIndex, 0, 0, pts.last() + EOS_TAIL_US, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drainOutputs(codec, muxerSession, bufferInfo, endOfStream = true)
            check(muxerSession.started) { "muxer never started (no encoder output)" }
            return out
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            val m = muxerSession
            if (m != null) {
                if (m.started) runCatching { m.muxer.stop() }
                runCatching { m.muxer.release() }
            }
        }
    }

    private fun fillYuvImage(image: Image, bitmap: Bitmap, yuv: IntArray, fillBuf: ByteArray) {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        // 先整平面铺黑覆盖奇数补偶边与 UV 子采样角落，再叠加像素
        fillPlane(yPlane.buffer, BLACK_Y, fillBuf)
        fillPlane(uPlane.buffer, NEUTRAL_UV, fillBuf)
        fillPlane(vPlane.buffer, NEUTRAL_UV, fillBuf)

        val srcW = bitmap.width
        val srcH = bitmap.height
        val row = IntArray(srcW)
        for (y in 0 until srcH) {
            bitmap.getPixels(row, 0, srcW, 0, y, srcW, 1)
            for (x in 0 until srcW) {
                rgbIntToYuvInto(row[x], yuv)
                putSample(yPlane, x, y, yuv[0])
                if (x % 2 == 0 && y % 2 == 0) {
                    putSample(uPlane, x / 2, y / 2, yuv[1])
                    putSample(vPlane, x / 2, y / 2, yuv[2])
                }
            }
        }
    }

    private fun putSample(plane: Image.Plane, x: Int, y: Int, value: Int) {
        plane.buffer.position(y * plane.rowStride + x * plane.pixelStride)
        plane.buffer.put(value.toByte())
    }

    // 个别设备输入平面只读时跳过铺黑，由后续位图像素覆盖主要区域
    private fun fillPlane(buffer: ByteBuffer, value: Int, fillBuf: ByteArray) {
        runCatching {
            buffer.position(0)
            val n = buffer.remaining()
            if (n > fillBuf.size) return
            java.util.Arrays.fill(fillBuf, 0, n, value.toByte())
            buffer.put(fillBuf, 0, n)
        }
    }

    private fun drainOutputs(
        codec: MediaCodec,
        session: MuxerSession?,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
    ) {
        if (session == null) return
        var spin = 0
        while (true) {
            val index = codec.dequeueOutputBuffer(bufferInfo, OUTPUT_TIMEOUT_US)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // 非 EOS 轮次允许暂无输出；EOS 轮次限次防死循环
                    if (!endOfStream) return
                    if (++spin > EOS_MAX_SPIN) error("encoder EOS timeout")
                }

                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    session.trackIndex = session.muxer.addTrack(codec.outputFormat)
                    session.muxer.start()
                    session.started = true
                }

                index >= 0 -> {
                    val data: ByteBuffer? = codec.getOutputBuffer(index)
                    if (data != null && bufferInfo.size > 0 &&
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && session.trackIndex >= 0
                    ) {
                        data.position(bufferInfo.offset)
                        data.limit(bufferInfo.offset + bufferInfo.size)
                        session.muxer.writeSampleData(session.trackIndex, data, bufferInfo)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    // ≈4 bit/像素/秒（30fps 下约 0.13 bpp/帧），钳位到 2..40 Mbps
    private fun computeBitRate(width: Int, height: Int): Int =
        (width * height * 4).coerceIn(MIN_BIT_RATE, MAX_BIT_RATE)

    private companion object {
        const val MIME_AVC = MediaFormat.MIMETYPE_VIDEO_AVC
        const val FRAME_RATE_HINT = 30
        const val I_FRAME_INTERVAL_SEC = 1
        const val INPUT_TIMEOUT_US = 10_000L
        const val OUTPUT_TIMEOUT_US = 10_000L

        // 保证末帧不被编码器视为零长丢弃
        const val EOS_TAIL_US = 100_000L
        const val MIN_BIT_RATE = 2_000_000
        const val MAX_BIT_RATE = 40_000_000
        const val BLACK_Y = 16
        const val NEUTRAL_UV = 128

        // ×10ms ≈ 6 秒无输出视为编码器僵死
        const val EOS_MAX_SPIN = 600
    }
}
