package com.pixiv.reader.core.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 全局形状 token。全圆口径一律用 [pill]（percent=50），禁止再出现 `999.dp` 或 `RoundedCornerShape(50)`。
 */
object AppShapes {
    val tiny: Shape = RoundedCornerShape(6.dp)
    val small: Shape = RoundedCornerShape(8.dp)
    val cardSmall: Shape = RoundedCornerShape(10.dp)
    val card: Shape = RoundedCornerShape(12.dp)
    val cardLarge: Shape = RoundedCornerShape(14.dp)
    val large: Shape = RoundedCornerShape(16.dp)
    /** 封面与卡片顶缘同角、底缘直通信息流 */
    val cardLargeTop: Shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
    val pill: Shape = RoundedCornerShape(percent = 50)
    val circle: Shape = CircleShape
}
