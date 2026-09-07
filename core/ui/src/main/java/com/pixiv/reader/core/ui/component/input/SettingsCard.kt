package com.pixiv.reader.core.ui.component.input

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 设置导航卡片数据（数据驱动 UI：图标 + 标题 + 描述 + 尾随动作）。
 *
 * 卡片本体的渲染由各设置页自行组装（如「我的」页用 MeRow 逐行渲染本列表）。
 *
 * @param icon 前置图标（居中于 40dp 圆形槽位，主色渲染）
 * @param title 主标题（`bodyLarge`，最多 1 行省略）
 * @param description 副描述（`bodySmall` 次级色，最多 1 行省略；空串则不显示）
 * @param trailingIcon 尾随图标（默认右箭头，可替换为开关/自定义）
 * @param onClick 卡片点击回调（导航到对应功能页或触发动作）
 */
data class SettingsCardItem(
    val icon: ImageVector,
    val title: String,
    val description: String = "",
    val trailingIcon: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
    val onClick: () -> Unit,
)
