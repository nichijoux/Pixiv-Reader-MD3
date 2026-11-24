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
import com.pixiv.reader.core.ui.component.detail.HeroSeed
import com.pixiv.reader.core.ui.component.detail.heroBoundsModifier
import com.pixiv.reader.core.ui.component.detail.novelCoverKey
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.feature.novel.R

/**
 * 沉浸式封面 banner（仅作背景，无视差），底部渐变过渡到 surface。
 * hero 元素必须由本组件独占且恒组合：seed 占位 → 同一容器原地转真图，否则子树硬切会让
 * SharedTransition 重播进入动画（闪现）。双层渲染（底层 seed / 顶层真图）消除 URL 不同源的接棒空窗。
 */
@Composable
internal fun NovelBanner(
    detail: Novel?,
    seed: HeroSeed?,
    fallbackId: Long?,
    height: Dp,
) {
    // hero 挂点需要稳定作品 id：详情数据优先，数据未到时用路由参数兜底；两者皆无则无从衔接 morph
    val heroId = detail?.id ?: fallbackId ?: return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            // 与列表卡片封面同 key 衔接 morph
            .heroBoundsModifier(novelCoverKey(heroId)),
    ) {
        // 底层垫 seed 封面：列表已缓存可即时出图，真图未缓存期间由它兜底
        val seedCoverUrl = seed?.coverUrl?.takeIf { it.isNotBlank() }
        if (seedCoverUrl != null) {
            PixivImage(
                url = seedCoverUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        // 顶层真图 medium 优先保画质；有 seed 垫底时占位色取透明，否则 PixivImage 默认不透明
        // 占位会盖住 seed，在真图未缓存时重新出现空窗
        if (detail != null) {
            PixivImage(
                url = detail.image_urls?.medium ?: detail.image_urls?.square_medium,
                contentDescription = detail.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholderColor = if (seedCoverUrl != null) Color.Transparent else null,
            )
        }
        // 顶部 scrim：悬浮白色返回按钮无圆底，需渐变保证浅色封面上可见
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

@Composable
internal fun FloatingBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onBack,
        modifier = modifier
            .statusBarsPadding()
            .padding(Spacing.xs)
            .size(Sizes.s40),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.novel_cd_back),
            tint = Color.White,
        )
    }
}

/** 正文限宽居中（banner 仍全宽沉浸）。 */
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
