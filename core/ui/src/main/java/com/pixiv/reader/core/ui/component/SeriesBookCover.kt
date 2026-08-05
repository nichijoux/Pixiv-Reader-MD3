package com.pixiv.reader.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 系列图标容器（MD3 Filled tonal icon container 语义）。
 *
 * 系列无封面 URL，用扁平 `secondaryContainer` 圆角容器 + `MenuBook` 图标作为占位
 * （与「插画无图/用户无头像」的兜底一致），无渐变、无装饰，深/浅色自适应。
 *
 * @param modifier 外部传入的 Modifier（建议指定尺寸，如 `size(48.dp)` / `size(64.dp)`）
 * @param iconSize 图标尺寸（默认 28dp）
 * @param shape 容器圆角（默认 12dp）
 */
@Composable
fun SeriesBookCover(
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(iconSize),
        )
    }
}
