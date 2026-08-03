package com.pixiv.reader.feature.novel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.pixivapi.model.Comment
import com.example.pixivapi.model.Novel
import com.pixiv.reader.core.database.dao.BrowseHistoryDao
import com.pixiv.reader.core.database.dao.ReadingProgressDao
import com.pixiv.reader.core.database.entity.BrowseHistoryEntity
import com.pixiv.reader.core.database.entity.ReadingProgressEntity
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 小说详情 ViewModel：详情 / 系列章节 / 阅读进度 / 收藏 / 追更。
 */
@HiltViewModel
class NovelViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val pixivRepository: PixivRepository,
    private val readingProgressDao: ReadingProgressDao,
    private val browseHistoryDao: BrowseHistoryDao,
    private val novelExporter: NovelExporter,
) : ViewModel() {

    private val novelId: Long = savedStateHandle.get<Long>("novelId") ?: 0L

    private val _novel = MutableStateFlow<Novel?>(null)
    val novel: StateFlow<Novel?> = _novel.asStateFlow()

    /** 系列详情（含章节列表） */
    private val _seriesNovels = MutableStateFlow<List<Novel>>(emptyList())
    val seriesNovels: StateFlow<List<Novel>> = _seriesNovels.asStateFlow()

    /** 评论（第一页） */
    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments.asStateFlow()

    private val _commentsLoading = MutableStateFlow(false)
    val commentsLoading: StateFlow<Boolean> = _commentsLoading.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

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

    private val _message = Channel<String>(Channel.BUFFERED)
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
                    loadComments()
                }
                .onFailure {
                    _error.value = it.message ?: "加载失败"
                }
            _isLoading.value = false
        }
    }

    /** 打开详情时写入浏览历史（先删旧记录避免重复）。 */
    private fun recordHistory(detail: Novel) {
        viewModelScope.launch {
            runCatching {
                browseHistoryDao.deleteByTarget("novel", detail.id)
                browseHistoryDao.upsert(
                    BrowseHistoryEntity(
                        targetType = "novel",
                        targetId = detail.id,
                        title = detail.title,
                        coverUrl = detail.image_urls?.medium ?: detail.image_urls?.square_medium,
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

    /** 加载评论区（第一页主评论）。 */
    fun loadComments() {
        if (_commentsLoading.value) return
        viewModelScope.launch {
            _commentsLoading.value = true
            runCatching { pixivRepository.api.getNovelComments(novelId) }
                .onSuccess { resp -> _comments.value = resp.comments }
                .onFailure { _comments.value = emptyList() }
            _commentsLoading.value = false
        }
    }

    // ── 评论区输入 / 发布 ────────────────────────────────────────────────────

    private val _commentDraft = MutableStateFlow("")
    val commentDraft: StateFlow<String> = _commentDraft.asStateFlow()

    fun onCommentDraftChange(value: String) {
        _commentDraft.value = value
    }

    /** 发布评论（成功后清空并刷新评论区）。 */
    fun postComment() {
        val text = _commentDraft.value.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            runCatching { pixivRepository.api.postNovelComment(novelId, text) }
                .onSuccess {
                    _commentDraft.value = ""
                    _message.send("评论已发布")
                    loadComments()
                }
                .onFailure { _message.send("评论失败：${it.message}") }
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
                _message.send(if (!current) "已收藏" else "已取消收藏")
            }.onFailure {
                _message.send("操作失败：${it.message}")
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
                _message.send(if (!current) "已加入追更" else "已取消追更")
            }.onFailure {
                _message.send("操作失败：${it.message}")
            }
            _isWatchlisting.value = false
        }
    }

    // ── 下载 / 导出 ──────────────────────────────────────────────────────────

    /** 导出当前单本小说为指定格式文件。 */
    fun exportNovel(format: NovelExportFormat) {
        val detail = _novel.value ?: return
        if (_downloading.value) return
        viewModelScope.launch {
            _downloading.value = true
            _downloadProgress.value = "正在下载…"
            // exportNovel 内部已 runCatching，返回 Result<File>
            novelExporter.exportNovel(detail, format)
                .onSuccess { file -> _message.send("已导出：${file.name}") }
                .onFailure { _message.send("导出失败：${it.message}") }
            _downloading.value = false
            _downloadProgress.value = null
        }
    }

    /** 导出整个系列为指定格式文件（逐章下载）。 */
    fun exportSeries(format: NovelExportFormat) {
        val detail = _novel.value ?: return
        if (_downloading.value) return
        viewModelScope.launch {
            _downloading.value = true
            _downloadProgress.value = "准备中…"
            novelExporter.exportSeries(detail, format) { index, total ->
                _downloadProgress.value = "正在下载第 $index/$total 章…"
            }
                .onSuccess { file -> _message.send("已导出：${file.name}") }
                .onFailure { _message.send("导出失败：${it.message}") }
            _downloading.value = false
            _downloadProgress.value = null
        }
    }

    // ── 离线下载（WorkManager 后台队列，缓存到 app，断网可读） ───────────────

    /** 下载当前单本到应用（后台队列）。 */
    fun downloadOfflineCurrent() {
        val detail = _novel.value ?: return
        val request = OneTimeWorkRequestBuilder<NovelOfflineDownloadWorker>()
            .setInputData(workDataOf(NovelOfflineDownloadWorker.KEY_NOVEL_ID to detail.id))
            .build()
        WorkManager.getInstance(context).enqueue(request)
        _message.trySend("已加入下载队列")
    }

    /** 下载整个系列到应用（后台队列，失败自动重试）。 */
    fun downloadOfflineSeries() {
        val detail = _novel.value ?: return
        val seriesId = detail.series?.id ?: return
        val request = OneTimeWorkRequestBuilder<NovelOfflineDownloadWorker>()
            .setInputData(
                workDataOf(
                    NovelOfflineDownloadWorker.KEY_NOVEL_ID to detail.id,
                    NovelOfflineDownloadWorker.KEY_SERIES_ID to seriesId,
                ),
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
        _message.trySend("已加入下载队列（后台）")
    }
}
