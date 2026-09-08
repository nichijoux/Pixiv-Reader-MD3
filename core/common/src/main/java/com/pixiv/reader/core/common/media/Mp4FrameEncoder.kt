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

/**
 * RGB(Int ARGB) → YUV（BT.601 limited range）三分量的整数实现（避免浮点、可单测）。
 *
 * @param pixel ARGB 打包的像素值（Bitmap.getPixels 产物，高 8 位 alpha 忽略）
 * @return `[y, u, v]` 三分量（均已 clamp 到 0..255）
 */
internal fun rgbIntToYuv(pixel: Int): IntArray {
    // 提取 R/G/B（getPixels 产出 ARGB 序，alpha 在最高字节）
    val r = (pixel shr 16) and 0xFF
    val g = (pixel shr 8) and 0xFF
    val b = pixel and 0xFF
    // BT.601 limited range 整数近似（libyuv 同款系数，+128 四舍五入）
    val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
    return intArrayOf(y.coerceIn(0, 255), u.coerceIn(0, 255), v.coerceIn(0, 255))
}

/**
 * 逐帧延时（毫秒）→ 各帧 MPEG 时间戳（微秒）：第 i 帧的 pts = 前 i 帧延时之和。
 * ugoira 帧延时逐帧不同，MP4 以 pts 表达变速帧（无需统一帧率）；延时 ≤0 的帧按 1ms 兜底，
 * 防止 pts 不增导致编码器丢帧。
 *
 * @param delaysMs 每帧展示延时毫秒
 * @return 与入参等长的 pts 微秒数组（首帧为 0）
 */
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
 * MP4 帧编码器：把「帧位图序列 + 每帧延时毫秒」编码为 H.264 MP4。
 * 纯系统 API（MediaCodec ByteBuffer 模式 + MediaMuxer），无第三方依赖。
 *
 * 帧数据经 [MediaCodec.getInputImage] 取到的 YUV_420_888 可写 Image 写入
 * （RGBA→YUV 见 [rgbIntToYuv]），避免 Surface/EGL 管线；H.264 要求宽高为偶数，
 * 奇数尺寸自动向上补偶（补边为黑色 Y=16/UV=128）。
 *
 * 内存护栏：调用方通过 [frameAt] 惰性提供帧（编码器用完即 `recycle`，不持有全部位图）。
 * 取消：逐帧检查协程活性，取消时释放编码器资源并向上抛 CancellationException。
 */
class Mp4FrameEncoder {

    /** muxer 轨道下标（编码器输出格式就绪后注册）。 */
    private var trackIndex = -1

    /** muxer 是否已启动（决定 finally 中是否需要 stop）。 */
    private var muxerStarted = false

    /**
     * 编码帧序列为 MP4。
     *
     * @param width 帧宽（像素，奇数自动补偶）
     * @param height 帧高（像素，奇数自动补偶）
     * @param delaysMs 每帧展示延时毫秒（长度须等于帧数）
     * @param frameAt 惰性取帧（索引 → 位图；返回 null 视为数据错误失败；位图由编码器 recycle）
     * @param out 输出 MP4 文件（已存在则覆盖；失败时删除残留）
     * @param onProgress 编码进度回调（已编码帧数, 总帧数；suspend 便于直接写库）
     * @return 编码完成的 MP4 文件
     * @throws IllegalStateException 编码器配置 / 帧数据缺失等失败场景
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
            // 取消正常向上传播（runCatching 会吞掉，这里补回）；其余失败清理半截产物
            if (e is CancellationException) throw e
            runCatching { out.delete() }
        }
    }

    /** 实际编码流程（资源在 finally 统一释放）。 */
    private suspend fun run(
        width: Int,
        height: Int,
        delaysMs: IntArray,
        frameAt: (index: Int) -> Bitmap?,
        out: File,
        onProgress: suspend (encoded: Int, total: Int) -> Unit,
    ): File {
        // H.264 要求偶数尺寸：奇数向上补偶（补边写黑色）
        val encW = (width + 1) / 2 * 2
        val encH = (height + 1) / 2 * 2
        val pts = buildFramePresentationTimesUs(delaysMs)
        trackIndex = -1
        muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
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
            muxer = MediaMuxer(out.path, OutputFormat.MUXER_OUTPUT_MPEG_4)

            val total = delaysMs.size
            for (i in 0 until total) {
                // 逐帧检查协程活性：任务取消时立即释放编码器并向上传播
                coroutineContext.ensureActive()
                val bitmap = frameAt(i) ?: error("frame $i unavailable")
                try {
                    val inputIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
                    check(inputIndex >= 0) { "no input buffer for frame $i" }
                    val image = codec.getInputImage(inputIndex)
                        ?: error("encoder input image unavailable (frame $i)")
                    fillYuvImage(image, bitmap, encW, encH)
                    val size = encW * encH * 3 / 2
                    codec.queueInputBuffer(inputIndex, 0, size, pts[i], 0)
                } finally {
                    bitmap.recycle()
                }
                // 每帧后抽干就绪输出（非阻塞），避免输出缓冲区耗尽阻塞后续输入
                drainOutputs(codec, muxer, bufferInfo, endOfStream = false)
                onProgress(i + 1, total)
            }
            // 收尾：入队 EOS 并抽干全部剩余输出
            val eosIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
            check(eosIndex >= 0) { "no input buffer for EOS" }
            codec.queueInputBuffer(eosIndex, 0, 0, pts.last() + EOS_TAIL_US, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drainOutputs(codec, muxer, bufferInfo, endOfStream = true)
            check(muxerStarted) { "muxer never started (no encoder output)" }
            return out
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
    }

    /**
     * 把位图像素写入 YUV_420_888 Image 的三个平面（含 row/pixel stride 适配与补偶黑边）。
     * UV 按 2×2 子采样（取偶行列像素值），补边区域为黑色（Y=16、UV=128）。
     */
    private fun fillYuvImage(image: Image, bitmap: Bitmap, encW: Int, encH: Int) {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        // 先整体铺黑（覆盖奇数补偶边 + 子采样未覆盖的角落），再叠加位图像素
        fillPlane(yPlane.buffer, BLACK_Y)
        fillPlane(uPlane.buffer, NEUTRAL_UV)
        fillPlane(vPlane.buffer, NEUTRAL_UV)

        val srcW = bitmap.width
        val srcH = bitmap.height
        val row = IntArray(srcW)
        for (y in 0 until srcH) {
            bitmap.getPixels(row, 0, srcW, 0, y, srcW, 1)
            for (x in 0 until srcW) {
                val yuv = rgbIntToYuv(row[x])
                putSample(yPlane, x, y, yuv[0])
                // UV 子采样：仅偶数行列写入（半分辨率平面）
                if (x % 2 == 0 && y % 2 == 0) {
                    putSample(uPlane, x / 2, y / 2, yuv[1])
                    putSample(vPlane, x / 2, y / 2, yuv[2])
                }
            }
        }
    }

    /** 按 row/pixel stride 定位并写入单字节采样。 */
    private fun putSample(plane: Image.Plane, x: Int, y: Int, value: Int) {
        plane.buffer.position(y * plane.rowStride + x * plane.pixelStride)
        plane.buffer.put(value.toByte())
    }

    /** 整平面铺满同一字节值（黑色底；个别只读平面跳过——由位图像素覆盖主要区域）。 */
    private fun fillPlane(buffer: ByteBuffer, value: Int) {
        runCatching {
            buffer.position(0)
            val fill = ByteArray(buffer.remaining()) { value.toByte() }
            buffer.put(fill)
        }
    }

    /**
     * 抽干编码器输出：输出格式就绪时向 muxer 注册轨道并启动；每拿到一帧编码数据即写入。
     *
     * @param endOfStream true 表示已入队 EOS，持续轮询直到 EOS 输出（超时上限防死循环）
     */
    private fun drainOutputs(
        codec: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
    ) {
        var spin = 0
        while (true) {
            val index = codec.dequeueOutputBuffer(bufferInfo, OUTPUT_TIMEOUT_US)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // 非 EOS 轮次允许暂时无输出（继续喂帧即可）；EOS 轮次限时防死循环
                    if (!endOfStream) return
                    if (++spin > EOS_MAX_SPIN) error("encoder EOS timeout")
                }

                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // 编码器输出格式就绪（首帧输出前到达）——注册轨道并启动 muxer
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }

                index >= 0 -> {
                    val data: ByteBuffer? = codec.getOutputBuffer(index)
                    if (data != null && bufferInfo.size > 0 &&
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && trackIndex >= 0
                    ) {
                        data.position(bufferInfo.offset)
                        data.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, data, bufferInfo)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    /** 编码码率：按像素量推算（约 4 bit/像素/帧），clamp 到 2..40 Mbps。 */
    private fun computeBitRate(width: Int, height: Int): Int =
        (width * height * 4).coerceIn(MIN_BIT_RATE, MAX_BIT_RATE)

    private companion object {
        const val MIME_AVC = MediaFormat.MIMETYPE_VIDEO_AVC
        const val FRAME_RATE_HINT = 30
        const val I_FRAME_INTERVAL_SEC = 1
        const val INPUT_TIMEOUT_US = 10_000L
        const val OUTPUT_TIMEOUT_US = 10_000L

        /** EOS 尾帧时长（微秒）：保证末帧不被编码器视为零长丢弃。 */
        const val EOS_TAIL_US = 100_000L
        const val MIN_BIT_RATE = 2_000_000
        const val MAX_BIT_RATE = 40_000_000
        const val BLACK_Y = 16
        const val NEUTRAL_UV = 128

        /** EOS 轮询上限（×10ms 超时）：约 6 秒无输出视为编码器僵死。 */
        const val EOS_MAX_SPIN = 600
    }
}
