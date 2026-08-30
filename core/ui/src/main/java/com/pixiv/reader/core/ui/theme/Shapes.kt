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
    /** 6dp：微型 chip / 徽标角（小于 [small]） */
    val tiny: Shape = RoundedCornerShape(6.dp)

    /** 8dp：小控件 / 小卡片角 */
    val small: Shape = RoundedCornerShape(8.dp)

    /** 10dp：chip / 输入框角（介于 [small] 与 [card] 之间） */
    val cardSmall: Shape = RoundedCornerShape(10.dp)

    /** 12dp：默认卡片角 */
    val card: Shape = RoundedCornerShape(12.dp)

    /** 14dp：卡片角变体（介于 [card] 与 [large] 之间） */
    val cardLarge: Shape = RoundedCornerShape(14.dp)

    /** 16dp：大卡片 / 弹层角 */
    val large: Shape = RoundedCornerShape(16.dp)

    /** 全圆（药丸 / 圆形按钮） */
    val pill: Shape = RoundedCornerShape(percent = 50)

    /** 正圆 */
    val circle: Shape = CircleShape
}
