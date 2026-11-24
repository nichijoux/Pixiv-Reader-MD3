package com.pixiv.reader.core.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.unit.IntOffset

/**
 * 导航过渡 token：路由级 = 轻量水平位移 + 淡入淡出，Tab 级 = 纯 fade。
 * Tab 严禁位移：首页 ↔ 发现页搜索框 sharedBounds hero 依赖容器纯 fade 让位。
 * 这些函数非 @Composable——NavHost 的 transition lambda 不是组合上下文，motionScheme 须由调用方在组合期取好传入。
 */
fun pixivNavEnter(motionScheme: MotionScheme): EnterTransition {
    val slideSpec = motionScheme.defaultSpatialSpec<IntOffset>()
    val fadeSpec = motionScheme.fastEffectsSpec<Float>()
    return slideInHorizontally(animationSpec = slideSpec) { it / 4 } +
        fadeIn(animationSpec = fadeSpec)
}

fun pixivNavExit(motionScheme: MotionScheme): ExitTransition {
    // 退场用 fast spatial 快吸附，避免与进入弹簧抢节奏
    val slideSpec = motionScheme.fastSpatialSpec<IntOffset>()
    val fadeSpec = motionScheme.fastEffectsSpec<Float>()
    return slideOutHorizontally(animationSpec = slideSpec) { -it / 8 } +
        fadeOut(animationSpec = fadeSpec)
}

fun pixivNavPopEnter(motionScheme: MotionScheme): EnterTransition {
    val slideSpec = motionScheme.defaultSpatialSpec<IntOffset>()
    val fadeSpec = motionScheme.fastEffectsSpec<Float>()
    return slideInHorizontally(animationSpec = slideSpec) { -it / 8 } +
        fadeIn(animationSpec = fadeSpec)
}

fun pixivNavPopExit(motionScheme: MotionScheme): ExitTransition {
    val slideSpec = motionScheme.fastSpatialSpec<IntOffset>()
    val fadeSpec = motionScheme.fastEffectsSpec<Float>()
    return slideOutHorizontally(animationSpec = slideSpec) { it / 4 } +
        fadeOut(animationSpec = fadeSpec)
}

fun pixivTabFadeIn(motionScheme: MotionScheme): EnterTransition {
    val fadeSpec = motionScheme.fastEffectsSpec<Float>()
    return fadeIn(animationSpec = fadeSpec)
}

fun pixivTabFadeOut(motionScheme: MotionScheme): ExitTransition {
    val fadeSpec = motionScheme.fastEffectsSpec<Float>()
    return fadeOut(animationSpec = fadeSpec)
}
