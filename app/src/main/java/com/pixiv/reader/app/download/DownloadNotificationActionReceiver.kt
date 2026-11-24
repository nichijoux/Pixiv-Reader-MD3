package com.pixiv.reader.app.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import androidx.work.await
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.network.download.DownloadQueue
import com.pixiv.reader.core.network.download.DownloadWorkerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 系统下载进度通知的操作广播接收器：处理通知栏“取消 / 全部取消”动作。
 *
 * 取消流程：
 * 1. 先立即撤销通知栏进度卡片，提供即时交互反馈；
 * 2. 查询当前处于 `pending` / `downloading` 的索引条目，按 [DownloadQueue.workTag] 取消 WorkManager 任务；
 * 3. 从 `download_entry` 删除对应条目（Worker 捕获 `CancellationException` 后不会回写数据库，
 *    必须在此清理，既防止状态卡在下载中，也避免误触发下载失败通知）。
 */
class DownloadNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CANCEL_ACTIVE) return
        val appContext = context.applicationContext
        // 立即撤销进度通知，避免异步取消完成前卡在状态栏
        runCatching {
            NotificationManagerCompat.from(appContext)
                .cancel(DownloadNotificationManager.NOTIFICATION_ID_PROGRESS)
        }
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    appContext,
                    DownloadWorkerEntryPoint::class.java,
                )
                val dao = entryPoint.downloadEntryDao()
                val workManager = WorkManager.getInstance(appContext)
                val activeEntries = dao.observeAll().first().filter { entry ->
                    entry.status == DownloadEntryEntity.STATUS_DOWNLOADING ||
                        entry.status == DownloadEntryEntity.STATUS_PENDING
                }
                activeEntries.forEach { entry ->
                    val tag = DownloadQueue.workTag(
                        targetType = entry.targetType,
                        targetId = entry.targetId,
                        format = entry.format,
                        scopeKey = entry.scopeKey,
                    )
                    runCatching { workManager.cancelAllWorkByTag(tag).await() }
                        .onFailure { e -> Log.w(TAG, "取消下载任务失败 tag=$tag", e) }
                    runCatching { dao.delete(entry) }
                        .onFailure { e -> Log.w(TAG, "清理已取消下载条目失败 id=${entry.targetId}", e) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "处理通知栏取消下载动作失败", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "DownloadNotifReceiver"

        /** 取消全部在途/待同步下载任务的广播 Action。 */
        const val ACTION_CANCEL_ACTIVE = "com.pixiv.reader.action.CANCEL_ACTIVE_DOWNLOADS"
    }
}
