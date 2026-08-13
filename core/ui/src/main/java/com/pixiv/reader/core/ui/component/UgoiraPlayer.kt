package com.pixiv.reader.core.ui.component

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import com.pixiv.reader.core.network.ugoira.UgoiraFrame
import com.pixiv.reader.core.ui.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * ugoira 动图播放：**双缓冲预解码**——显示当前帧的同时后台预解码下一帧，帧延迟到点直接交换。
 *
 * 流畅性：帧周期 = max(解码耗时, 帧延迟)，而非旧的串行「解码 + 延迟」；
 * 只要解码 < 帧延迟即完全按 delay 帧率播放（解码超过延迟时退化为解码速度，不会更差）。
 * 内存峰值 = 2 帧 Bitmap（首帧先解码，之后始终持有当前帧 + 预解码帧）。
 *
 * 逐帧解码（非全量载入）控制内存；[maxDecodeSize] 限定采样解码最长边（px），
 * 瀑布流卡片传封面宽度避免解码原图尺寸（查看器传 null 用原图）。
 *
 * @param frames 帧列表（[UgoiraLoader.prepare] 产出；空列表不渲染）
 * @param maxDecodeSize 采样解码最长边上限（px）；null = 原尺寸解码
 * @param contentScale 帧渲染缩放方式（查看器 Fit 完整显示；卡片 Crop 填满封面）
 * @param loadingContent 帧就绪前的占位内容；null 则不渲染（露出下层封面）
 */
@Composable
fun UgoiraPlayer(
    frames: List<UgoiraFrame>,
    modifier: Modifier = Modifier,
    maxDecodeSize: Int? = null,
    contentScale: ContentScale = ContentScale.Fit,
    loadingContent: (@Composable () -> Unit)? = null,
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(frames) {
        if (frames.isEmpty()) return@LaunchedEffect
        // 首帧先解码（无预解码可用），随后进入双缓冲循环
        var index = 0
        bitmap = withContext(Dispatchers.IO) { decodeSampled(frames[index].file, maxDecodeSize) }
        while (true) {
            val nextIndex = (index + 1) % frames.size
            // 后台预解码下一帧，与当前帧的停留时间并行（LaunchedEffect 取消时自动取消）
            val next = async(Dispatchers.IO) { decodeSampled(frames[nextIndex].file, maxDecodeSize) }
            delay(frames[index].delayMs.toLong())
            bitmap = next.await()
            index = nextIndex
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = stringResource(R.string.ugoira_cd),
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else {
            loadingContent?.invoke()
        }
    }
}

/** 采样解码：最长边不超过 [maxSize]（px）；null 或非正数原尺寸解码。 */
private fun decodeSampled(file: File, maxSize: Int?): Bitmap? {
    if (maxSize == null || maxSize <= 0) return BitmapFactory.decodeFile(file.absolutePath)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSize || bounds.outHeight / (sample * 2) >= maxSize) {
        sample *= 2
    }
    return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
}
