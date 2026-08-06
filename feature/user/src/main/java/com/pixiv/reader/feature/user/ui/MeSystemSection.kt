package com.pixiv.reader.feature.user.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pixiv.reader.feature.user.R

/**
 * SAF 初始定位 URI：内置存储 Download 文件夹。
 * 传给 [ActivityResultContracts.OpenDocumentTree] 的 EXTRA_INITIAL_URI，打开选择器时
 * 直接定位到 Download（而非存储根——Android 11+ 禁止授权存储卷根目录，会弹隐私提示）。
 */
private val DOWNLOAD_DOCUMENT_URI = Uri.parse(
    "content://com.android.externalstorage.documents/document/primary%3ADownload",
)

/** 我的页「系统设置」：自动更新 / 下载位置（SAF）/ 清除缓存。 */
@Composable
internal fun MeSystemSection(
    autoUpdate: Boolean,
    novelExportDir: String,
    cacheSize: String,
    onSetAutoUpdate: (Boolean) -> Unit,
    onPickExportDir: (android.net.Uri) -> Unit,
    onResetExportDir: () -> Unit,
    onClearCache: () -> Unit,
) {
    val context = LocalContext.current
    val exportDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) onPickExportDir(uri)
    }
    // 当前导出目录显示名（未配置 = 应用默认）
    val exportDirName = androidx.compose.runtime.remember(novelExportDir) {
        if (novelExportDir.isBlank()) {
            context.getString(R.string.me_export_dir_default)
        } else {
            androidx.documentfile.provider.DocumentFile.fromTreeUri(context, android.net.Uri.parse(novelExportDir))?.name
                ?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.me_export_dir_default)
        }
    }

    // 自动更新
    MeSettingCard {
        SettingSwitchRow(
            title = stringResource(R.string.me_auto_update),
            subtitle = stringResource(R.string.me_auto_update_desc),
            checked = autoUpdate,
            onCheckedChange = onSetAutoUpdate,
        )
    }
    CardSpacer()
    // 下载位置（小说导出目录：默认应用目录，可 SAF 指定如系统 Download）
    MeSettingCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.me_export_dir), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.me_export_dir_value, exportDirName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (novelExportDir.isNotBlank()) {
                TextButton(onClick = onResetExportDir) {
                    Text(stringResource(R.string.me_export_dir_reset))
                }
            }
        }
        OutlinedButton(
            // 初始定位到 Download：避免用户从存储根进入时被系统「保护隐私」限制拦截
            onClick = { exportDirLauncher.launch(DOWNLOAD_DOCUMENT_URI) },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.me_export_dir_pick))
        }
    }
    CardSpacer()
    // 存储：清除缓存
    MeSettingCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.me_clear_cache), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.me_cache_size, cacheSize),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onClearCache) {
                Text(stringResource(R.string.me_clear), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
