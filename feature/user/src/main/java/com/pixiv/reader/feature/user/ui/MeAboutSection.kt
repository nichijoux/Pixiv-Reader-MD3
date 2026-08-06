package com.pixiv.reader.feature.user.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixiv.reader.core.ui.component.SettingsCard
import com.pixiv.reader.core.ui.component.SettingsCardItem
import com.pixiv.reader.feature.user.R

/** 开源仓库地址。 */
private const val OPEN_SOURCE_URL = "https://github.com/nichijoux/Pixiv-Material"

/** 我的页「关于」：应用信息 / 开源仓库 / 检查更新。 */
@Composable
internal fun MeAboutSection(
    versionName: String,
    onCheckUpdate: () -> Unit,
) {
    val context = LocalContext.current
    MeSettingCard {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = "Pixiv Reader",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.me_version, versionName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = stringResource(R.string.me_about_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            text = stringResource(R.string.me_open_source_license),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
    CardSpacer()
    // 开源仓库
    SettingsCard(
        SettingsCardItem(
            icon = Icons.Filled.OpenInNew,
            title = stringResource(R.string.me_open_source_repo),
            trailingIcon = Icons.Filled.OpenInNew,
            onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OPEN_SOURCE_URL)))
                }
            },
        ),
    )
    CardSpacer()
    // 检查更新
    SettingsCard(
        SettingsCardItem(
            icon = Icons.Filled.SystemUpdate,
            title = stringResource(R.string.me_check_update),
            onClick = onCheckUpdate,
        ),
    )
}
