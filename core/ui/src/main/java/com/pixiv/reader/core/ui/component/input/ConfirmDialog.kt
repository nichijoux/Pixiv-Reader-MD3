package com.pixiv.reader.core.ui.component.input

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pixiv.reader.core.ui.R
import com.pixiv.reader.core.ui.theme.Spacing
import com.pixiv.reader.core.ui.theme.Sizes

enum class ConfirmDialogVariant(
    val defaultIcon: ImageVector,
) {
    DANGER(Icons.Filled.DeleteOutline),
    WARNING(Icons.Filled.Info),
}

/** 通用确认提示框；同时传入 [bodyContent] 与 [message] 时优先展示 [bodyContent]。 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String? = null,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ConfirmDialogVariant = ConfirmDialogVariant.DANGER,
    icon: ImageVector? = null,
    dismissText: String? = null,
    bodyContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val accent = when (variant) {
        ConfirmDialogVariant.DANGER -> MaterialTheme.colorScheme.error
        ConfirmDialogVariant.WARNING -> MaterialTheme.colorScheme.primary
    }
    val onAccent = when (variant) {
        ConfirmDialogVariant.DANGER -> MaterialTheme.colorScheme.onError
        ConfirmDialogVariant.WARNING -> MaterialTheme.colorScheme.onPrimary
    }
    val resolvedIcon = icon ?: variant.defaultIcon
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
            ) {
                Column(modifier = Modifier.padding(Spacing.xl)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(Sizes.s40)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = resolvedIcon,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(Sizes.s20),
                            )
                        }
                        Spacer(Modifier.width(Spacing.md))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (bodyContent != null) {
                        Column(
                            modifier = Modifier.padding(top = Spacing.sm),
                            content = bodyContent,
                        )
                    } else if (message != null) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.xl),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(
                                text = dismissText ?: stringResource(R.string.common_cancel),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(Spacing.sm))
                        Button(
                            onClick = onConfirm,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = onAccent,
                            ),
                        ) {
                            Text(text = confirmText, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
