package com.pixiv.reader.app.download

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pixiv.reader.app.MainActivity
import com.pixiv.reader.app.R
import com.pixiv.reader.core.database.dao.DownloadStatusRow
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** 下载通知去重键，与数据库复合主键一致。 */
data class DownloadTaskKey(
    val targetType: String,
    val targetId: Long,
    val format: String,
    val scopeKey: String,
) {
    constructor(row: DownloadStatusRow) : this(
        targetType = row.targetType,
        targetId = row.targetId,
        format = row.format,
        scopeKey = row.scopeKey,
    )
}

data class DownloadProgressState(
    val activeCount: Int,
    val downloadingCount: Int,
    val pendingCount: Int,
    val primaryTitle: String,
    val progress: Int,
    val indeterminate: Boolean,
)

/** 隔离 Android 通知 API，供 [com.pixiv.reader.app.DownloadCompletionNotifier] 单测替换。 */
interface DownloadNotificationSink {
    fun ensureChannels()
    fun showProgress(state: DownloadProgressState)
    fun cancelProgress()
    fun showResult(key: DownloadTaskKey, title: String, isSuccess: Boolean)
}

/**
 * 系统下载通知管理器（兼容 Android 16 Live Updates，低版本自动降级为标准进度条）。
 * Android 16 实况提升要求进度渠道 importance >= DEFAULT 且禁用 RemoteViews/colorized/groupSummary。
 */
@Singleton
class DownloadNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DownloadNotificationSink {
    private val compatManager: NotificationManagerCompat
        get() = NotificationManagerCompat.from(context)

    override fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val res = localizedContext().resources

        // Live Updates 要求 importance >= DEFAULT，改为关闭声音与震动保持静默
        val progressChannel = NotificationChannel(
            CHANNEL_PROGRESS,
            res.getString(R.string.download_channel_progress_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = res.getString(R.string.download_channel_progress_desc)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
        }

        val resultChannel = NotificationChannel(
            CHANNEL_RESULT,
            res.getString(R.string.download_channel_result_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = res.getString(R.string.download_channel_result_desc)
        }

        manager.createNotificationChannels(listOf(progressChannel, resultChannel))
    }

    fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return compatManager.areNotificationsEnabled()
    }

    // canPostNotifications() 已在入口处校验 POST_NOTIFICATIONS 运行时权限与通知总开关
    @SuppressLint("MissingPermission")
    override fun showProgress(state: DownloadProgressState) {
        if (!canPostNotifications()) return
        ensureChannels()

        val res = localizedContext().resources
        val clampedProgress = state.progress.coerceIn(0, 100)
        val fallbackTitle = res.getString(R.string.download_notif_fallback_title)
        val displayTitle = state.primaryTitle.ifBlank { fallbackTitle }

        val (title, contentText) = if (state.activeCount <= 1) {
            if (state.indeterminate) {
                res.getString(R.string.download_notif_pending_single, displayTitle) to
                    res.getString(R.string.download_notif_waiting_network)
            } else {
                res.getString(R.string.download_notif_downloading_single, displayTitle) to
                    "$clampedProgress%"
            }
        } else {
            if (state.indeterminate) {
                res.getString(R.string.download_notif_pending_multi, state.activeCount) to
                    res.getString(R.string.download_notif_waiting_network)
            } else {
                res.getString(R.string.download_notif_downloading_multi, state.downloadingCount) to
                    res.getString(R.string.download_notif_multi_detail, displayTitle, clampedProgress)
            }
        }

        val shortCriticalText = if (state.indeterminate) {
            res.getString(R.string.download_notif_short_pending)
        } else {
            "$clampedProgress%"
        }
        val cancelLabel = res.getString(
            if (state.activeCount > 1) {
                R.string.download_notif_action_cancel_all
            } else {
                R.string.download_notif_action_cancel
            },
        )

        // API < 36 在 ProgressStyle.apply() 内部自动降级为标准进度条
        val progressStyle = NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .addProgressSegment(NotificationCompat.ProgressStyle.Segment(100))
            .setProgressIndeterminate(state.indeterminate)
        if (!state.indeterminate) {
            progressStyle.setProgress(clampedProgress)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(contentText)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(shortCriticalText)
            .setStyle(progressStyle)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openDownloadsPendingIntent())
            .addAction(0, cancelLabel, cancelPendingIntent())
            .build()

        runCatching {
            compatManager.notify(NOTIFICATION_ID_PROGRESS, notification)
        }.onFailure { e ->
            Log.w(TAG, "发送下载进度通知失败", e)
        }
    }

    override fun cancelProgress() {
        runCatching {
            compatManager.cancel(NOTIFICATION_ID_PROGRESS)
        }.onFailure { e ->
            Log.w(TAG, "撤销下载进度通知失败", e)
        }
    }

    // canPostNotifications() 已在入口处校验 POST_NOTIFICATIONS 运行时权限与通知总开关
    @SuppressLint("MissingPermission")
    override fun showResult(key: DownloadTaskKey, title: String, isSuccess: Boolean) {
        if (!canPostNotifications()) return
        ensureChannels()

        val res = localizedContext().resources
        val icon = if (isSuccess) {
            R.drawable.ic_stat_download_done
        } else {
            R.drawable.ic_stat_download_error
        }
        val notifTitle = res.getString(
            if (isSuccess) {
                R.string.download_notif_done_title
            } else {
                R.string.download_notif_failed_title
            },
        )
        val text = title.ifBlank { res.getString(R.string.download_notif_fallback_title) }
        val notificationId = RESULT_ID_BASE + (key.hashCode() and 0x7FFF)

        val notification = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(icon)
            .setContentTitle(notifTitle)
            .setContentText(text)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openDownloadsPendingIntent())
            .build()

        runCatching {
            compatManager.notify(notificationId, notification)
        }.onFailure { e ->
            Log.w(TAG, "发送下载结果通知失败 id=$notificationId", e)
        }
    }

    private fun openDownloadsPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(DEEP_LINK_DOWNLOADS)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN_DOWNLOADS,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelPendingIntent(): PendingIntent {
        val intent = Intent(context, DownloadNotificationActionReceiver::class.java).apply {
            action = DownloadNotificationActionReceiver.ACTION_CANCEL_ACTIVE
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_CANCEL_ACTIVE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // Application Context 不随应用内语言覆盖更新，按 Locale.getDefault() 派生资源上下文
    private fun localizedContext(): Context {
        val targetLocale = Locale.getDefault()
        val currentLocale = if (!context.resources.configuration.locales.isEmpty) {
            context.resources.configuration.locales[0]
        } else {
            null
        }
        if (currentLocale == targetLocale) return context
        val config = Configuration(context.resources.configuration).apply {
            setLocale(targetLocale)
            setLayoutDirection(targetLocale)
        }
        return context.createConfigurationContext(config)
    }

    companion object {
        private const val TAG = "DownloadNotifManager"
        const val CHANNEL_PROGRESS = "download_progress"
        const val CHANNEL_RESULT = "download_result"
        const val NOTIFICATION_ID_PROGRESS = 1001
        private const val RESULT_ID_BASE = 2000
        private const val REQUEST_CODE_OPEN_DOWNLOADS = 101
        private const val REQUEST_CODE_CANCEL_ACTIVE = 102
        private const val DEEP_LINK_DOWNLOADS = "pixiv://downloads"
    }
}
