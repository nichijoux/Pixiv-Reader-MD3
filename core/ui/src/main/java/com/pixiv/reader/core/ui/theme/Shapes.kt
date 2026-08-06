package com.pixiv.reader.core.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * 全局形状 token。
 *
 * 统一「全圆」口径：一律用 [pill]（percent=50），禁止再出现 `999.dp` 或 `RoundedCornerShape(50)`。
 */
object AppShapes {
    /** 8dp：小控件 / 小卡片角 */
    val small: Shape = RoundedCornerShape(8.dp)

    /** 12dp：默认卡片角 */
    val card: Shape = RoundedCornerShape(12.dp)

    /** 16dp：大卡片 / 弹层角 */
    val large: Shape = RoundedCornerShape(16.dp)

    /** 全圆（药丸 / 圆形按钮） */
    val pill: Shape = RoundedCornerShape(percent = 50)

    /** 正圆 */
    val circle: Shape = CircleShape
}
