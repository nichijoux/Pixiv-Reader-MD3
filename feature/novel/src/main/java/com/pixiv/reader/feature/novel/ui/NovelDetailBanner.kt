package com.pixiv.reader.feature.novel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.common.ui.MAX_CONTENT_WIDTH_DP
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.feature.novel.R

/** 沉浸式封面 banner：仅作背景（非完整展示），无视差，底部渐变过渡到 surface。 */
@Composable
internal fun NovelBanner(detail: Novel, height: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        PixivImage(
            url = detail.image_urls?.medium ?: detail.image_urls?.square_medium,
            contentDescription = detail.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        // 顶部 scrim：悬浮白色返回按钮无圆底，需顶部渐变保证浅色封面上可见
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(NOVEL_BANNER_SCRIM_HEIGHT)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black.copy(alpha = 0.30f),
                            1f to Color.Transparent,
                        ),
                    ),
                ),
        )
        // 底部渐变过渡到正文背景
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(NOVEL_BANNER_GRADIENT_HEIGHT)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            1f to MaterialTheme.colorScheme.surface,
                        ),
                    ),
                ),
        )
    }
}

/** 悬浮返回按钮（沉浸式：与普通 TopAppBar 返回箭头一致的样式，白图标靠 banner 顶部 scrim 保证可见）。 */
@Composable
internal fun FloatingBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onBack,
        modifier = modifier
            .statusBarsPadding()
            .padding(4.dp)
            .size(40.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.novel_cd_back),
            tint = Color.White,
        )
    }
}

/** 平板适配：详情正文内容限宽居中（banner 保持全宽沉浸）。 */
@Composable
internal fun NovelCenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = MAX_CONTENT_WIDTH_DP.dp),
        ) {
            content()
        }
    }
}
