
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import coil.size.Precision

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
 * @param showProgress 加载反馈开关（默认 false 保持纯占位）：加载中在占位块底部显示
 *   不确定进度条、加载失败显示断图图标——阅读器等需要明确「正在加载/加载失败」反馈的场景开启。
 *   注意 Coil 2 不暴露字节级下载进度，进度条为不确定动画（能区分加载中 vs 卡死，无百分比）。
 *
 * 加载失败时统一打 `Log.w`（TAG "PixivImage"，带 URL 与异常，可区分 403/超时/协议串解析失败）；
 * 成功/加载中不打日志，避免列表页每张图刷屏。
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
    // INEXACT 精度：允许用内存缓存中更大的已解码位图满足较小目标尺寸（Crop 下视觉无差）——
    // 容器宽度动画（Master-Detail 重排等）时尺寸逐帧变化，避免每帧重启解码导致卡顿/闪烁
    val model = ImageRequest.Builder(LocalContext.current)
        .data(url)
        .precision(Precision.INEXACT)
        .build()
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
                // Empty（请求未开始）/ Loading：占位 + 底部不确定进度条
                else -> PixivImageLoadingContent(placeholder)
            }
        }
    } else {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            placeholder = ColorPainter(placeholder),
            error = ColorPainter(placeholder),
            onError = { state ->
                Log.w(LOG_TAG, "error: ${url.take(150)} — ${state.result.throwable}")
            },
        )
    }
}

private const val LOG_TAG = "PixivImage"

/** 加载失败覆盖层：占位背景上居中显示断图图标（[PixivImage.showProgress] 路径）。 */
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

/** 加载中覆盖层：占位背景上底部显示不确定进度条（[PixivImage.showProgress] 路径）。 */
@Composable
private fun PixivImageLoadingContent(
    placeholder: Color,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(placeholder),
    ) {
        LinearProgressIndicator(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
        )
    }
}
