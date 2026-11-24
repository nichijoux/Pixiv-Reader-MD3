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
 * 处理通知栏取消下载广播：Worker 捕获 CancellationException 后不会回写数据库，
 * 故须在此同步删除活跃条目，防状态卡在下载中或误发失败通知。
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
        const val ACTION_CANCEL_ACTIVE = "com.pixiv.reader.action.CANCEL_ACTIVE_DOWNLOADS"
    }
}
