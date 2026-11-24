package com.pixiv.reader.feature.user.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pixiv.reader.feature.user.R
import com.pixiv.reader.core.ui.theme.SettingAccentAmber
import com.pixiv.reader.core.ui.theme.SettingAccentBlue
import com.pixiv.reader.core.ui.theme.SettingAccentGreen
import com.pixiv.reader.core.ui.theme.SettingAccentTeal
import com.pixiv.reader.core.ui.theme.Spacing

/**
 * 选择器直接定位到 Download：Android 11+ 禁止授权存储卷根目录（会弹隐私提示拦下选择）。
 */
private val DOWNLOAD_DOCUMENT_URI = Uri.parse(
    "content://com.android.externalstorage.documents/document/primary%3ADownload",
)

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
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationStatus by remember { mutableStateOf(readNotificationStatus(context)) }
    // 从系统通知/实况权限页返回时自动刷新状态文案
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationStatus = readNotificationStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationStatus = readNotificationStatus(context)
        if (!granted) {
            openNotificationSettings(context)
        }
    }
    val exportDirLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) onPickExportDir(uri)
    }
    val exportDirName = remember(novelExportDir) {
        if (novelExportDir.isBlank()) {
            context.getString(R.string.me_export_dir_default)
        } else {
            androidx.documentfile.provider.DocumentFile.fromTreeUri(context, android.net.Uri.parse(novelExportDir))?.name
                ?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.me_export_dir_default)
        }
    }
    val (notificationsEnabled, liveUpdatesEnabled) = notificationStatus
    val notificationSubtitle = when {
        !notificationsEnabled -> stringResource(R.string.me_download_notif_disabled)
        Build.VERSION.SDK_INT >= 36 && liveUpdatesEnabled ->
            stringResource(R.string.me_download_notif_live_enabled)
        Build.VERSION.SDK_INT >= 36 ->
            stringResource(R.string.me_download_notif_live_disabled)
        else -> stringResource(R.string.me_download_notif_enabled)
    }
    MeGroupCard {
        MeSwitchRow(
            icon = Icons.Filled.SystemUpdateAlt,
            title = stringResource(R.string.me_auto_update),
            subtitle = stringResource(R.string.me_auto_update_desc),
            checked = autoUpdate,
            accent = SettingAccentGreen,
            onCheckedChange = onSetAutoUpdate,
        )
        MeRow(
            icon = Icons.Filled.NotificationsActive,
            title = stringResource(R.string.me_download_notifications),
            subtitle = notificationSubtitle,
            subtitleMaxLines = 1,
            accent = SettingAccentTeal,
            trailing = { MeArrowTrailing() },
            onClick = {
                val postGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !postGranted ->
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    !notificationsEnabled ->
                        openNotificationSettings(context)
                    Build.VERSION.SDK_INT >= 36 && !liveUpdatesEnabled ->
                        openPromotedNotificationSettings(context)
                    else ->
                        openNotificationSettings(context)
                }
            },
        )
        MeRow(
            icon = Icons.Filled.FolderOpen,
            title = stringResource(R.string.me_export_dir),
            subtitle = stringResource(R.string.me_export_dir_value, exportDirName),
            subtitleMaxLines = 1,
            accent = SettingAccentBlue,
            trailing = {
                if (novelExportDir.isNotBlank()) {
                    TextButton(onClick = onResetExportDir) {
                        Text(stringResource(R.string.me_export_dir_reset))
                    }
                }
            },
            // 避免从存储根进入时被系统「保护隐私」限制拦截
            onClick = { exportDirLauncher.launch(DOWNLOAD_DOCUMENT_URI) },
        )
        // 确认弹窗由 MeRoute 持有，行内按钮与整行点击走同一条路径
        MeRow(
            icon = Icons.Filled.DeleteSweep,
            title = stringResource(R.string.me_clear_cache),
            subtitle = stringResource(R.string.me_cache_size, cacheSize),
            subtitleMaxLines = 1,
            accent = SettingAccentAmber,
            trailing = {
                TextButton(onClick = onClearCache) {
                    Text(stringResource(R.string.me_clear), color = MaterialTheme.colorScheme.error)
                }
            },
            onClick = onClearCache,
        )
    }
}

private fun readNotificationStatus(context: Context): Pair<Boolean, Boolean> {
    val postGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    val compat = NotificationManagerCompat.from(context)
    val enabled = postGranted && compat.areNotificationsEnabled()
    val liveEnabled = enabled && Build.VERSION.SDK_INT >= 36 && compat.canPostPromotedNotifications()
    return enabled to liveEnabled
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
    runCatching { context.startActivity(intent) }
}

/** ROM 可能未实现实况通知开关页，启动失败就回退到普通通知管理页。 */
private fun openPromotedNotificationSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= 36) {
        val promoIntent = Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        if (runCatching { context.startActivity(promoIntent) }.isSuccess) return
    }
    openNotificationSettings(context)
}
