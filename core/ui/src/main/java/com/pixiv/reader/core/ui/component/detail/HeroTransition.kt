package com.pixiv.reader.core.ui.component.detail

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.pixiv.reader.core.ui.component.image.PixivImage

/** 固定短 tween 与路由 enter/exit spring 解耦：双 spring 并联会导致 bounds 拖尾后猛跳。 */
private const val HERO_BOUNDS_MOTION_MS = 200

/** 卡片点击时的封面快照，供详情加载期占位衔接 morph（aspectRatio 为 null 时按 1.5f 兜底）。 */
data class HeroSeed(
    val coverUrl: String,
    val aspectRatio: Float?,
)

/**
 * 进程级一次性 hero 种子暂存（仅主线程使用，无需加锁）。
 * 键须经 [illustCoverKey]/[novelCoverKey] 带类型前缀防串用；`consume` 取出即删，
 * 详情路由销毁时须调 `clear` 兜底清理命中缓存未消费的残留。
 */
object HeroSeedStore {

    private val seeds = mutableMapOf<String, HeroSeed>()

    fun seed(key: String, value: HeroSeed) {
        seeds[key] = value
    }

    fun consume(key: String): HeroSeed? = seeds.remove(key)

    fun clear(key: String) {
        seeds.remove(key)
    }
}

/** core:ui 不依赖 navigation-compose，经此下发根级 SharedTransitionScope；未提供时 hero 退化为无操作。 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** 详情路由 composable 提供的可见性作用域，用于跨 NavHost 与内层 Tab 卡片匹配进出场。 */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

fun illustCoverKey(id: Long): String = "illust_cover_$id"

fun novelCoverKey(id: Long): String = "novel_cover_$id"

/** 双 CompositionLocal 均可用时挂 `sharedBounds`，否则原样返回；Tab 内等非详情路由须显式传入 [animatedVisibilityScope]。 */
@Composable
fun Modifier.heroBoundsModifier(
    key: String,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val visibilityScope = animatedVisibilityScope ?: LocalNavAnimatedVisibilityScope.current
        ?: return this
    // boundsTransform 非组合上下文，须在组合期提前构造 spec
    val boundsSpec: FiniteAnimationSpec<Rect> = tween(
        durationMillis = HERO_BOUNDS_MOTION_MS,
        easing = FastOutSlowInEasing,
    )
    return with(sharedScope) {
        this@heroBoundsModifier.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = visibilityScope,
            boundsTransform = { _, _ -> boundsSpec },
        )
    }
}

/** 详情加载期一次性消费 [HeroSeed] 渲染占位衔接 morph；无种子（深链/平板直切）时不渲染。 */
@Composable
fun HeroSeedCover(
    key: String,
    modifier: Modifier = Modifier,
    height: Dp? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    // remember 缓存 consume 结果：种子取出即删，避免重组二次消费拿到 null
    val seed = remember(key) { HeroSeedStore.consume(key) } ?: return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (height != null) Modifier.height(height)
                // 1.5f 与 IllustPagePager 通用兜底一致，避免接棒高度跳变
                else Modifier.aspectRatio(seed.aspectRatio ?: 1.5f),
            )
            .heroBoundsModifier(key),
    ) {
        PixivImage(
            url = seed.coverUrl.ifBlank { null },
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
        )
    }
}
