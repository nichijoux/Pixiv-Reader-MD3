package com.pixiv.reader.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * M3 扩展语义色板：收藏红、成功绿、设置点缀色等标准角色之外的业务语义色，
 * 随 [PixivReaderTheme] 自动切换 tone-40/tone-80 双档（非文本图标 ≥3:1）。
 */
@Immutable
data class PixivExtendedColors(
    val favorite: Color,
    /** [ImageScrim] 遮罩与黑底查看器上恒用高亮度浅红 */
    val favoriteOnScrim: Color,
    val success: Color,
    /** 反色容器底色与常规表面极性相反，需取反向档 */
    val successOnInverse: Color,
    val errorOnInverse: Color,
    val accentViolet: Color,
    val accentBlue: Color,
    val accentTeal: Color,
    val accentGreen: Color,
    val accentAmber: Color,
    val accentOrange: Color,
    val accentPink: Color,
)

/** 浅色主题下 `inverseSurface` 为深灰，故 `OnInverse` 取高亮度档。 */
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

/** 深色主题下 `inverseSurface` 为浅灰，故 `OnInverse` 取深色调档。 */
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

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

val MaterialTheme.extendedColors: PixivExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current
