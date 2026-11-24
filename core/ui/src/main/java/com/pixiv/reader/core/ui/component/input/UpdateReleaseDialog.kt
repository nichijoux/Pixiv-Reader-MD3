package com.pixiv.reader.core.ui.component.input

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.network.update.AppRelease
import com.pixiv.reader.core.ui.component.text.ChangelogMarkdownText

@Composable
fun UpdateReleaseDialog(
    release: AppRelease,
    title: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    ConfirmDialog(
        title = title,
        confirmText = confirmText,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        variant = ConfirmDialogVariant.WARNING,
        dismissText = dismissText,
        bodyContent = {
            if (release.body.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    ChangelogMarkdownText(release.body)
                }
            }
        },
    )
}
