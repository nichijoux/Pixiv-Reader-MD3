package com.pixiv.reader.feature.illust

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.common.MessageType
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.database.dao.BrowseHistoryDao
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.entity.BrowseHistoryEntity
import com.pixiv.reader.core.model.IllustPageInfo
import com.pixiv.reader.core.model.toPages
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
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
 * 插画详情 ViewModel：详情 / 多页（网页接口补每 P 真实宽高）/ 相关推荐 / 评论区 / 收藏。
 * 附带副作用：打开详情写浏览历史；下载整个作品（全部页）由 [IllustDownloadWorker] 后台执行，
 * 完成后观察下载索引并发应用内完成/失败通知。
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

    private val _message = Channel<UiMessage>(Channel.BUFFERED)
    val message = _message.receiveAsFlow()

    val relatedPaged = PagedState<Illust>()

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

    /** 下载整个作品（全部页）到 filesDir/Downloads/pixiv_{id}/，由 WorkManager 后台执行。 */
    fun download() {
        val request = OneTimeWorkRequestBuilder<IllustDownloadWorker>()
            .setInputData(workDataOf(IllustDownloadWorker.KEY_ILLUST_ID to illustId))
            .build()
        WorkManager.getInstance(context).enqueue(request)
        _message.trySend(UiMessage(R.string.illust_msg_download_started))
        observeDownloadCompletion()
    }

    /** 观察本次下载完成：等 downloading 出现后，再等 done/failed，发应用内完成/失败通知（与开始通知同位置）。 */
    private fun observeDownloadCompletion() {
        viewModelScope.launch {
            // 先等本次下载进入 downloading（避免命中历史 done 记录）
            downloadEntryDao.observeAll().first { entries ->
                entries.any { it.targetId == illustId && it.targetType == "illust" && it.status == "downloading" }
            }
            val done = downloadEntryDao.observeAll()
                .first { entries ->
                    entries.any { it.targetId == illustId && it.targetType == "illust" && (it.status == "done" || it.status == "failed") }
                }
                .firstOrNull { it.targetId == illustId && it.targetType == "illust" }
            when (done?.status) {
                "done" -> _message.send(UiMessage(R.string.illust_msg_download_done, type = MessageType.SUCCESS))
                "failed" -> _message.send(UiMessage(R.string.illust_msg_download_failed, listOf(""), type = MessageType.ERROR))
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
