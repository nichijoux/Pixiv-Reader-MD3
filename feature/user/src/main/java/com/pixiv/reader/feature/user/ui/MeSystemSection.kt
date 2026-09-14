package com.pixiv.reader.feature.user.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.pixiv.reader.feature.user.R
import com.pixiv.reader.core.ui.theme.Spacing

/**
 * SAF 初始定位 URI：内置存储 Download 文件夹。
 * 传给 [ActivityResultContracts.OpenDocumentTree] 的 EXTRA_INITIAL_URI，打开选择器时
 * 直接定位到 Download（而非存储根——Android 11+ 禁止授权存储卷根目录，会弹隐私提示）。
 */
private val DOWNLOAD_DOCUMENT_URI = Uri.parse(
    "content://com.android.externalstorage.documents/document/primary%3ADownload",
)

/**
 * 我的页「系统设置」：自动更新 / 下载位置（SAF）/ 清除缓存。
 * Expressive 分组面板：三项聚入单张 28dp 圆角卡，组内行用分隔线区隔。
 *
 * @param autoUpdate 自动更新开关
 * @param novelExportDir 小说导出目录（SAF tree URI；空串 = 应用默认）
 * @param cacheSize 缓存占用（已格式化文案，如 "12.3 MB"）
 * @param onSetAutoUpdate 设置自动更新开关
 * @param onPickExportDir 用户经 SAF 选定目录（拿到的 tree Uri）
 * @param onResetExportDir 重置导出目录为应用默认
 * @param onClearCache 请求清除缓存（确认弹窗由调用方持有）
 * @return 无返回值
 */
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
    val exportDirName = remember(novelExportDir) {
        if (novelExportDir.isBlank()) {
            context.getString(R.string.me_export_dir_default)
        } else {
            androidx.documentfile.provider.DocumentFile.fromTreeUri(context, android.net.Uri.parse(novelExportDir))?.name
                ?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.me_export_dir_default)
        }
    }
    MeGroupCard {
        // 自动更新（开关行：整行可点切换）
        MeSwitchRow(
            icon = Icons.Filled.SystemUpdateAlt,
            title = stringResource(R.string.me_auto_update),
            subtitle = stringResource(R.string.me_auto_update_desc),
            checked = autoUpdate,
            onCheckedChange = onSetAutoUpdate,
        )
        MeRowDivider()
        // 下载位置（导航行：点击打开 SAF 选择器；已配置时尾随「重置」行内动作）
        MeRow(
            icon = Icons.Filled.FolderOpen,
            title = stringResource(R.string.me_export_dir),
            subtitle = stringResource(R.string.me_export_dir_value, exportDirName),
            subtitleMaxLines = 1,
            trailing = {
                if (novelExportDir.isNotBlank()) {
                    TextButton(onClick = onResetExportDir) {
                        Text(stringResource(R.string.me_export_dir_reset))
                    }
                }
            },
            // 初始定位到 Download：避免用户从存储根进入时被系统「保护隐私」限制拦截
            onClick = { exportDirLauncher.launch(DOWNLOAD_DOCUMENT_URI) },
        )
        MeRowDivider()
        // 清除缓存（动作行：行内「清除」与整行点击均走确认弹窗，弹窗由 MeRoute 持有）
        MeRow(
            icon = Icons.Filled.DeleteSweep,
            title = stringResource(R.string.me_clear_cache),
            subtitle = stringResource(R.string.me_cache_size, cacheSize),
            subtitleMaxLines = 1,
            trailing = {
                TextButton(onClick = onClearCache) {
                    Text(stringResource(R.string.me_clear), color = MaterialTheme.colorScheme.error)
                }
            },
            onClick = onClearCache,
        )
    }
}
