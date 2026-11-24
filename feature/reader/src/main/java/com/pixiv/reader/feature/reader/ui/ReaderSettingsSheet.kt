package com.pixiv.reader.feature.reader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixiv.reader.core.common.config.ReaderDualPageMode
import com.pixiv.reader.core.common.config.ReaderPageMode
import com.pixiv.reader.core.common.config.ReaderThemeMode
import com.pixiv.reader.core.ui.component.input.AccentIconBadge
import com.pixiv.reader.core.ui.component.input.PillSegmentedRow
import com.pixiv.reader.core.ui.component.layout.SectionTitle
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.SettingAccentAmber
import com.pixiv.reader.core.ui.theme.SettingAccentBlue
import com.pixiv.reader.core.ui.theme.SettingAccentGreen
import com.pixiv.reader.core.ui.theme.SettingAccentPink
import com.pixiv.reader.core.ui.theme.SettingAccentTeal
import com.pixiv.reader.core.ui.theme.SettingAccentViolet
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.feature.reader.R
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    fontSize: Float,
    lineHeight: Float,
    fontFamilyKey: String,
    fontWeight: Int,
    paragraphIndent: Int,
    paragraphSpacing: Float,
    letterSpacing: Float,
    theme: ReaderThemeMode,
    pageMode: ReaderPageMode,
    dualPageMode: ReaderDualPageMode,
    brightness: Float,
    followSystem: Boolean,
    hasCustomFont: Boolean,
    chineseConvert: Int,
    showChineseConvert: Boolean,
    onChineseConvertChange: (Int) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onFontFamilyChange: (String) -> Unit,
    onFontWeightChange: (Int) -> Unit,
    onParagraphIndentChange: (Int) -> Unit,
    onParagraphSpacingChange: (Float) -> Unit,
    onLetterSpacingChange: (Float) -> Unit,
    onThemeChange: (ReaderThemeMode) -> Unit,
    onPageModeChange: (ReaderPageMode) -> Unit,
    onDualPageModeChange: (ReaderDualPageMode) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onFollowSystemChange: (Boolean) -> Unit,
    onImportFont: () -> Unit,
    onClearFont: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lgPlus)
                .padding(bottom = 28.dp),
        ) {
            Text(
                stringResource(R.string.reader_settings_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            SectionTitle(
                text = stringResource(R.string.reader_settings_section_typography),
                modifier = Modifier.padding(top = 18.dp),
                icon = Icons.Filled.FormatSize,
                accent = SettingAccentAmber,
                style = MaterialTheme.typography.labelLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.smPlus)) {
                TypographyCard(
                    title = stringResource(R.string.reader_settings_font_size),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                    ) {
                        OutlinedIconButton(
                            onClick = { onFontSizeChange((fontSize - 1f).coerceAtLeast(14f)) },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(Sizes.s16))
                        }
                        Text(
                            "${fontSize.roundToInt()}sp",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        OutlinedIconButton(
                            onClick = { onFontSizeChange((fontSize + 1f).coerceAtMost(24f)) },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(Sizes.s16))
                        }
                    }
                }
                FontWeightCard(
                    fontWeight = fontWeight,
                    onFontWeightChange = onFontWeightChange,
                    modifier = Modifier.weight(1f),
                )
            }
            val customWeight = fontWeight !in FONT_WEIGHT_PRESETS
            AnimatedVisibility(visible = customWeight) {
                SliderCard(
                    title = stringResource(R.string.reader_settings_font_weight_custom_desc),
                    valueText = fontWeight.toString(),
                    value = fontWeight.toFloat(),
                    valueRange = 100f..900f,
                    onValueChange = { onFontWeightChange(it.roundToInt()) },
                    minLabel = "100",
                    maxLabel = "900",
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.smPlus)) {
                SliderCard(
                    title = stringResource(R.string.reader_settings_indent),
                    valueText = stringResource(R.string.reader_settings_indent_value, paragraphIndent),
                    value = paragraphIndent.toFloat(),
                    valueRange = 0f..4f,
                    steps = 3,
                    onValueChange = { onParagraphIndentChange(it.roundToInt()) },
                    minLabel = "0",
                    maxLabel = "4",
                    modifier = Modifier.weight(1f),
                )
                SliderCard(
                    title = stringResource(R.string.reader_settings_paragraph_spacing),
                    valueText = formatEm(paragraphSpacing),
                    value = paragraphSpacing,
                    valueRange = 0f..2f,
                    steps = 19,
                    onValueChange = { onParagraphSpacingChange(roundStep(it, 0.1f)) },
                    minLabel = "0",
                    maxLabel = "2em",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.smPlus)) {
                SliderCard(
                    title = stringResource(R.string.reader_settings_line_spacing),
                    valueText = String.format(Locale.US, "%.2f", lineHeight),
                    value = lineHeight,
                    valueRange = -1f..1f,
                    steps = 39,
                    onValueChange = { onLineHeightChange(roundStep(it, 0.05f)) },
                    minLabel = "-1.0",
                    maxLabel = "+1.0",
                    modifier = Modifier.weight(1f),
                )
                SliderCard(
                    title = stringResource(R.string.reader_settings_letter_spacing),
                    valueText = formatEm(letterSpacing),
                    value = letterSpacing,
                    valueRange = -0.5f..0.5f,
                    steps = 19,
                    onValueChange = { onLetterSpacingChange(roundStep(it, 0.05f)) },
                    minLabel = "-0.5",
                    maxLabel = "0.5em",
                    modifier = Modifier.weight(1f),
                )
            }

            SliderCard(
                title = stringResource(R.string.reader_settings_brightness),
                valueText = if (brightness >= 1f) {
                    stringResource(R.string.reader_settings_brightness_auto)
                } else {
                    "${(brightness * 100).roundToInt()}%"
                },
                value = brightness,
                valueRange = 0.05f..1f,
                onValueChange = onBrightnessChange,
                minLabel = "5%",
                maxLabel = stringResource(R.string.reader_settings_brightness_auto),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.smPlus),
            )

            SectionTitle(
                text = stringResource(R.string.reader_settings_section_font),
                modifier = Modifier.padding(top = 18.dp),
                icon = Icons.Filled.FontDownload,
                accent = SettingAccentBlue,
                style = MaterialTheme.typography.labelLarge,
            )
            SettingSegmentedRow(
                options = READER_FONT_FAMILY_KEYS,
                selected = fontFamilyKey,
                labelRes = { key ->
                    READER_FONT_FAMILY_NAME_RES[READER_FONT_FAMILY_KEYS.indexOf(key)]
                },
                onSelect = onFontFamilyChange,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.smPlus),
            ) {
                OutlinedButton(onClick = onImportFont, modifier = Modifier.weight(1f)) {
                    Text(if (hasCustomFont) stringResource(R.string.reader_settings_change_custom_font) else stringResource(R.string.reader_settings_import_custom_font))
                }
                if (hasCustomFont) {
                    OutlinedButton(onClick = onClearFont, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.reader_settings_clear))
                    }
                }
            }

            SectionTitle(
                text = stringResource(R.string.reader_settings_section_theme),
                modifier = Modifier.padding(top = 18.dp),
                icon = Icons.Filled.Palette,
                accent = SettingAccentViolet,
                style = MaterialTheme.typography.labelLarge,
            )
            SettingSegmentedRow(
                options = ReaderThemeMode.entries.toList(),
                selected = theme,
                enabled = { !followSystem },
                labelRes = { mode ->
                    READER_THEME_NAME_RES[ReaderThemeMode.entries.indexOf(mode)]
                },
                onSelect = onThemeChange,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.smPlus),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.reader_settings_follow_system), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.reader_settings_follow_system_desc),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = followSystem, onCheckedChange = onFollowSystemChange)
            }

            SectionTitle(
                text = stringResource(R.string.reader_settings_section_page_mode),
                modifier = Modifier.padding(top = 18.dp),
                icon = Icons.Filled.SwapVert,
                accent = SettingAccentTeal,
                style = MaterialTheme.typography.labelLarge,
            )
            SettingSegmentedRow(
                options = ReaderPageMode.entries.toList(),
                selected = pageMode,
                labelRes = { mode ->
                    READER_PAGE_MODE_NAME_RES[ReaderPageMode.entries.indexOf(mode)]
                },
                onSelect = onPageModeChange,
            )

            SectionTitle(
                text = stringResource(R.string.reader_settings_dual_page),
                modifier = Modifier.padding(top = 18.dp),
                icon = Icons.AutoMirrored.Filled.MenuBook,
                accent = SettingAccentGreen,
                style = MaterialTheme.typography.labelLarge,
            )
            SettingSegmentedRow(
                options = ReaderDualPageMode.entries.toList(),
                selected = dualPageMode,
                labelRes = { mode ->
                    READER_DUAL_PAGE_NAME_RES[ReaderDualPageMode.entries.indexOf(mode)]
                },
                onSelect = onDualPageModeChange,
            )

            if (showChineseConvert) {
                SectionTitle(
                    text = stringResource(R.string.reader_settings_chinese_convert),
                    modifier = Modifier.padding(top = 18.dp),
                    icon = Icons.Filled.Translate,
                    accent = SettingAccentPink,
                    style = MaterialTheme.typography.labelLarge,
                )
                SettingSegmentedRow(
                    options = CHINESE_CONVERT_OPTIONS.map { it.first },
                    selected = chineseConvert,
                    labelRes = { value ->
                        CHINESE_CONVERT_OPTIONS.first { it.first == value }.second
                    },
                    onSelect = onChineseConvertChange,
                )
            }
        }
    }
}

private val FONT_WEIGHT_PRESETS = intArrayOf(300, 400, 700)

@Composable
private fun FontWeightCard(
    fontWeight: Int,
    onFontWeightChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val label = when (fontWeight) {
        300 -> stringResource(R.string.reader_settings_font_weight_thin)
        400 -> stringResource(R.string.reader_settings_font_weight_normal)
        700 -> stringResource(R.string.reader_settings_font_weight_bold)
        else -> stringResource(R.string.reader_settings_font_weight_custom)
    }
    TypographyCard(
        title = stringResource(R.string.reader_settings_font_weight),
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xxs)) {
            OutlinedButton(
                onClick = { menuOpen = true },
                modifier = Modifier.fillMaxWidth().height(30.dp),
                contentPadding = PaddingValues(horizontal = Spacing.smPlus),
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.s18),
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                shape = AppShapes.large,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reader_settings_font_weight_thin)) },
                    onClick = { onFontWeightChange(300); menuOpen = false },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reader_settings_font_weight_normal)) },
                    onClick = { onFontWeightChange(400); menuOpen = false },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reader_settings_font_weight_bold)) },
                    onClick = { onFontWeightChange(700); menuOpen = false },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reader_settings_font_weight_custom)) },
                    onClick = { onFontWeightChange(500); menuOpen = false },
                )
            }
        }
    }
}

/** [icon] 留作无分组标题的独立卡备用，避免与 [SectionTitle] 形成同组双图标。 */
@Composable
private fun TypographyCard(
    title: String,
    modifier: Modifier = Modifier,
    valueText: String? = null,
    icon: ImageVector? = null,
    accent: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(AppShapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = Spacing.smPlus, vertical = Spacing.xsPlus),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (icon != null) {
                    AccentIconBadge(
                        icon = icon,
                        accent = accent,
                        size = Sizes.s28,
                        cornerRadius = 9.dp,
                        iconSize = Sizes.s16,
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (valueText != null) {
                Text(
                    valueText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        content()
    }
}

@Composable
private fun SliderCard(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    minLabel: String,
    maxLabel: String,
    modifier: Modifier = Modifier,
    steps: Int = 0,
) {
    TypographyCard(
        title = title,
        valueText = valueText,
        modifier = modifier,
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(horizontal = Spacing.xxs),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                minLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                maxLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun roundStep(value: Float, step: Float): Float =
    Math.round(value / step) * step

@Composable
private fun <T> SettingSegmentedRow(
    options: List<T>,
    selected: T,
    enabled: (T) -> Boolean = { true },
    labelRes: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    PillSegmentedRow(
        options = options.map { it to labelRes(it) },
        selected = selected,
        onSelect = onSelect,
        optionEnabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun formatEm(value: Float): String {
    val rounded = Math.round(value * 100) / 100f
    return if (rounded == 0f) "0"
    else if (rounded > 0) "+${String.format(Locale.US, "%.2f", rounded)}"
    else String.format(Locale.US, "%.2f", rounded)
}

private val CHINESE_CONVERT_OPTIONS = listOf(
    0 to R.string.reader_settings_convert_off,
    1 to R.string.reader_settings_convert_s2t,
    2 to R.string.reader_settings_convert_t2s,
)
