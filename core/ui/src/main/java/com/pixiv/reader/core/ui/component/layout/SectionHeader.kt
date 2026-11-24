package com.pixiv.reader.core.ui.component.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.component.input.AccentIconBadge
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = Color.Unspecified,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = modifier.padding(start = Spacing.xs, bottom = Spacing.xsPlus),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (icon != null) {
            AccentIconBadge(
                icon = icon,
                accent = accent,
                size = Sizes.s20,
                cornerRadius = 6.dp,
                iconSize = 13.dp,
            )
        }
        Text(
            text = text,
            style = style,
            color = color,
        )
    }
}

@Composable
fun SectionSpacer() {
    Spacer(Modifier.height(20.dp))
}
