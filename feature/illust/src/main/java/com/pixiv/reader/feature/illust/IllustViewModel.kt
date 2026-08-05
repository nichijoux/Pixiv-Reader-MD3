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
import com.pixiv.reader.core.common.UiMessage
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

/**
 * 插画详情 ViewModel：详情 / 多页（网页接口补每 P 真实宽高）/ 相关推荐 / 评论区 / 收藏。
 * 附带副作用：打开详情写浏览历史；下载原图到 filesDir/Downloads 并写下载索引。
 * illustId 从 SavedStateHandle 读取（路由参数）。
 */
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

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error.asStateFlow()

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked: StateFlow<Boolean> = _isBookmarked.asStateFlow()

    private val _isBookmarking = MutableStateFlow(false)
    val isBookmarking: StateFlow<Boolean> = _isBookmarking.asStateFlow()

    private val _commentDraft = MutableStateFlow("")
    val commentDraft: StateFlow<String> = _commentDraft.asStateFlow()

    private val _message = Channel<UiMessage>(Channel.BUFFERED)
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
                    _error.value = it.message?.let { m -> UiMessage(R.string.illust_error_load_failed_reason, listOf(m)) }
                        ?: UiMessage(R.string.illust_error_load_failed)
                }
            _isLoading.value = false
        }
    }

    /** 打开详情时写入浏览历史（先删旧记录避免重复；payloadJson 存宽高等，供历史页完整显示）。 */
    private fun recordHistory(ill: Illust) {
        viewModelScope.launch {
            runCatching {
                val payload = org.json.JSONObject().apply {
                    put("id", ill.id)
                    put("title", ill.title.orEmpty())
                    put("coverUrl", ill.image_urls?.medium ?: ill.image_urls?.square_medium)
                    put("width", ill.width)
                    put("height", ill.height)
                    put("bookmarks", ill.total_bookmarks ?: 0)
                    put("pageCount", ill.page_count ?: 0)
                    put("isBookmarked", ill.is_bookmarked == true)
                }.toString()
                browseHistoryDao.deleteByTarget("illust", ill.id)
                browseHistoryDao.upsert(
                    BrowseHistoryEntity(
                        targetType = "illust",
                        targetId = ill.id,
                        title = ill.title,
                        coverUrl = ill.image_urls?.medium ?: ill.image_urls?.square_medium,
                        payloadJson = payload,
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
                    _message.send(UiMessage(R.string.illust_msg_comment_published))
                    loadComments()
                }
                .onFailure { _message.send(UiMessage(R.string.illust_msg_comment_failed, listOf(it.message ?: ""))) }
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
                .onFailure { _message.send(UiMessage(R.string.illust_msg_action_failed, listOf(it.message ?: ""))) }
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
                    _message.send(UiMessage(R.string.illust_msg_saved_to_downloads))
                }
                .onFailure {
                    recordDownload(file = null, status = "failed")
                    _message.send(UiMessage(R.string.illust_msg_download_failed, listOf(it.message ?: "")))
                }
        }
    }

    /** 写入下载索引（targetType=illust；解析本地文件真实宽高）。 */
    private fun recordDownload(file: File?, status: String) {
        viewModelScope.launch {
            runCatching {
                var w = 0
                var h = 0
                if (file != null && file.exists()) {
                    val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(file.path, opts)
                    w = opts.outWidth
                    h = opts.outHeight
                }
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
                        width = w,
                        height = h,
                    ),
                )
            }
        }
    }

    /** 举报（占位；P7 接入 /v2/illust/report） */
    fun report() {
        viewModelScope.launch {
            _message.send(UiMessage(R.string.illust_msg_report_wip))
        }
    }
}
