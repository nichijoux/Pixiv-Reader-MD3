package com.pixiv.reader.core.ui.component.input

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/** 分段控件单行自适应标签：窄宽时逐级缩字（14sp → 9sp）防换行撑高轨道，[color] 未指定时沿用 [LocalContentColor]。 */
@Composable
fun SegmentedButtonLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        // BasicText 不自动读取 LocalContentColor，须显式写入 style
        style = MaterialTheme.typography.labelLarge.copy(
            color = if (color == Color.Unspecified) LocalContentColor.current else color,
            fontWeight = fontWeight ?: MaterialTheme.typography.labelLarge.fontWeight,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(
            minFontSize = 9.sp,
            maxFontSize = 14.sp,
            stepSize = 0.5.sp,
        ),
    )
}
