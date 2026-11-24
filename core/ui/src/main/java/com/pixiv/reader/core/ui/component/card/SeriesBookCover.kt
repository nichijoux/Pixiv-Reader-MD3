package com.pixiv.reader.core.ui.component.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.SettingAccentTeal
import com.pixiv.reader.core.ui.theme.resolveAccent

@Composable
fun SeriesBookCover(
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp,
    shape: Shape = AppShapes.card,
) {
    val accent = resolveAccent(SettingAccentTeal)
    // 深色下提高淡底透明度：12% × 高亮色叠深底色差过小，色块几乎不可见
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val containerAlpha = if (dark) 0.18f else 0.12f
    Box(
        modifier = modifier
            .clip(shape)
            .background(accent.copy(alpha = containerAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(iconSize),
        )
    }
}
