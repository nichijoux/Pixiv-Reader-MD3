package com.pixiv.reader.feature.user.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pixiv.reader.core.common.config.AppLanguage
import com.pixiv.reader.core.common.config.ThemeMode
import com.pixiv.reader.feature.user.R
import com.pixiv.reader.core.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * 我的页「外观」设置：主题模式 / 动态取色 / 字号缩放 / 语言。
 * Expressive 分组面板：四项聚入单张 28dp 圆角卡，组内行用分隔线区隔。
 *
 * @param themeMode 当前主题模式
 * @param dynamicColor 当前动态取色开关
 * @param fontScale 当前全局字体缩放
 * @param appLanguage 当前应用语言（存储值）
 * @param switchingLanguage 语言切换写盘中（锁定重复触发）
 * @param onSetThemeMode 设置主题模式
 * @param onSetDynamicColor 设置动态取色
 * @param onSetFontScale 设置字体缩放（写入 DataStore 后 MainActivity 覆盖 fontScale 即时生效）
 * @param onSetAppLanguage 设置语言（参数 = 存储值 + 落盘完成回调）
 * @param onLanguageApplied 语言落盘完成回调（调用方重建 Activity 生效）
 * @return 无返回值
 */
@Composable
internal fun MeAppearanceSection(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    fontScale: Float,
    appLanguage: String,
    switchingLanguage: Boolean,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetFontScale: (Float) -> Unit,
    onSetAppLanguage: (String, () -> Unit) -> Unit,
    onLanguageApplied: () -> Unit,
) {
    MeGroupCard {
        // 主题模式：宽控件行（标题行 + 全宽分段选择）
        MeRow(icon = Icons.Filled.Palette, title = stringResource(R.string.me_theme_mode))
        val themeModes = listOf(
            ThemeMode.FOLLOW_SYSTEM to R.string.me_theme_follow_system,
            ThemeMode.LIGHT to R.string.me_theme_light,
            ThemeMode.DARK to R.string.me_theme_dark,
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md),
        ) {
            themeModes.forEachIndexed { index, (mode, labelRes) ->
                SegmentedButton(
                    selected = themeMode == mode,
                    onClick = { onSetThemeMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = themeModes.size),
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }
        MeRowDivider()
        // 动态取色（开关行：整行可点切换）
        MeRow(
            icon = Icons.Filled.AutoAwesome,
            title = stringResource(R.string.me_dynamic_color),
            subtitle = stringResource(R.string.me_dynamic_color_desc),
            trailing = { Switch(checked = dynamicColor, onCheckedChange = onSetDynamicColor) },
            onClick = { onSetDynamicColor(!dynamicColor) },
        )
        MeRowDivider()
        // 字号缩放（滑杆行：标题 + 当前百分比，滑杆全宽）
        MeRow(
            icon = Icons.Filled.FormatSize,
            title = stringResource(R.string.me_font_scale),
            subtitle = stringResource(R.string.me_font_scale_desc),
            trailing = {
                // 语言中性 token：百分比档位（80%~130%）
                Text(
                    text = "${(fontScale * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        )
        Slider(
            value = fontScale,
            onValueChange = onSetFontScale,
            valueRange = 0.8f..1.3f,
            // 六档：0.80 / 0.90 / 1.00 / 1.10 / 1.20 / 1.30
            steps = 5,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md),
        )
        MeRowDivider()
        // 语言（值行 + 下拉菜单；切换写入落盘后由调用方重建 Activity 生效）
        var languageExpanded by remember { mutableStateOf(false) }
        Box {
            MeRow(
                icon = Icons.Filled.Translate,
                title = stringResource(R.string.me_language),
                trailing = { MeValueTrailing(languageLabel(appLanguage)) },
                onClick = { languageExpanded = true },
            )
            DropdownMenu(
                expanded = languageExpanded,
                onDismissRequest = { languageExpanded = false },
            ) {
                LANG_OPTIONS.forEach { (value, labelRes) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(labelRes)) },
                        onClick = {
                            languageExpanded = false
                            // 已选语言/切换中不重复触发；写入落盘完成后再重建，避免异步写入被取消
                            if (!switchingLanguage && appLanguage != value) {
                                onSetAppLanguage(value, onLanguageApplied)
                            }
                        },
                    )
                }
            }
        }
    }
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
