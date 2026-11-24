package com.pixiv.reader.core.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.unit.IntOffset

/**
 * 导航过渡 token（全屏容器统一使用无过冲的临界阻尼 effects 规格，避免欠阻尼弹簧造成整页回弹抖动）：
 * - 普通路由级：轻量水平位移 + 底页微缩景深 + 淡入淡出
 * - Hero 详情路由级：零水平位移的容器淡入/微缩，把空间轨迹完全让给封面 sharedBounds
 * - 主 Tab 级：方向感知的 Shared Axis X 微位移 + 柔和淡入，与底栏滑块同向同步
 * - 发现/搜索 Tab：零位移纯 fade
 * 这些函数非 @Composable——NavHost 的 transition lambda 不是组合上下文，motionScheme 须由调用方在组合期取好传入。
 */
fun pixivNavEnter(motionScheme: MotionScheme): EnterTransition {
    val slideSpec = motionScheme.defaultEffectsSpec<IntOffset>()
    val fadeSpec = motionScheme.defaultEffectsSpec<Float>()
    return slideInHorizontally(animationSpec = slideSpec) { it / 5 } +
        fadeIn(animationSpec = fadeSpec)
}

fun pixivNavExit(motionScheme: MotionScheme): ExitTransition {
    // 底页轻微后退并微缩（0.96f），呈现层叠景深感而非平片硬滑
    val slideSpec = motionScheme.defaultEffectsSpec<IntOffset>()
    val scaleSpec = motionScheme.defaultEffectsSpec<Float>()
    val fadeSpec = motionScheme.defaultEffectsSpec<Float>()
    return slideOutHorizontally(animationSpec = slideSpec) { -it / 10 } +
        scaleOut(animationSpec = scaleSpec, targetScale = 0.96f) +
        fadeOut(animationSpec = fadeSpec)
}

fun pixivNavPopEnter(motionScheme: MotionScheme): EnterTransition {
    val slideSpec = motionScheme.defaultEffectsSpec<IntOffset>()
    val scaleSpec = motionScheme.defaultEffectsSpec<Float>()
    val fadeSpec = motionScheme.defaultEffectsSpec<Float>()
    return slideInHorizontally(animationSpec = slideSpec) { -it / 10 } +
        scaleIn(animationSpec = scaleSpec, initialScale = 0.96f) +
        fadeIn(animationSpec = fadeSpec)
}

fun pixivNavPopExit(motionScheme: MotionScheme): ExitTransition {
    val slideSpec = motionScheme.fastEffectsSpec<IntOffset>()
    val fadeSpec = motionScheme.fastEffectsSpec<Float>()
    return slideOutHorizontally(animationSpec = slideSpec) { it / 5 } +
        fadeOut(animationSpec = fadeSpec)
}

fun pixivHeroNavEnter(motionScheme: MotionScheme): EnterTransition {
    val scaleSpec = motionScheme.defaultEffectsSpec<Float>()
    val fadeSpec = motionScheme.defaultEffectsSpec<Float>()
    return scaleIn(animationSpec = scaleSpec, initialScale = 0.97f) +
        fadeIn(animationSpec = fadeSpec)
}

fun pixivHeroNavExit(motionScheme: MotionScheme): ExitTransition {
    // 来源页保持静止纯淡出，确保封面卡片 sharedBounds 起飞坐标不被位移或缩放扰动
    return fadeOut(animationSpec = motionScheme.defaultEffectsSpec())
}

fun pixivHeroNavPopEnter(motionScheme: MotionScheme): EnterTransition {
    // 目标来源页保持静止纯淡入，让返回飞回的封面精准落回卡片位置
    return fadeIn(animationSpec = motionScheme.defaultEffectsSpec())
}

fun pixivHeroNavPopExit(motionScheme: MotionScheme): ExitTransition {
    val scaleSpec = motionScheme.fastEffectsSpec<Float>()
    val fadeSpec = motionScheme.fastEffectsSpec<Float>()
    return scaleOut(animationSpec = scaleSpec, targetScale = 0.97f) +
        fadeOut(animationSpec = fadeSpec)
}

/** 平级主 Tab 切换（direction: +1 向右切 / -1 向左切），与底栏滑块同向微移。 */
fun pixivTabSlideEnter(motionScheme: MotionScheme, direction: Int): EnterTransition {
    val slideSpec = motionScheme.defaultEffectsSpec<IntOffset>()
    val scaleSpec = motionScheme.defaultEffectsSpec<Float>()
    val fadeSpec = motionScheme.defaultEffectsSpec<Float>()
    return slideInHorizontally(animationSpec = slideSpec) { (it * 0.05f * direction).toInt() } +
        scaleIn(animationSpec = scaleSpec, initialScale = 0.985f) +
        fadeIn(animationSpec = fadeSpec)
}

fun pixivTabSlideExit(motionScheme: MotionScheme, direction: Int): ExitTransition {
    val slideSpec = motionScheme.fastEffectsSpec<IntOffset>()
    val fadeSpec = motionScheme.fastEffectsSpec<Float>()
    return slideOutHorizontally(animationSpec = slideSpec) { (-it * 0.04f * direction).toInt() } +
        fadeOut(animationSpec = fadeSpec)
}

fun pixivTabFadeIn(motionScheme: MotionScheme): EnterTransition {
    val fadeSpec = motionScheme.defaultEffectsSpec<Float>()
    return fadeIn(animationSpec = fadeSpec)
}

fun pixivTabFadeOut(motionScheme: MotionScheme): ExitTransition {
    val fadeSpec = motionScheme.defaultEffectsSpec<Float>()
    return fadeOut(animationSpec = fadeSpec)
}
