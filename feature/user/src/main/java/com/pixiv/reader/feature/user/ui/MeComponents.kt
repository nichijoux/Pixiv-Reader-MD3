package com.pixiv.reader.feature.user.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.theme.AppShapes

/** 我的页区块标题。 */
@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
    )
}

/** 我的页区块间间距。 */
@Composable
internal fun SectionSpacer() {
    Spacer(Modifier.height(20.dp))
}

/** 我的页卡片间间距。 */
@Composable
internal fun CardSpacer() {
    Spacer(Modifier.height(8.dp))
}

/** 标题 + 副标题 + Switch 行（自动更新等）。 */
@Composable
internal fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * 胶囊选择按钮（主题模式 / 语言用）：全圆胶囊 + 44dp 高，文字绝对居中。
 * 选中态用主题主色 12% 透明度浅底（更浅更通透），未选中用 `surfaceContainerHigh` 弱化。
 */
@Composable
internal fun PillSelectButton(
    selected: Boolean,
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(AppShapes.pill)
            .background(container)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = content,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

/**
 * 内嵌设置卡片容器（我的页「外观 / 浏览 / 系统」各区块统一样式：
 * surfaceContainer 底 + 16dp 内边距）。
 */
@Composable
internal fun MeSettingCard(content: @Composable () -> Unit) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}
