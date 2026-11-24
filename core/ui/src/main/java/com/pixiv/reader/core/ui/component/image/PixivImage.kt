
package com.pixiv.reader.core.ui.component.image

import com.pixiv.reader.core.ui.theme.Sizes
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.size.Precision

/**
 * Pixiv 统一图片加载入口（走全局 `SingletonImageLoader`，自动注入 pximg.net Referer 避免 403）。
 * [showProgress] 开启时展示不确定进度条（Coil 2 无字节级进度）与断图占位。
 */
@Composable
fun PixivImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderColor: Color? = null,
    showProgress: Boolean = false,
) {
    val placeholder = placeholderColor ?: MaterialTheme.colorScheme.surfaceContainerHigh
    if (url == null) {
        Box(modifier = modifier.background(placeholder))
        return
    }
    val context = LocalContext.current
    // INEXACT 允许用内存缓存中更大的位图满足较小目标尺寸，避免容器宽度动画时逐帧重启解码导致闪烁
    val model = remember(context, url) {
        ImageRequest.Builder(context)
            .data(url)
            .precision(Precision.INEXACT)
            .build()
    }
    if (showProgress) {
        SubcomposeAsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            onState = { state ->
                if (state is AsyncImagePainter.State.Error) {
                    Log.w(LOG_TAG, "error: ${url.take(150)} — ${state.result.throwable}")
                }
            },
        ) {
            when (painter.state) {
                is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                is AsyncImagePainter.State.Error -> PixivImageErrorContent(placeholder)
                else -> PixivImageLoadingContent(placeholder)
            }
        }
    } else {
        val placeholderPainter = remember(placeholder) { ColorPainter(placeholder) }
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            placeholder = placeholderPainter,
            error = placeholderPainter,
            onError = { state ->
                Log.w(LOG_TAG, "error: ${url.take(150)} — ${state.result.throwable}")
            },
        )
    }
}

private const val LOG_TAG = "PixivImage"

@Composable
private fun PixivImageErrorContent(
    placeholder: Color,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(placeholder),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.BrokenImage,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(Sizes.s28),
        )
    }
}

@Composable
private fun PixivImageLoadingContent(
    placeholder: Color,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(placeholder),
    ) {
        LinearWavyProgressIndicator(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
        )
    }
}
