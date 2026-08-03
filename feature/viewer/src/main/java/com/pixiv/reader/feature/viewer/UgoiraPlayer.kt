package com.pixiv.reader.feature.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * ugoira 动图播放：按 frames[].delay 逐帧解码显示。
 * 逐帧解码（而非全部载入），控制内存。
 */
@Composable
fun UgoiraPlayer(
    frames: List<UgoiraFrame>,
    modifier: Modifier = Modifier,
) {
    var index by remember { mutableStateOf(0) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(frames, index) {
        if (frames.isEmpty()) return@LaunchedEffect
        val frame = frames[index % frames.size]
        bitmap = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(frame.file.absolutePath)
        }
        delay(frame.delayMs.toLong())
        index = (index + 1) % frames.size
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val current = bitmap
        if (current != null) {
            Image(
                bitmap = current.asImageBitmap(),
                contentDescription = "动图",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text("动图加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
