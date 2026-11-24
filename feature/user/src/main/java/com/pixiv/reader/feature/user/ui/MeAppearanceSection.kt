package com.pixiv.reader.feature.user.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Dock
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
import com.pixiv.reader.core.common.config.BottomNavBarStyle
import com.pixiv.reader.core.common.config.ThemeMode
import com.pixiv.reader.feature.user.R
import com.pixiv.reader.core.ui.theme.SettingAccentBlue
import com.pixiv.reader.core.ui.theme.SettingAccentOrange
import com.pixiv.reader.core.ui.theme.SettingAccentPink
import com.pixiv.reader.core.ui.theme.SettingAccentTeal
import com.pixiv.reader.core.ui.theme.SettingAccentViolet
import com.pixiv.reader.core.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * 语言切换必须等落盘完成（onSetAppLanguage 回调）才能重建 Activity，否则异步写入会被取消。
 */
@Composable
internal fun MeAppearanceSection(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    colorStyleEnabled: Boolean,
    bottomNavBarStyle: BottomNavBarStyle,
    fontScale: Float,
    appLanguage: String,
    switchingLanguage: Boolean,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetColorStyleEnabled: (Boolean) -> Unit,
    onSetBottomNavBarStyle: (BottomNavBarStyle) -> Unit,
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
        MeSwitchRow(
            icon = Icons.Filled.AutoAwesome,
            title = stringResource(R.string.me_dynamic_color),
            subtitle = stringResource(R.string.me_dynamic_color_desc),
            checked = dynamicColor,
            accent = SettingAccentPink,
            onCheckedChange = onSetDynamicColor,
        )
        // 关闭时全 App 图标/空态/徽标回退主题单色（各组件经 resolveAccent 解析）
        MeSwitchRow(
            icon = Icons.Filled.ColorLens,
            title = stringResource(R.string.me_color_style),
            subtitle = stringResource(R.string.me_color_style_desc),
            checked = colorStyleEnabled,
            accent = SettingAccentViolet,
            onCheckedChange = onSetColorStyleEnabled,
        )
        // 仅手机端生效（平板为左侧栏）
        val navBarStyles = listOf(
            BottomNavBarStyle.DOCKED to R.string.me_nav_bar_style_docked,
            BottomNavBarStyle.FLOATING_CAPSULE to R.string.me_nav_bar_style_floating,
        )
        MeSegmentedRow(
            icon = Icons.Filled.Dock,
            title = stringResource(R.string.me_nav_bar_style),
            subtitle = stringResource(R.string.me_nav_bar_style_desc),
            selected = bottomNavBarStyle,
            options = navBarStyles,
            accent = SettingAccentOrange,
            onSelect = onSetBottomNavBarStyle,
        )
        MeRow(
            icon = Icons.Filled.FormatSize,
            title = stringResource(R.string.me_font_scale),
            subtitle = stringResource(R.string.me_font_scale_desc),
            accent = SettingAccentBlue,
            trailing = {
                // 语言中性 token：百分比档位保留内联，不走 strings.xml
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
        MeDropdownRow(
            icon = Icons.Filled.Translate,
            title = stringResource(R.string.me_language),
            currentValue = languageLabel(appLanguage),
            options = LANG_OPTIONS,
            accent = SettingAccentTeal,
            onSelect = { value ->
                // 切换中/同值不重复触发
                if (!switchingLanguage && appLanguage != value) {
                    onSetAppLanguage(value, onLanguageApplied)
                }
            },
        )
    }
}

private fun snapAppearanceFontScale(value: Float): Float {
    // 异常偏好值回到 100%，避免 NaN 转整数时抛出异常。
    if (!value.isFinite()) return 1f
    return (value * 10).roundToInt().coerceIn(8, 13) / 10f
}

/** 语言选项按本地名显示（与系统语言设置一致），不跟随界面语言翻译。 */
private val LANG_OPTIONS = listOf(
    AppLanguage.SYSTEM to R.string.me_language_follow_system,
    AppLanguage.ZH to R.string.me_language_chinese,
    AppLanguage.ZH_TW to R.string.me_language_chinese_traditional,
    AppLanguage.EN to R.string.me_language_english,
)

@Composable
private fun languageLabel(value: String): String =
    stringResource(LANG_OPTIONS.firstOrNull { it.first == value }?.second ?: R.string.me_language_follow_system)
