package com.pixiv.reader.feature.novel.state

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.common.MessageType
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.database.dao.BrowseHistoryDao
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.dao.ReadingProgressDao
import com.pixiv.reader.core.database.entity.BrowseHistoryEntity
import com.pixiv.reader.core.database.entity.ReadingProgressEntity
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.core.ui.component.NovelCardData
import com.google.gson.Gson
import com.pixiv.reader.feature.novel.R
import com.pixiv.reader.feature.novel.data.NovelExportFormat
import com.pixiv.reader.feature.novel.data.NovelExportWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 小说详情 ViewModel：详情 / 系列章节 / 阅读进度 / 收藏 / 追更。
 * 评论已独立到 [NovelCommentsViewModel]（详情页不再加载评论）。
 * 导出（worker）完成后观察下载索引并发应用内完成/失败通知。
 */
@HiltViewModel
class NovelViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val pixivRepository: PixivRepository,
    private val readingProgressDao: ReadingProgressDao,
    private val browseHistoryDao: BrowseHistoryDao,
    private val downloadEntryDao: DownloadEntryDao,
) : ViewModel() {

    private val novelId: Long = savedStateHandle.get<Long>("novelId") ?: 0L

    private val _novel = MutableStateFlow<Novel?>(null)
    val novel: StateFlow<Novel?> = _novel.asStateFlow()

    /** 系列详情（含章节列表） */
    private val _seriesNovels = MutableStateFlow<List<Novel>>(emptyList())
    val seriesNovels: StateFlow<List<Novel>> = _seriesNovels.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error.asStateFlow()

    private val _progress = MutableStateFlow<ReadingProgressEntity?>(null)
    val progress: StateFlow<ReadingProgressEntity?> = _progress.asStateFlow()

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked: StateFlow<Boolean> = _isBookmarked.asStateFlow()

    private val _isBookmarking = MutableStateFlow(false)
    val isBookmarking: StateFlow<Boolean> = _isBookmarking.asStateFlow()

    private val _isWatchlisted = MutableStateFlow(false)
    val isWatchlisted: StateFlow<Boolean> = _isWatchlisted.asStateFlow()

    private val _isWatchlisting = MutableStateFlow(false)
    val isWatchlisting: StateFlow<Boolean> = _isWatchlisting.asStateFlow()

    private val _message = Channel<UiMessage>(Channel.BUFFERED)
    val message = _message.receiveAsFlow()

    /** 下载/导出进行中 */
    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading.asStateFlow()

    /** 下载/导出进度文案（如"正在下载第 3/12 章…"） */
    private val _downloadProgress = MutableStateFlow<String?>(null)
    val downloadProgress: StateFlow<String?> = _downloadProgress.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching { pixivRepository.api.getNovel(novelId) }
                .onSuccess { resp ->
                    val detail = resp.novel ?: return@onSuccess
                    _novel.value = detail
                    _isBookmarked.value = detail.is_bookmarked == true
                    recordHistory(detail)
                    loadProgress()
                    loadSeries(detail)
                }
                .onFailure {
                    _error.value = it.message?.let { m -> UiMessage(R.string.novel_error_load_failed_reason, listOf(m)) }
                        ?: UiMessage(R.string.novel_error_load_failed)
                }
            _isLoading.value = false
        }
    }

    /** 打开详情时写入浏览历史（先删旧记录避免重复；payloadJson 存完整卡片数据供历史页展示）。 */
    private fun recordHistory(detail: Novel) {
        viewModelScope.launch {
            runCatching {
                val card = NovelCardData(
                    id = detail.id,
                    title = detail.title.orEmpty(),
                    coverUrl = detail.image_urls?.square_medium ?: detail.image_urls?.medium,
                    authorId = detail.user?.id ?: 0L,
                    authorName = detail.user?.name.orEmpty(),
                    authorAvatarUrl = detail.user?.profile_image_urls?.best(),
                    publishDate = detail.create_date,
                    seriesTitle = detail.series?.title,
                    seriesId = detail.series?.id,
                    favoriteCount = detail.total_bookmarks ?: 0,
                    wordCount = detail.text_length ?: 0,
                    tags = detail.tags.orEmpty()
                        .take(6)
                        .map { it.translated_name ?: it.name ?: "" }
                        .filter { it.isNotBlank() },
                    isFavorite = detail.is_bookmarked == true,
                )
                browseHistoryDao.deleteByTarget("novel", detail.id)
                browseHistoryDao.upsert(
                    BrowseHistoryEntity(
                        targetType = "novel",
                        targetId = detail.id,
                        title = detail.title,
                        coverUrl = detail.image_urls?.medium ?: detail.image_urls?.square_medium,
                        payloadJson = Gson().toJson(card),
                    ),
                )
            }
        }
    }

    private suspend fun loadProgress() {
        _progress.value = readingProgressDao.getByNovel(novelId)
    }

    private fun loadSeries(detail: Novel) {
        val seriesId = detail.series?.id ?: return
        viewModelScope.launch {
            runCatching { pixivRepository.api.getNovelSeries(seriesId) }
                .onSuccess { resp ->
                    _seriesNovels.value = resp.novels.orEmpty()
                    _isWatchlisted.value = resp.novel_series_detail?.watchlist_added == true
                }
        }
    }

    fun toggleBookmark() {
        if (_isBookmarking.value) return
        viewModelScope.launch {
            _isBookmarking.value = true
            val current = _isBookmarked.value
            runCatching {
                if (current) pixivRepository.api.unbookmarkNovel(novelId)
                else pixivRepository.api.bookmarkNovel(novelId, "public", emptyList())
            }.onSuccess {
                _isBookmarked.value = !current
                _message.send(if (!current) UiMessage(R.string.novel_msg_bookmarked) else UiMessage(R.string.novel_msg_unbookmarked))
            }.onFailure {
                _message.send(UiMessage(R.string.novel_msg_action_failed, listOf(it.message ?: "")))
            }
            _isBookmarking.value = false
        }
    }

    fun toggleWatchlist() {
        val seriesId = _novel.value?.series?.id ?: return
        if (_isWatchlisting.value) return
        viewModelScope.launch {
            _isWatchlisting.value = true
            val current = _isWatchlisted.value
            runCatching {
                if (current) pixivRepository.api.removeWatchlistNovel(seriesId)
                else pixivRepository.api.addWatchlistNovel(seriesId)
            }.onSuccess {
                _isWatchlisted.value = !current
                _message.send(if (!current) UiMessage(R.string.novel_msg_watching_added) else UiMessage(R.string.novel_msg_watching_removed))
            }.onFailure {
                _message.send(UiMessage(R.string.novel_msg_action_failed, listOf(it.message ?: "")))
            }
            _isWatchlisting.value = false
        }
    }

    // ── 下载 / 导出 ──────────────────────────────────────────────────────────

    /** 导出小说为指定格式文件（本文或整个系列，后台队列，支持断点续传）。 */
    fun export(format: NovelExportFormat, series: Boolean = false) {
        val detail = _novel.value ?: return
        if (_downloading.value) return
        val seriesId = if (series) detail.series?.id else null
        if (series && seriesId == null) return
        val data = mutableListOf<Pair<String, Any?>>()
        data += NovelExportWorker.KEY_NOVEL_ID to detail.id
        data += NovelExportWorker.KEY_FORMAT to format.name
        seriesId?.let { data += NovelExportWorker.KEY_SERIES_ID to it }
        val request = OneTimeWorkRequestBuilder<NovelExportWorker>()
            .setInputData(workDataOf(*data.toTypedArray()))
            .build()
        WorkManager.getInstance(context).enqueue(request)
        _downloading.value = true
        _downloadProgress.value = context.getString(R.string.novel_msg_export_queued)
        _message.trySend(UiMessage(R.string.novel_msg_export_queued))
        observeExportCompletion(detail.id)
    }

    /** 观察导出完成：等 downloading 出现后，再等 done/failed，复位导出中状态并发应用内完成/失败通知。 */
    private fun observeExportCompletion(id: Long) {
        viewModelScope.launch {
            downloadEntryDao.observeAll().first { entries ->
                entries.any { it.targetId == id && it.targetType == "novel" && it.status == "downloading" }
            }
            val done = downloadEntryDao.observeAll()
                .first { entries ->
                    entries.any { it.targetId == id && it.targetType == "novel" && (it.status == "done" || it.status == "failed") }
                }
                .firstOrNull { it.targetId == id && it.targetType == "novel" }
            _downloading.value = false
            _downloadProgress.value = null
            when (done?.status) {
                "done" -> _message.send(UiMessage(R.string.novel_msg_exported, listOf(done.title ?: ""), type = MessageType.SUCCESS))
                "failed" -> _message.send(UiMessage(R.string.novel_msg_export_failed, listOf(""), type = MessageType.ERROR))
            }
        }
    }
}
