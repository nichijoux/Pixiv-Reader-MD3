package com.pixiv.reader.core.ui.component.input

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.theme.Spacing

/**
 * 设置导航卡片数据（数据驱动 UI：图标 + 标题 + 描述 + 尾随动作）。
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

/**
 * 设置导航卡片（个人中心/设置页共用，Material 主题自适应）。
 *
 * ## UI 设计方式
 * 横向 `Row`：前置图标（40dp 圆槽 + 主色 22dp 图标）+ 文本列（标题 + 描述，`weight(1f)` 占满）
 * + 尾随图标（20dp 次级色）。整卡 `surfaceContainer` 底色 + 默认圆角 + `clickable`。
 * 颜色与尺寸全部取自 `MaterialTheme`，支持深浅色主题，不硬编码。
 *
 * @param item 数据（见 [SettingsCardItem]）
 * @param modifier 外部传入的 Modifier（如 `fillMaxWidth`）
 */
@Composable
fun SettingsCard(
    item: SettingsCardItem,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = item.onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Icon(
                imageVector = item.trailingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
