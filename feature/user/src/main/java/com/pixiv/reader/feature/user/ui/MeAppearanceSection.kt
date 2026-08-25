package com.pixiv.reader.feature.user.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.common.config.AppLanguage
import com.pixiv.reader.core.common.config.ThemeMode
import com.pixiv.reader.feature.user.R
import kotlin.math.roundToInt

/** 我的页「外观」设置：主题模式 / 动态取色 / 语言（各独立卡片）。 */
@OptIn(ExperimentalMaterial3Api::class)
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
    // 主题模式
    MeSettingCard {
        Text(
            text = stringResource(R.string.me_theme_mode),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                ThemeMode.FOLLOW_SYSTEM to R.string.me_theme_follow_system,
                ThemeMode.LIGHT to R.string.me_theme_light,
                ThemeMode.DARK to R.string.me_theme_dark,
            ).forEach { (mode, labelRes) ->
                PillSelectButton(
                    selected = themeMode == mode,
                    onClick = { onSetThemeMode(mode) },
                    text = stringResource(labelRes),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    CardSpacer()
    // 动态取色
    MeSettingCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.me_dynamic_color),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.me_dynamic_color_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = dynamicColor,
                onCheckedChange = onSetDynamicColor,
            )
        }
    }
    CardSpacer()
    // 字号缩放（滑动条；写入 DataStore 后 MainActivity 覆盖 fontScale 即时生效）
    MeSettingCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.me_font_scale),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.me_font_scale_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 语言中性 token：百分比档位（80%~130%）
            Text(
                text = "${(fontScale * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = fontScale,
            onValueChange = onSetFontScale,
            valueRange = 0.8f..1.3f,
            // 六档：0.80 / 0.90 / 1.00 / 1.10 / 1.20 / 1.30
            steps = 5,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    CardSpacer()
    // 语言（下拉选择框；切换后重建 Activity 生效）
    MeSettingCard {
        Text(
            text = stringResource(R.string.me_language),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded && !switchingLanguage,
            onExpandedChange = { if (!switchingLanguage) expanded = it },
        ) {
            OutlinedTextField(
                value = languageLabel(appLanguage),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = expanded && !switchingLanguage,
                onDismissRequest = { expanded = false },
            ) {
                LANG_OPTIONS.forEach { (value, labelRes) ->
                    DropdownMenuItem(
                        text = { Text(stringResource(labelRes)) },
                        onClick = {
                            expanded = false
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
