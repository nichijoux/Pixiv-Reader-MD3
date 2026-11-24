package com.pixiv.reader.core.ui.component.input

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** 设置导航条目数据；[accent] 为 [Color.Unspecified] 时由渲染侧回退主题 primary。 */
data class SettingsCardItem(
    val icon: ImageVector,
    val title: String,
    val description: String = "",
    val trailingIcon: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
    val accent: Color = Color.Unspecified,
    val onClick: () -> Unit,
)
