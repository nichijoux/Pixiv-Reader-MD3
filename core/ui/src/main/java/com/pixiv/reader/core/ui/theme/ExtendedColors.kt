package com.pixiv.reader.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Material 3 扩展语义色板（Custom Color Roles）：
 * 承载收藏红、成功绿与设置点缀色组等 M3 标准角色之外的业务语义色，
 * 随 [PixivReaderTheme] 自动切换浅色（tone-40）/ 深色（tone-80）档，
 * 保证全场景非文本图标与浅底徽标对比度达到 WCAG 标准（≥ 3:1）。
 */
@Immutable
data class PixivExtendedColors(
    /** 常规表面（卡片/详情操作栏）的收藏选中红 */
    val favorite: Color,
    /** 深色图片遮罩（[ImageScrim] 70% 黑）及黑底全屏查看器的收藏选中红（恒为高亮度浅红） */
    val favoriteOnScrim: Color,
    /** 常规表面的成功绿 */
    val success: Color,
    /** 反色容器（`inverseSurface` 通知条）上的成功绿（随反色容器底色极性反转） */
    val successOnInverse: Color,
    /** 反色容器（`inverseSurface` 通知条）上的错误红（随反色容器底色极性反转） */
    val errorOnInverse: Color,
    /** 设置/导航点缀色（紫） */
    val accentViolet: Color,
    /** 设置/导航点缀色（蓝） */
    val accentBlue: Color,
    /** 设置/导航点缀色（青） */
    val accentTeal: Color,
    /** 设置/导航点缀色（绿） */
    val accentGreen: Color,
    /** 设置/导航点缀色（琥珀） */
    val accentAmber: Color,
    /** 设置/导航点缀色（橙） */
    val accentOrange: Color,
    /** 设置/导航点缀色（粉） */
    val accentPink: Color,
)

/** 浅色主题扩展语义色（浅色 `inverseSurface` 为深灰，故 `OnInverse` 取高亮度档）。 */
val LightExtendedColors = PixivExtendedColors(
    favorite = FavoriteLight,
    favoriteOnScrim = FavoriteDark,
    success = SuccessLight,
    successOnInverse = SuccessDark,
    errorOnInverse = DarkError,
    accentViolet = SettingAccentViolet,
    accentBlue = SettingAccentBlue,
    accentTeal = SettingAccentTeal,
    accentGreen = SettingAccentGreen,
    accentAmber = SettingAccentAmber,
    accentOrange = SettingAccentOrange,
    accentPink = SettingAccentPink,
)

/** 深色主题扩展语义色（深色 `inverseSurface` 为浅灰，故 `OnInverse` 取深色调档）。 */
val DarkExtendedColors = PixivExtendedColors(
    favorite = FavoriteDark,
    favoriteOnScrim = FavoriteDark,
    success = SuccessDark,
    successOnInverse = SuccessLight,
    errorOnInverse = Error,
    accentViolet = DarkSettingAccentViolet,
    accentBlue = DarkSettingAccentBlue,
    accentTeal = DarkSettingAccentTeal,
    accentGreen = DarkSettingAccentGreen,
    accentAmber = DarkSettingAccentAmber,
    accentOrange = DarkSettingAccentOrange,
    accentPink = DarkSettingAccentPink,
)

/** 扩展语义色板 CompositionLocal（默认浅色，由 [PixivReaderTheme] 按明暗主题提供）。 */
val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/**
 * 访问当前主题下的扩展语义色板（`MaterialTheme.extendedColors`），与 `MaterialTheme.colorScheme` 对齐。
 */
val MaterialTheme.extendedColors: PixivExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current
