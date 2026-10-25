package com.pixiv.reader.core.ui.component.input

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/**
 * SegmentedButton 单行自适应标签：宽度不足时按步长缩小字号（14sp → 最低 9sp）而不是换行。
 * 用于分段选择等宽场景，防止长文案（如英文 "Seamless vertical"）换行把所在分段撑成两行高、
 * 整条分段选择器高低错位。仍放不下时省略号兜底。
 *
 * @param text 标签文案
 * @param modifier 外部传入的 Modifier
 * @return 无返回值
 */
@Composable
fun SegmentedButtonLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        modifier = modifier,
        // BasicText 不自动取 LocalContentColor，需随样式显式带上（选中/未选中态色由宿主注入）
        style = MaterialTheme.typography.labelLarge.copy(color = LocalContentColor.current),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(
            minFontSize = 9.sp,
            maxFontSize = 14.sp,
            stepSize = 0.5.sp,
        ),
    )
}
