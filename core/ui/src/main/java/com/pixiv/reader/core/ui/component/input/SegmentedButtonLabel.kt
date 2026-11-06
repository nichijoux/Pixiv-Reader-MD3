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

/**
 * 分段控件单行自适应标签：宽度不足时按步长缩小字号（14sp → 最低 9sp）而不是换行。
 * 用于分段选择等宽场景，防止长文案（如英文 "Seamless vertical"）换行把所在分段撑成两行高、
 * 整条分段选择器高低错位。仍放不下时省略号兜底。
 *
 * 供 [PillSegmentedRow]（全 App 分段控件统一实现）使用：颜色/字重按选中态显式注入。
 *
 * @param text 标签文案
 * @param modifier 外部传入的 Modifier
 * @param color 文字颜色；[Color.Unspecified] 时沿用 [LocalContentColor]（宿主未指定选中态时）
 * @param fontWeight 文字字重；null 时沿用主题 `labelLarge` 字重
 * @return 无返回值
 */
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
        // BasicText 不自动取 LocalContentColor，需随样式显式带上（选中/未选中态色由宿主注入）
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
