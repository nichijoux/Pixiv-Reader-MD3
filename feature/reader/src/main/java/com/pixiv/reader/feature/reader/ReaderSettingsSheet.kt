package com.pixiv.reader.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 阅读器设置面板：字号 / 行距 / 字体 / 主题 / 翻页模式 / 亮度。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    fontSize: Float,
    lineHeight: Float,
    fontFamilyKey: String,
    theme: Int,
    pageMode: Int,
    brightness: Float,
    onFontSizeChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onFontFamilyChange: (String) -> Unit,
    onThemeChange: (Int) -> Unit,
    onPageModeChange: (Int) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                "阅读设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            SettingsSlider(
                "字号",
                "${fontSize.roundToInt()}sp",
                14f,
                24f,
                fontSize,
                onFontSizeChange
            )
            SettingsSlider(
                "行距",
                String.format("%.1f", lineHeight),
                1.4f,
                2.6f,
                lineHeight,
                onLineHeightChange
            )
            SettingsSlider(
                "亮度",
                "${(brightness * 100).roundToInt()}%",
                0.3f,
                1f,
                brightness,
                onBrightnessChange
            )

            SectionLabel("字体")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                READER_FONT_FAMILY_KEYS.forEachIndexed { index, key ->
                    SegmentedButton(
                        selected = fontFamilyKey == key,
                        onClick = { onFontFamilyChange(key) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = READER_FONT_FAMILY_KEYS.size
                        ),
                    ) { Text(READER_FONT_FAMILY_NAMES[index]) }
                }
            }

            SectionLabel("主题")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                READER_THEME_NAMES.forEachIndexed { index, name ->
                    SegmentedButton(
                        selected = theme == index,
                        onClick = { onThemeChange(index) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = READER_THEME_NAMES.size
                        ),
                    ) { Text(name) }
                }
            }

            SectionLabel("翻页模式")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                READER_PAGE_MODE_NAMES.forEachIndexed { index, name ->
                    SegmentedButton(
                        selected = pageMode == index,
                        onClick = { onPageModeChange(index) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = READER_PAGE_MODE_NAMES.size
                        ),
                    ) { Text(name) }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsSlider(
    label: String,
    valueText: String,
    rangeStart: Float,
    rangeEnd: Float,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.widthIn(min = 40.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = rangeStart..rangeEnd,
            modifier = Modifier.weight(1f),
        )
        Text(
            valueText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(min = 44.dp),
        )
    }
}
