package com.pixiv.reader.feature.novel.data

import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.network.download.DownloadQueue

/**
 * 小说导出统一入队：装配「待同步」下载索引条目 + 组装 WorkManager 导出请求。
 * 单本详情（Route 层接线，经 core VM [markDownloadPending] 写条目）与
 * 系列页（VM 内经 [DownloadQueue.markPending] 写条目）共用同一装配逻辑，
 * 断网停留待同步（网络约束），联网自动开始。
 */
internal object NovelExportQueue {

    /**
     * 装配「待同步」下载索引条目（卡片快照）：标题自动加格式后缀，
     * 作者/字数/收藏/发布时间/系列齐全，下载管理卡片入队即可完整显示。
     *
     * @param novelId 下载索引 targetId（单本=小说 id；系列=首册 id）
     * @param seriesId 所属系列 id（null / 非正 = 单本导出）
     * @param chapterIds 部分分册 id（仅系列「选取部分」非空；决定 scopeKey=partial）
     * @param format 导出格式
     * @param title 卡片标题基底（不含格式后缀；null 不展示标题）
     * @param coverUrl 封面 URL
     * @param seriesTitle 系列标题
     * @param authorName 作者名
     * @param authorAvatarUrl 作者头像 URL
     * @param wordCount 字数
     * @param favoriteCount 收藏数
     * @param publishDate 发布时间
     * @return 待写入下载索引的条目（status 由调用方经 markPending 置 pending）
     */
    fun pendingEntry(
        novelId: Long,
        seriesId: Long?,
        chapterIds: List<Long>,
        format: NovelExportFormat,
        title: String?,
        coverUrl: String?,
        seriesTitle: String?,
        authorName: String?,
        authorAvatarUrl: String?,
        wordCount: Int,
        favoriteCount: Int,
        publishDate: String?,
    ): DownloadEntryEntity = DownloadEntryEntity(
        targetId = novelId,
        targetType = "novel",
        title = title?.let { "$it（${format.name}）" },
        coverUrl = coverUrl,
        format = format.name,
        scopeKey = novelScopeKey(seriesId, chapterIds.takeIf { it.isNotEmpty() }),
        seriesId = seriesId?.takeIf { it > 0L },
        seriesTitle = seriesTitle,
        authorName = authorName,
        authorAvatarUrl = authorAvatarUrl,
        wordCount = wordCount,
        favoriteCount = favoriteCount,
        publishDate = publishDate,
    )

    /**
     * 组装导出 WorkManager 请求：网络约束（断网停留待同步）+ 按条目复合主键 tag
     * （入队与下载管理页删除取消两端共用）。
     *
     * @param novelId 目标小说 id（单本=小说 id；系列=首册 id）
     * @param seriesId 所属系列 id（非空时写入 [NovelExportWorker.KEY_SERIES_ID]，Worker 按系列导出）
     * @param chapterIds 部分分册 id（非空时写入 [NovelExportWorker.KEY_CHAPTER_IDS]，只导出选中分册）
     * @param format 导出格式
     * @return 待入队的一次性 WorkManager 请求
     */
    fun buildRequest(
        novelId: Long,
        seriesId: Long?,
        chapterIds: List<Long>,
        format: NovelExportFormat,
    ): OneTimeWorkRequest {
        val scopeKey = novelScopeKey(seriesId, chapterIds.takeIf { it.isNotEmpty() })
        val data = mutableListOf<Pair<String, Any?>>()
        data += NovelExportWorker.KEY_NOVEL_ID to novelId
        data += NovelExportWorker.KEY_FORMAT to format.name
        seriesId?.let { data += NovelExportWorker.KEY_SERIES_ID to it }
        if (chapterIds.isNotEmpty()) {
            data += NovelExportWorker.KEY_CHAPTER_IDS to chapterIds.toLongArray()
        }
        return OneTimeWorkRequestBuilder<NovelExportWorker>()
            .setInputData(workDataOf(*data.toTypedArray()))
            .setConstraints(DownloadQueue.networkConstraints())
            .addTag(DownloadQueue.workTag("novel", novelId, format.name, scopeKey))
            .build()
    }
}
