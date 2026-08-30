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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixiv.reader.core.common.config.ReaderPageMode
import com.pixiv.reader.core.common.config.ReaderThemeMode
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes
import com.pixiv.reader.feature.reader.R
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 阅读器设置面板（参考 legado-with-MD3）：
 * - 排版区两列网格：字号 Stepper / 字重下拉（细体·常规·粗体·自定义 100–900）/ 缩进·段距·行距·字距滑条
 * - 字体 / 主题（含跟随系统）/ 翻页模式 / 亮度
 */
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

            // ── 排版：两两分组一行 ──
            SectionLabel(stringResource(R.string.reader_settings_section_typography))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.smPlus)) {
                // 字号：Stepper（MD3：outlined 按钮自带边框，中间值，间距拉开）
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
                // 字重：下拉（细体/常规/粗体/自定义）
                FontWeightCard(
                    fontWeight = fontWeight,
                    onFontWeightChange = onFontWeightChange,
                    modifier = Modifier.weight(1f),
                )
            }
            // 字重自定义：跨行滑条 100~900
            val customWeight = fontWeight !in FONT_WEIGHT_PRESETS
            AnimatedVisibility(visible = customWeight) {
                TypographyCard(
                    title = stringResource(R.string.reader_settings_font_weight_custom_desc),
                    valueText = fontWeight.toString(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Slider(
                        value = fontWeight.toFloat(),
                        onValueChange = { onFontWeightChange(it.roundToInt()) },
                        valueRange = 100f..900f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .padding(horizontal = Spacing.xxs),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("100", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("900", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
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

            // ── 亮度（联动系统真实亮度；1.0 = 跟随系统）──
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

            SectionLabel(stringResource(R.string.reader_settings_section_font))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                READER_FONT_FAMILY_KEYS.forEachIndexed { index, key ->
                    SegmentedButton(
                        selected = fontFamilyKey == key,
                        onClick = { onFontFamilyChange(key) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = READER_FONT_FAMILY_KEYS.size
                        ),
                    ) { Text(stringResource(READER_FONT_FAMILY_NAME_RES[index])) }
                }
            }
            // 自定义字体：导入 / 清除
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

            SectionLabel(stringResource(R.string.reader_settings_section_theme))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                READER_THEME_NAME_RES.forEachIndexed { index, res ->
                    val mode = ReaderThemeMode.entries.getOrNull(index) ?: ReaderThemeMode.PAPER
                    SegmentedButton(
                        selected = theme == mode,
                        onClick = { onThemeChange(mode) },
                        enabled = !followSystem,
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = READER_THEME_NAME_RES.size
                        ),
                    ) { Text(stringResource(res)) }
                }
            }
            // 跟随系统深色模式
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

            SectionLabel(stringResource(R.string.reader_settings_section_page_mode))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                READER_PAGE_MODE_NAME_RES.forEachIndexed { index, res ->
                    val mode = ReaderPageMode.entries.getOrNull(index) ?: ReaderPageMode.SCROLL
                    SegmentedButton(
                        selected = pageMode == mode,
                        onClick = { onPageModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = READER_PAGE_MODE_NAME_RES.size
                        ),
                    ) { Text(stringResource(res)) }
                }
            }

            // 简繁转换（仅应用语言为中文时显示；OpenCC 转换正文文本块）
            if (showChineseConvert) {
                SectionLabel(stringResource(R.string.reader_settings_chinese_convert))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    CHINESE_CONVERT_OPTIONS.forEachIndexed { index, (value, labelRes) ->
                        SegmentedButton(
                            selected = chineseConvert == value,
                            onClick = { onChineseConvertChange(value) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = CHINESE_CONVERT_OPTIONS.size
                            ),
                        ) { Text(stringResource(labelRes)) }
                    }
                }
            }
        }
    }
}

/** 字重预设（细体 300 / 常规 400 / 粗体 700）；不在其中的视为自定义 100..900。 */
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
                Text(label, fontSize = 13.sp)
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.s18),
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
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

/** 排版卡片：标题 + 可选值 + 内容区（surfaceContainerLow 圆角卡，紧凑高度）。 */
@Composable
private fun TypographyCard(
    title: String,
    modifier: Modifier = Modifier,
    valueText: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(AppShapes.card)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = Spacing.smPlus, vertical = Spacing.xsPlus),
    ) {
        if (valueText != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    valueText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

/** 滑条卡：标题 + 值 + Slider + 范围标签（紧凑：滑条压矮、小字号）。 */
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
                fontSize = 10.sp,
            )
            Text(
                maxLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
        }
    }
}

/** 浮点值按步进拍平（避免滑条中间值抖动）。 */
private fun roundStep(value: Float, step: Float): Float =
    Math.round(value / step) * step

/** em 值格式化：0 → "0"；+0.05 → "+0.05"；-0.05 → "-0.05"。 */
private fun formatEm(value: Float): String {
    val rounded = Math.round(value * 100) / 100f
    return if (rounded == 0f) "0"
    else if (rounded > 0) "+${String.format(Locale.US, "%.2f", rounded)}"
    else String.format(Locale.US, "%.2f", rounded)
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 18.dp, bottom = Spacing.xsPlus),
    )
}

/** 简繁转换三档：(值, 文案)。0 关闭 / 1 简体→繁体 / 2 繁体→简体。 */
private val CHINESE_CONVERT_OPTIONS = listOf(
    0 to R.string.reader_settings_convert_off,
    1 to R.string.reader_settings_convert_s2t,
    2 to R.string.reader_settings_convert_t2s,
)
