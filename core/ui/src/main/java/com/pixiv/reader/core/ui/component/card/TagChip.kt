package com.pixiv.reader.core.ui.component.card

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.resolveAccent

@Composable
fun TagChip(
    text: String,
    onClick: (() -> Unit)?,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    accent: Color = Color.Unspecified,
) {
    val accentColor = if (accent == Color.Unspecified) null else resolveAccent(accent)
    val containerColor = if (accentColor != null) {
        val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        accentColor.copy(alpha = if (dark) 0.18f else 0.12f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = accentColor ?: MaterialTheme.colorScheme.onSecondaryContainer
    Text(
        text = text,
        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
        color = contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(AppShapes.pill)
            .background(containerColor)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(
                horizontal = if (compact) Spacing.sm else Spacing.smPlus,
                vertical = if (compact) 3.dp else Spacing.xs,
            ),
    )
}
