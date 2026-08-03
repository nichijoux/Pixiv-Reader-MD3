package com.pixiv.reader.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/**
 * Pixiv 图片：默认使用应用级 SingletonImageLoader（自动带 Referer 头）。
 */
@Composable
fun PixivImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderColor: Color? = null,
) {
    val placeholder = placeholderColor ?: MaterialTheme.colorScheme.surfaceContainerHigh
    if (url == null) {
        Box(modifier = modifier.background(placeholder))
        return
    }
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        placeholder = ColorPainter(placeholder),
        error = ColorPainter(placeholder),
    )
}
