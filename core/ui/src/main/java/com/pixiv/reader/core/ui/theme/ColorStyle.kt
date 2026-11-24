package com.pixiv.reader.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 彩色风格开关（CompositionLocal 下发）：true = 彩色点缀，false = 纯 M3 单色（默认）。
 * 由 MainActivity 依 UserPreferences.colorStyleEnabled 注入。
 */
val LocalColorStyleEnabled = staticCompositionLocalOf { false }

/**
 * 按彩色风格开关与明暗主题解析点缀色：关闭时回退 `colorScheme.primary`，开启时把七个
 * [SettingAccent*] token 映射到 tone-40/tone-80 双档（保 12%/18% 淡底对比度）。
 */
@Composable
fun resolveAccent(accent: Color): Color {
    if (!LocalColorStyleEnabled.current) return MaterialTheme.colorScheme.primary
    val ext = LocalExtendedColors.current
    return when (accent) {
        SettingAccentViolet -> ext.accentViolet
        SettingAccentBlue -> ext.accentBlue
        SettingAccentTeal -> ext.accentTeal
        SettingAccentGreen -> ext.accentGreen
        SettingAccentAmber -> ext.accentAmber
        SettingAccentOrange -> ext.accentOrange
        SettingAccentPink -> ext.accentPink
        else -> accent
    }
}
