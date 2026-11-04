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
 * 全 App 导航过渡 token：路由级（全屏页）水平位移 + 淡入淡出，Tab 级纯淡入淡出。
 *
 * 容器变换语义（以「前进」为正向）：
 * - 新页自右侧 1/4 屏宽滑入并淡入——只做局部让位，避免整屏平移的沉重感；
 * - 老页向左 1/8 屏宽退让并淡出——形成前后层级而非平面对切；
 * - pop（返回）方向全部镜像：下层页自左侧 1/8 回位，顶层页向右 1/4 退场。
 *
 * 时长与曲线一律取自 [MotionScheme]（`MotionScheme.expressive()`，见 Theme.kt）：
 * 位移走 spatial 弹簧规格、透明度走 effects 快档，本文件禁止出现 tween 等固定时长。
 *
 * 说明：这些函数**非 @Composable**——NavHost 的 `enterTransition` / `exitTransition` 等
 * lambda 不是组合上下文，无法在其中读取 `MaterialTheme.motionScheme`，故由调用方在组合期
 * 取好 motionScheme 后作为参数传入。
 */

/**
 * 路由「前进」进入过渡：自右侧 1/4 屏宽滑入 + 淡入。
 *
 * @param motionScheme 当前主题的动效方案（组合期取自 `MaterialTheme.motionScheme`）
 * @return 供 NavHost `enterTransition` 使用的进入过渡
 */
fun pixivNavEnter(motionScheme: MotionScheme): EnterTransition {
    // 位移走 spatial 弹簧（进入带弹性），透明度走 effects 快档
    val slideSpec = motionScheme.defaultSpatialSpec<IntOffset>()
    val fadeSpec = motionScheme.fastEffectsSpec<Float>()
    return slideInHorizontally(animationSpec = slideSpec) { it / 4 } +
        fadeIn(animationSpec = fadeSpec)
}

/**
 * 路由「前进」退出过渡：向左 1/8 屏宽让位 + 淡出。
 *
 * @param motionScheme 当前主题的动效方案（组合期取自 `MaterialTheme.motionScheme`）
 * @return 供 NavHost `exitTransition` 使用的退出过渡
 */
fun pixivNavExit(motionScheme: MotionScheme): ExitTransition {
    // 退场用 fast spatial 快吸附，避免与进入弹簧抢节奏
    val slideSpec = motionScheme.fastSpatialSpec<IntOffset>()
    val fadeSpec = motionScheme.fastEffectsSpec<Float>()
    return slideOutHorizontally(animationSpec = slideSpec) { -it / 8 } +
        fadeOut(animationSpec = fadeSpec)
}

/**
 * 路由 pop 进入过渡：下层页自左侧 1/8 屏宽回位 + 淡入（[pixivNavExit] 的镜像）。
 *
 * @param motionScheme 当前主题的动效方案（组合期取自 `MaterialTheme.motionScheme`）
 * @return 供 NavHost `popEnterTransition` 使用的进入过渡
 */
fun pixivNavPopEnter(motionScheme: MotionScheme): EnterTransition {
    val slideSpec = motionScheme.defaultSpatialSpec<IntOffset>()
    val fadeSpec = motionScheme.fastEffectsSpec<Float>()
    return slideInHorizontally(animationSpec = slideSpec) { -it / 8 } +
        fadeIn(animationSpec = fadeSpec)
}

/**
 * 路由 pop 退出过渡：顶层页向右 1/4 屏宽退场 + 淡出（[pixivNavEnter] 的镜像）。
 *
 * @param motionScheme 当前主题的动效方案（组合期取自 `MaterialTheme.motionScheme`）
 * @return 供 NavHost `popExitTransition` 使用的退出过渡
 */
fun pixivNavPopExit(motionScheme: MotionScheme): ExitTransition {
    val slideSpec = motionScheme.fastSpatialSpec<IntOffset>()
    val fadeSpec = motionScheme.fastEffectsSpec<Float>()
    return slideOutHorizontally(animationSpec = slideSpec) { it / 4 } +
        fadeOut(animationSpec = fadeSpec)
}

/**
 * Tab 级进入过渡：纯淡入，无水平位移。
 *
 * Tab 切换是同位层级替换（含首页 ↔ 发现页的搜索框 sharedBounds hero），
 * 容器只做快速淡入让位，位移交由共享元素自身承担。
 *
 * @param motionScheme 当前主题的动效方案（组合期取自 `MaterialTheme.motionScheme`）
 * @return 供内层 NavHost `enterTransition` 使用的进入过渡
 */
fun pixivTabFadeIn(motionScheme: MotionScheme): EnterTransition {
    val fadeSpec = motionScheme.fastEffectsSpec<Float>()
    return fadeIn(animationSpec = fadeSpec)
}

/**
 * Tab 级退出过渡：纯淡出，无水平位移（与 [pixivTabFadeIn] 同档规格）。
 *
 * @param motionScheme 当前主题的动效方案（组合期取自 `MaterialTheme.motionScheme`）
 * @return 供内层 NavHost `exitTransition` 使用的退出过渡
 */
fun pixivTabFadeOut(motionScheme: MotionScheme): ExitTransition {
    val fadeSpec = motionScheme.fastEffectsSpec<Float>()
    return fadeOut(animationSpec = fadeSpec)
}
