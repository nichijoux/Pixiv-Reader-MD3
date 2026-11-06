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
 * 按全局彩色风格开关解析点缀色。
 * 开关开启返回原 accent；关闭统一回退 colorScheme.primary（纯 M3 单色）。
 * 所有彩色组件必须经此函数取色，调用方无需读开关。
 *
 * @param accent 调用方期望的点缀色（SettingAccent* 或语义色）
 * @return 实际生效的颜色
 */
@Composable
fun resolveAccent(accent: Color): Color =
    if (LocalColorStyleEnabled.current) accent else MaterialTheme.colorScheme.primary
