package com.pixiv.reader.core.ui.component.feedback

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.theme.AppShapes
import com.pixiv.reader.core.ui.theme.Durations
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes

@Composable
fun skeletonPulseColor(label: String = "skeleton"): Color {
    val transition = rememberInfiniteTransition(label = label)
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = Durations.SKELETON_PULSE_MS),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "${label}Alpha",
    )
    return MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
}

@Composable
fun SkeletonBlock(modifier: Modifier, color: Color) {
    Box(modifier = modifier.background(color))
}
