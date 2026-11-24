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

/**
 * 下载通知去重键（与数据库复合主键一致，区分同作品多格式与单本/系列/部分导出）。
 *
 * @property targetType 目标类型（illust / ugoira / novel）
 * @property targetId 目标 ID
 * @property format 导出格式（插画为空串，小说/动图为具体格式）
 * @property scopeKey 下载作用域（单本为空串，系列为 series，部分分册为 partial）
 */
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

/**
 * 当前活跃下载任务聚合状态（用于构建系统进度通知与 Android 16 实况胶囊）。
 *
 * @property activeCount 活跃任务总数（downloading + pending）
 * @property downloadingCount 正在下载中的任务数
 * @property pendingCount 等待网络/排队的任务数
 * @property primaryTitle 优先展示的主任务标题（优先取 downloading 首个非空标题）
 * @property progress 聚合进度百分比（0-100；全为 pending 时为 0）
 * @property indeterminate 是否不确定进度（仅当全为 pending 无 downloading 时为 true）
 */
data class DownloadProgressState(
    val activeCount: Int,
    val downloadingCount: Int,
    val pendingCount: Int,
    val primaryTitle: String,
    val progress: Int,
    val indeterminate: Boolean,
)

/** 系统下载通知输出端口（隔离 Android 通知 API，便于 [com.pixiv.reader.app.DownloadCompletionNotifier] 单元测试）。 */
interface DownloadNotificationSink {
    /** 创建或校验通知渠道。 */
    fun ensureChannels()

    /**
     * 展示或更新系统下载进度通知（含 Android 16 Live Updates）。
     *
     * @param state 当前聚合下载状态
     * @return 无返回值
     */
    fun showProgress(state: DownloadProgressState)

    /** 撤销正在展示的进度通知。 */
    fun cancelProgress()

    /**
     * 发送后台下载完成或失败通知。
     *
     * @param key 任务去重键（用于计算稳定通知 ID）
     * @param title 任务标题
     * @param isSuccess true 为下载完成，false 为下载失败
     * @return 无返回值
     */
    fun showResult(key: DownloadTaskKey, title: String, isSuccess: Boolean)
}

/**
 * 系统下载通知构建与分发器（兼容标准系统进度通知 + Android 16 原生 Live Updates 实况通知）。
 *
 * 规范约束：
 * - 进度渠道使用 `IMPORTANCE_DEFAULT`（Android 16 实况提升禁止 `IMPORTANCE_MIN`），同时静音无震动并设 `setOnlyAlertOnce(true)`；
 * - 使用 `NotificationCompat.ProgressStyle` + `setOngoing(true)` + `setRequestPromotedOngoing(true)` +
 *   `setShortCriticalText(...)`，不使用 `RemoteViews`、`setColorized(true)` 或 `setGroupSummary(true)`；
 * - Android 15 及以下自动降级为标准系统进度条通知。
 */
@Singleton
class DownloadNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DownloadNotificationSink {
    private val compatManager: NotificationManagerCompat
        get() = NotificationManagerCompat.from(context)

    /**
     * 确保下载进度与结果通知渠道已创建（幂等，minSdk 26 均支持）。
     *
     * @return 无返回值
     */
    override fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val res = localizedContext().resources

        // 进度渠道：Android 16 Live Updates 要求 importance >= DEFAULT（不能用 MIN），同时关闭提示音与震动
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

        // 结果渠道：后台下载完成/失败通知
        val resultChannel = NotificationChannel(
            CHANNEL_RESULT,
            res.getString(R.string.download_channel_result_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = res.getString(R.string.download_channel_result_desc)
        }

        manager.createNotificationChannels(listOf(progressChannel, resultChannel))
    }

    /**
     * 检查当前是否已拥有发送系统通知的权限且用户未在系统设置关闭通知。
     *
     * @return true 表示可以安全调用 `notify`
     */
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

    /**
     * 发送或更新下载进度系统通知（支持 Android 16 实况胶囊，旧系统自动降级为标准进度条）。
     *
     * @param state 当前聚合下载状态
     * @return 无返回值
     */
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

        // Android 16+ 渲染为分段实况进度条；API < 36 在 apply() 内部自动回退为标准 setProgress(100, p, indeterminate)
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

    /**
     * 撤销正在展示的进度通知。
     *
     * @return 无返回值
     */
    override fun cancelProgress() {
        runCatching {
            compatManager.cancel(NOTIFICATION_ID_PROGRESS)
        }.onFailure { e ->
            Log.w(TAG, "撤销下载进度通知失败", e)
        }
    }

    /**
     * 发送后台下载结果通知（完成 / 失败）。
     *
     * @param key 任务去重键（用于计算稳定通知 ID）
     * @param title 任务标题
     * @param isSuccess true 为下载完成，false 为下载失败
     * @return 无返回值
     */
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

    /** 构造点击通知跳转下载管理页的 PendingIntent（复用 singleTask MainActivity）。 */
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

    /** 构造通知栏「取消 / 全部取消」动作按钮的 PendingIntent。 */
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

    /**
     * 按当前应用覆盖后的 [Locale.getDefault] 派生资源上下文，
     * 避免 Application Context 在语言切换后仍返回系统语言文案。
     */
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
