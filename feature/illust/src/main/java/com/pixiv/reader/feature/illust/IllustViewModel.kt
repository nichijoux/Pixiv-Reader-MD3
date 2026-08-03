package com.pixiv.reader.feature.illust

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixivapi.model.Comment
import com.example.pixivapi.model.Illust
import com.pixiv.reader.core.database.dao.BrowseHistoryDao
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.entity.BrowseHistoryEntity
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.model.IllustPageInfo
import com.pixiv.reader.core.model.toPages
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

@HiltViewModel
class IllustViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
    private val browseHistoryDao: BrowseHistoryDao,
    private val downloadEntryDao: DownloadEntryDao,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val illustId: Long = savedStateHandle.get<Long>("illustId") ?: 0L

    private val _illust = MutableStateFlow<Illust?>(null)
    val illust: StateFlow<Illust?> = _illust.asStateFlow()

    private val _pages = MutableStateFlow<List<IllustPageInfo>>(emptyList())
    val pages: StateFlow<List<IllustPageInfo>> = _pages.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked: StateFlow<Boolean> = _isBookmarked.asStateFlow()

    private val _isBookmarking = MutableStateFlow(false)
    val isBookmarking: StateFlow<Boolean> = _isBookmarking.asStateFlow()

    private val _commentDraft = MutableStateFlow("")
    val commentDraft: StateFlow<String> = _commentDraft.asStateFlow()

    private val _message = Channel<String>(Channel.BUFFERED)
    val message = _message.receiveAsFlow()

    val relatedPaged = PagedState<Illust>()
    val commentsPaged = PagedState<Comment>()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching { pixivRepository.api.getIllust(illustId) }
                .onSuccess { resp ->
                    val ill = resp.illust ?: return@onSuccess
                    _illust.value = ill
                    _isBookmarked.value = ill.is_bookmarked == true
                    _pages.value = ill.toPages()
                    recordHistory(ill)
                    loadRelated()
                    loadComments()
                    loadRealSizes()
                }
                .onFailure {
                    _error.value = it.message ?: "加载失败"
                }
            _isLoading.value = false
        }
    }

    /** 打开详情时写入浏览历史（先删旧记录避免重复）。 */
    private fun recordHistory(ill: Illust) {
        viewModelScope.launch {
            runCatching {
                browseHistoryDao.deleteByTarget("illust", ill.id)
                browseHistoryDao.upsert(
                    BrowseHistoryEntity(
                        targetType = "illust",
                        targetId = ill.id,
                        title = ill.title,
                        coverUrl = ill.image_urls?.medium ?: ill.image_urls?.square_medium,
                    ),
                )
            }
        }
    }

    /** 网页接口补齐每 P 真实宽高（app-api 不提供） */
    private fun loadRealSizes() {
        viewModelScope.launch {
            runCatching { pixivRepository.webApi.getIllustPages(illustId) }
                .onSuccess { resp ->
                    val sizes = resp.body.orEmpty()
                    if (sizes.isNotEmpty()) {
                        val updated = _pages.value.mapIndexed { index, page ->
                            val size = sizes.getOrNull(index)
                            if (size != null && size.width > 0 && size.height > 0) {
                                page.copy(width = size.width, height = size.height)
                            } else {
                                page
                            }
                        }
                        _pages.value = updated
                    }
                }
        }
    }

    fun loadRelated() {
        viewModelScope.launch {
            relatedPaged.loadInitial(
                fetch = { pixivRepository.api.getRelatedIllusts(illustId) },
                fetchNext = { pixivRepository.api.getNextIllusts(it) },
            )
        }
    }

    fun loadMoreRelated() {
        viewModelScope.launch { relatedPaged.loadMore() }
    }

    fun loadComments() {
        viewModelScope.launch {
            commentsPaged.loadInitial(
                fetch = { pixivRepository.api.getIllustComments(illustId) },
                fetchNext = { pixivRepository.api.getNextComments(it) },
            )
        }
    }

    fun onCommentDraftChange(value: String) {
        _commentDraft.value = value
    }

    fun postComment() {
        val text = _commentDraft.value.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            runCatching { pixivRepository.api.postIllustComment(illustId, text) }
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
            val result = runCatching {
                if (current) {
                    pixivRepository.api.unbookmarkIllust(illustId)
                } else {
                    pixivRepository.api.bookmarkIllust(illustId, "public", emptyList())
                }
            }
            result
                .onSuccess { _isBookmarked.value = !current }
                .onFailure { _message.send("操作失败：${it.message}") }
            _isBookmarking.value = false
        }
    }

    /** 下载当前作品原图到 filesDir/Downloads，并写入下载索引（P6 迁移到 DownloadManager）。 */
    fun download() {
        viewModelScope.launch {
            val page = _pages.value.firstOrNull() ?: return@launch
            val url = page.originalUrl ?: page.displayUrl ?: return@launch
            val name = "pixiv_${illustId}.jpg"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.filesDir, "Downloads").apply { mkdirs() }
                    val file = File(dir, name)
                    pixivRepository.imageClient.newCall(Request.Builder().url(url).build())
                        .execute()
                        .use { resp ->
                            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                            resp.body?.byteStream()?.use { input ->
                                file.outputStream().use { input.copyTo(it) }
                            }
                        }
                    file
                }
            }
            result
                .onSuccess { file ->
                    recordDownload(file, status = "done")
                    _message.send("已保存到下载目录")
                }
                .onFailure {
                    recordDownload(file = null, status = "failed")
                    _message.send("下载失败：${it.message}")
                }
        }
    }

    /** 写入下载索引（targetType=illust）。 */
    private fun recordDownload(file: File?, status: String) {
        viewModelScope.launch {
            runCatching {
                downloadEntryDao.upsert(
                    DownloadEntryEntity(
                        targetId = illustId,
                        targetType = "illust",
                        title = _illust.value?.title,
                        coverUrl = _illust.value?.image_urls?.medium
                            ?: _illust.value?.image_urls?.square_medium,
                        localPath = file?.path,
                        status = status,
                        pageCount = _pages.value.size,
                    ),
                )
            }
        }
    }

    /** 举报（占位；P7 接入 /v2/illust/report） */
    fun report() {
        viewModelScope.launch {
            _message.send("举报功能开发中")
        }
    }
}
