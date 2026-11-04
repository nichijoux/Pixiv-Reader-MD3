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
 * 沉浸式封面 banner：仅作背景（非完整展示），无视差，底部渐变过渡到 surface。
 *
 * hero 元素由本组件独占且恒组合：数据未到达时以 [seed]（列表卡片已缓存的 square_medium 封面）
 * 占位渲染，数据到达后同一容器原地转真图，避免「骨架 + 占位封面 → 真图 banner」的子树硬切
 * 导致 hero 销毁重建、SharedTransition 重播动画（闪现）。
 *
 * 双层渲染：底层垫 seed 封面、顶层铺真图。两者 URL 不同源
 * （seed 为 square_medium，真图为 medium 优先），真图未缓存期间顶层为空，
 * 底层 seed 全程可见，消除接棒时的加载空窗。
 *
 * @param detail 小说详情数据；null 表示数据未到达（仅渲染 seed 占位层）
 * @param seed 列表卡片写入的 hero 种子封面（可空：深链进入 / 种子已被消费）
 * @param fallbackId hero 共享元素 key 的回退作品 id（路由参数，detail 未到达时定位 key）
 * @param height banner 固定高度（手机 / 平板两种规格）
 * @return 无返回值（渲染 Composable；无任何可用 id 时不渲染）
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
            // hero：banner 与列表小说卡片封面同 key 衔接 morph（key 用作品 id 区分）
            .heroBoundsModifier(novelCoverKey(heroId)),
    ) {
        // 底层垫 seed 封面：列表已缓存，即时出图；与真图 URL 不同源，加载期由它兜住底
        val seedCoverUrl = seed?.coverUrl?.takeIf { it.isNotBlank() }
        if (seedCoverUrl != null) {
            PixivImage(
                url = seedCoverUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        // 顶层真图：数据到达后渲染（medium 优先保画质），加载完成自然覆盖底层。
        // 有 seed 垫底时占位色取透明：PixivImage 默认占位是不透明底色，会在真图加载期间盖住
        // 底层 seed，与真图 URL 不同源（medium 未缓存）时重新出现空窗；无 seed 时保留默认灰底
        if (detail != null) {
            PixivImage(
                url = detail.image_urls?.medium ?: detail.image_urls?.square_medium,
                contentDescription = detail.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholderColor = if (seedCoverUrl != null) Color.Transparent else null,
            )
        }
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
