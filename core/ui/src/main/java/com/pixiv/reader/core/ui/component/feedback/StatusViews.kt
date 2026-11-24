package com.pixiv.reader.core.ui.component.feedback

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.R
import com.pixiv.reader.core.ui.component.input.AccentIconBadge
import com.pixiv.reader.core.ui.theme.SettingAccentOrange
import com.pixiv.reader.core.ui.theme.Spacing

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ContainedLoadingIndicator()
    }
}

@Composable
private fun StateIllustration(icon: ImageVector, accent: Color = Color.Unspecified) {
    // 组合期捕获动效规格：Animatable.animateTo 不在组合上下文
    val fadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = fadeSpec)
    }
    Box(
        modifier = Modifier
            .padding(bottom = Spacing.md)
            .graphicsLayer { this.alpha = alpha.value },
        contentAlignment = Alignment.Center,
    ) {
        if (accent == Color.Unspecified) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(48.dp),
            )
        } else {
            AccentIconBadge(
                icon = icon,
                accent = accent,
                size = 64.dp,
                cornerRadius = 16.dp,
                iconSize = 32.dp,
            )
        }
    }
}

@Composable
fun ErrorBox(
    message: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = message.takeUnless { it.isNullOrBlank() } ?: stringResource(R.string.load_failed)
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StateIllustration(Icons.Outlined.CloudOff, accent = SettingAccentOrange)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = Spacing.lg)) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Text(stringResource(R.string.retry), modifier = Modifier.padding(start = Spacing.xsPlus))
        }
    }
}

@Composable
fun EmptyBox(text: String, modifier: Modifier = Modifier, accent: Color = Color.Unspecified) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StateIllustration(Icons.Outlined.Inbox, accent = accent)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
