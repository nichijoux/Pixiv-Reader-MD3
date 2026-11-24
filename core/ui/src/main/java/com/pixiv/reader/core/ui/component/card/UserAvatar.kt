package com.pixiv.reader.core.ui.component.card

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import com.pixiv.reader.core.ui.component.image.PixivImage
import com.pixiv.reader.core.ui.theme.resolveAccent

@Composable
fun UserAvatar(
    name: String?,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.foundation.shape.CircleShape,
    onClick: (() -> Unit)? = null,
    accent: Color = Color.Unspecified,
) {
    val clickModifier = if (onClick != null) modifier.clip(shape).clickable(onClick = onClick) else modifier
    if (!avatarUrl.isNullOrBlank()) {
        PixivImage(
            url = avatarUrl,
            contentDescription = name,
            modifier = clickModifier.clip(shape),
        )
    } else {
        val custom = accent != Color.Unspecified
        val accentColor = if (custom) resolveAccent(accent) else MaterialTheme.colorScheme.primaryContainer
        // 深色下提高淡底透明度，避免色块不可见
        val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val containerColor = if (custom) {
            accentColor.copy(alpha = if (dark) 0.18f else 0.12f)
        } else {
            accentColor
        }
        val contentColor = if (custom) accentColor else MaterialTheme.colorScheme.onPrimaryContainer
        Box(
            modifier = clickModifier
                .clip(shape)
                .background(containerColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name?.firstOrNull()?.toString() ?: "?",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}
