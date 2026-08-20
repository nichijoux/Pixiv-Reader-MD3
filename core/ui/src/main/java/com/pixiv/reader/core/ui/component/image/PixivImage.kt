package com.pixiv.reader.core.ui.component.image

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
 * Pixiv 图片加载（全项目统一图片入口）。
 *
 * ## 设计说明
 * 使用 Coil `AsyncImage`，默认走应用级 `SingletonImageLoader`
 * （`PixivApp` 注入 `PixivRepository.imageClient`，自动带 `Referer: app-api.pixiv.net`，
 * 否则 `i.pximg.net` 返回 403）。`url` 为 null 时渲染纯色占位块。
 * 占位/错误统一用 `ColorPainter`（`surfaceContainerHigh` 或自定义色）填充，避免闪烁/灰块。
 *
 * @param url 图片 URL（`null` 显示占位色块）
 * @param contentDescription 无障碍描述
 * @param modifier 外部传入的 Modifier
 * @param contentScale 图片缩放方式（默认 `ContentScale.Crop` 裁剪填充）
 * @param placeholderColor 占位/错误背景色（null 用主题 `surfaceContainerHigh`）
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
