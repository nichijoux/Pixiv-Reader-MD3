package com.pixiv.reader.feature.user.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pixiv.reader.core.common.config.AppLanguage
import com.pixiv.reader.core.common.config.ThemeMode
import com.pixiv.reader.feature.user.R
import com.pixiv.reader.core.ui.theme.SettingAccentBlue
import com.pixiv.reader.core.ui.theme.SettingAccentPink
import com.pixiv.reader.core.ui.theme.SettingAccentTeal
import com.pixiv.reader.core.ui.theme.SettingAccentViolet
import com.pixiv.reader.core.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * 我的页「外观」设置：主题模式 / 动态取色 / 彩色风格 / 字号缩放 / 语言。
 * Expressive 分组面板：各项聚入单张 28dp 圆角卡，行间以留白分隔（无分割线），
 * 每行前置图标带各自点缀色。
 *
 * @param themeMode 当前主题模式
 * @param dynamicColor 当前动态取色开关
 * @param colorStyleEnabled 当前彩色风格开关（false = 纯 M3 单色）
 * @param fontScale 当前全局字体缩放
 * @param appLanguage 当前应用语言（存储值）
 * @param switchingLanguage 语言切换写盘中（锁定重复触发）
 * @param onSetThemeMode 设置主题模式
 * @param onSetDynamicColor 设置动态取色
 * @param onSetColorStyleEnabled 设置彩色风格开关
 * @param onSetFontScale 设置字体缩放（写入 DataStore 后 MainActivity 覆盖 fontScale 即时生效）
 * @param onSetAppLanguage 设置语言（参数 = 存储值 + 落盘完成回调）
 * @param onLanguageApplied 语言落盘完成回调（调用方重建 Activity 生效）
 * @return 无返回值
 */
@Composable
internal fun MeAppearanceSection(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    colorStyleEnabled: Boolean,
    fontScale: Float,
    appLanguage: String,
    switchingLanguage: Boolean,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetColorStyleEnabled: (Boolean) -> Unit,
    onSetFontScale: (Float) -> Unit,
    onSetAppLanguage: (String, () -> Unit) -> Unit,
    onLanguageApplied: () -> Unit,
) {
    val selectedFontScale = snapAppearanceFontScale(fontScale)
    // 将旧版滑块写入的档间值迁到最近的档位，使预览与实际字号一致。
    LaunchedEffect(fontScale, selectedFontScale) {
        if (fontScale != selectedFontScale) onSetFontScale(selectedFontScale)
    }

    MeGroupCard {
        // 主题模式：宽控件行（标题行 + 全宽分段选择）
        val themeModes = listOf(
            ThemeMode.FOLLOW_SYSTEM to R.string.me_theme_follow_system,
            ThemeMode.LIGHT to R.string.me_theme_light,
            ThemeMode.DARK to R.string.me_theme_dark,
        )
        MeSegmentedRow(
            icon = Icons.Filled.Palette,
            title = stringResource(R.string.me_theme_mode),
            selected = themeMode,
            options = themeModes,
            accent = SettingAccentViolet,
            onSelect = onSetThemeMode,
        )
        // 动态取色（开关行：整行可点切换）
        MeSwitchRow(
            icon = Icons.Filled.AutoAwesome,
            title = stringResource(R.string.me_dynamic_color),
            subtitle = stringResource(R.string.me_dynamic_color_desc),
            checked = dynamicColor,
            accent = SettingAccentPink,
            onCheckedChange = onSetDynamicColor,
        )
        // 彩色风格（开关行）：关闭时全 App 图标/空态/徽标回退主题单色
        MeSwitchRow(
            icon = Icons.Filled.ColorLens,
            title = stringResource(R.string.me_color_style),
            subtitle = stringResource(R.string.me_color_style_desc),
            checked = colorStyleEnabled,
            accent = SettingAccentViolet,
            onCheckedChange = onSetColorStyleEnabled,
        )
        // 字号缩放（滑杆行：标题 + 当前百分比，滑杆全宽）
        MeRow(
            icon = Icons.Filled.FormatSize,
            title = stringResource(R.string.me_font_scale),
            subtitle = stringResource(R.string.me_font_scale_desc),
            accent = SettingAccentBlue,
            trailing = {
                // 语言中性 token：百分比档位（80%~130%）
                Text(
                    text = "${(selectedFontScale * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        )
        Slider(
            value = selectedFontScale,
            onValueChange = { onSetFontScale(snapAppearanceFontScale(it)) },
            valueRange = 0.8f..1.3f,
            // 四个内部刻度加两个端点，按 10% 递增。
            steps = 4,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md),
        )
        // 语言（值行 + 下拉菜单；切换写入落盘后由调用方重建 Activity 生效）
        MeDropdownRow(
            icon = Icons.Filled.Translate,
            title = stringResource(R.string.me_language),
            currentValue = languageLabel(appLanguage),
            options = LANG_OPTIONS,
            accent = SettingAccentTeal,
            onSelect = { value ->
                // 已选语言/切换中不重复触发；写入落盘完成后再重建，避免异步写入被取消
                if (!switchingLanguage && appLanguage != value) {
                    onSetAppLanguage(value, onLanguageApplied)
                }
            },
        )
    }
}

/**
 * 将字号比例对齐到 80% 至 130% 的 10% 刻度。
 *
 * @param value 存储或滑杆产生的字号比例
 * @return 对齐后的字号比例
 */
private fun snapAppearanceFontScale(value: Float): Float {
    // 异常偏好值回到 100%，避免 NaN 转整数时抛出异常。
    if (!value.isFinite()) return 1f
    return (value * 10).roundToInt().coerceIn(8, 13) / 10f
}

/** 语言选项（存储值, 显示文案）；按本地名显示（与系统语言设置一致）。 */
private val LANG_OPTIONS = listOf(
    AppLanguage.SYSTEM to R.string.me_language_follow_system,
    AppLanguage.ZH to R.string.me_language_chinese,
    AppLanguage.ZH_TW to R.string.me_language_chinese_traditional,
    AppLanguage.EN to R.string.me_language_english,
)

/** 当前语言显示名。 */
@Composable
private fun languageLabel(value: String): String =
    stringResource(LANG_OPTIONS.firstOrNull { it.first == value }?.second ?: R.string.me_language_follow_system)
