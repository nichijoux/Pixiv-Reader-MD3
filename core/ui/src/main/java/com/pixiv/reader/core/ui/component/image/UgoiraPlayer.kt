package com.pixiv.reader.core.ui.component.image

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.pixiv.reader.core.network.ugoira.UgoiraFrame
import com.pixiv.reader.core.ui.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * ugoira 双缓冲逐帧播放（后台预解码下一帧，内存峰值 2 帧 Bitmap）；
 * 仅在前台 `STARTED` 生命周期内推进帧循环，[maxDecodeSize] 为 null 时原尺寸解码。
 */
@Composable
fun UgoiraPlayer(
    frames: List<UgoiraFrame>,
    modifier: Modifier = Modifier,
    maxDecodeSize: Int? = null,
    contentScale: ContentScale = ContentScale.Fit,
    loadingContent: (@Composable () -> Unit)? = null,
) {
    var bitmap by remember(frames) { mutableStateOf<Bitmap?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(frames, lifecycleOwner) {
        if (frames.isEmpty()) {
            bitmap = null
            return@LaunchedEffect
        }
        bitmap = withContext(Dispatchers.IO) { decodeSampled(frames[0].file, maxDecodeSize) }
        if (frames.size <= 1) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var index = 0
            while (true) {
                val nextIndex = (index + 1) % frames.size
                val next = async(Dispatchers.IO) { decodeSampled(frames[nextIndex].file, maxDecodeSize) }
                val delayMs = frames[index].delayMs.coerceAtLeast(16).toLong()
                delay(delayMs.milliseconds)
                val decoded = next.await()
                if (decoded != null) {
                    bitmap = decoded
                }
                index = nextIndex
            }
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
