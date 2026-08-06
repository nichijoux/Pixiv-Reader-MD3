package com.pixiv.reader.feature.user.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.common.AppLanguage
import com.pixiv.reader.core.common.ThemeMode
import com.pixiv.reader.feature.user.R

/** 我的页「外观」设置：主题模式 / 动态取色 / 语言（各独立卡片）。 */
@Composable
internal fun MeAppearanceSection(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    appLanguage: String,
    switchingLanguage: Boolean,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
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
    // 语言（切换后重建 Activity 生效）
    MeSettingCard {
        Text(
            text = stringResource(R.string.me_language),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                AppLanguage.SYSTEM to R.string.me_language_follow_system,
                AppLanguage.ZH to R.string.me_language_chinese,
                AppLanguage.EN to R.string.me_language_english,
            ).forEach { (value, labelRes) ->
                PillSelectButton(
                    selected = appLanguage == value,
                    enabled = !switchingLanguage,
                    onClick = {
                        // 已选语言/切换中不重复触发；写入落盘完成后再重建，避免异步写入被取消
                        if (!switchingLanguage && appLanguage != value) {
                            onSetAppLanguage(value, onLanguageApplied)
                        }
                    },
                    text = stringResource(labelRes),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
