package com.pixiv.reader.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 彩色风格开关（CompositionLocal 下发）。
 * true = 彩色点缀（设置页七色图标、各页彩色空态/按钮/徽标）；false = 纯 M3 单色（默认）。
 * 由 MainActivity 依 UserPreferences.colorStyleEnabled 注入，全局即时生效。
 */
val LocalColorStyleEnabled = staticCompositionLocalOf { false }

/**
 * 按全局彩色风格开关与当前明暗主题解析点缀色。
 * - 开关关闭：统一回退 `colorScheme.primary`（纯 M3 单色）。
 * - 开关开启：将七个 [SettingAccent*] 静态 token 映射到当前主题下的 [LocalExtendedColors]
 *   浅色（tone-40）/ 深色（tone-80）双档色值，保证 12%/18% 淡底上的对比度；其余色值原样返回。
 *
 * @param accent 调用方期望的点缀色（`SettingAccent*` 或语义色）
 * @return 实际生效的颜色
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
