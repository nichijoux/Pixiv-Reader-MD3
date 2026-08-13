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
import com.pixiv.reader.core.network.ugoira.UgoiraFrame
import com.pixiv.reader.core.network.ugoira.UgoiraLoader
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
 * 插画详情 ViewModel：详情 / 多页（网页接口补每 P 真实宽高）/ 动图帧（ugoira）/ 相关推荐 / 评论区 / 收藏。
 * 附带副作用：打开详情写浏览历史；下载整个作品（全部页）由 [IllustDownloadWorker] 后台执行，
 * 完成后观察下载索引并发应用内完成/失败通知。
 * illustId 从 SavedStateHandle 读取（路由参数）。
 */
@HiltViewModel
class IllustViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
    private val ugoiraLoader: UgoiraLoader,
    private val browseHistoryDao: BrowseHistoryDao,
    private val downloadEntryDao: DownloadEntryDao,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val illustId: Long = savedStateHandle.get<Long>("illustId") ?: 0L

    private val _illust = MutableStateFlow<Illust?>(null)
    val illust: StateFlow<Illust?> = _illust.asStateFlow()

    private val _pages = MutableStateFlow<List<IllustPageInfo>>(emptyList())
    val pages: StateFlow<List<IllustPageInfo>> = _pages.asStateFlow()

    /** 动图帧（ugoira 作品非空）：详情页 pager 播放动画（帧未就绪显示静态封面）。 */
    private val _ugoiraFrames = MutableStateFlow<List<UgoiraFrame>>(emptyList())
    val ugoiraFrames: StateFlow<List<UgoiraFrame>> = _ugoiraFrames.asStateFlow()

    /** 动图 zip 下载进度 0..1（下载中）；null = 未开始/已就绪/失败（详情页转圈提示用）。 */
    private val _ugoiraProgress = MutableStateFlow<Float?>(null)
    val ugoiraProgress: StateFlow<Float?> = _ugoiraProgress.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error.asStateFlow()

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked: StateFlow<Boolean> = _isBookmarked.asStateFlow()

    private val _isBookmarking = MutableStateFlow(false)
    val isBookmarking: StateFlow<Boolean> = _isBookmarking.asStateFlow()

    /** 作者是否已关注（作者行关注胶囊；用 user/detail 权威刷新）。 */
    private val _isAuthorFollowed = MutableStateFlow(false)
    val isAuthorFollowed: StateFlow<Boolean> = _isAuthorFollowed.asStateFlow()

    /** 作者关注操作进行中（防连点）。 */
    private val _isAuthorFollowing = MutableStateFlow(false)
    val isAuthorFollowing: StateFlow<Boolean> = _isAuthorFollowing.asStateFlow()

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
                    // 内嵌 user.is_followed 可能缺失，用 user/detail 权威刷新关注态（失败保留内嵌值）
                    _isAuthorFollowed.value = ill.user?.is_followed == true
                    ill.user?.id?.let { loadAuthorFollowState(it) }
                    _pages.value = ill.toPages()
                    recordHistory(ill)
                    loadRelated()
                    loadRealSizes()
                    if (ill.isGif()) loadUgoira()
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

    /** user/detail 权威刷新作者关注态（详情内嵌 user.is_followed 可能缺失）。 */
    private fun loadAuthorFollowState(userId: Long) {
        viewModelScope.launch {
            runCatching { pixivRepository.api.getUserDetail(userId) }
                .onSuccess { resp ->
                    resp.user?.is_followed?.let { _isAuthorFollowed.value = it }
                }
        }
    }

    /** 关注 / 取关作者（作者行胶囊，乐观翻转 + 防连点）。 */
    fun toggleFollowAuthor() {
        if (_isAuthorFollowing.value) return
        val userId = _illust.value?.user?.id ?: return
        viewModelScope.launch {
            _isAuthorFollowing.value = true
            val current = _isAuthorFollowed.value
            runCatching {
                if (current) pixivRepository.api.unfollowUser(userId)
                else pixivRepository.api.followUser(userId, "public")
            }
                .onSuccess {
                    _isAuthorFollowed.value = !current
                    _message.send(if (!current) UiMessage(R.string.illust_msg_followed) else UiMessage(R.string.illust_msg_unfollowed))
                }
                .onFailure {
                    _message.send(UiMessage(R.string.illust_msg_action_failed, listOf(it.message ?: "")))
                }
            _isAuthorFollowing.value = false
        }
    }

    /** 网页接口补齐每 P 真实宽高（app-api 不提供） */
    private fun loadRealSizes() {        viewModelScope.launch {
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

    /** 动图：加载 zip 帧（详情页 pager 播放；失败帧空则保持静态封面，查看器有失败提示）。 */
    private fun loadUgoira() {
        viewModelScope.launch {
            _ugoiraProgress.value = 0f
            _ugoiraFrames.value = ugoiraLoader.prepare(illustId) { p -> _ugoiraProgress.value = p }.orEmpty()
            _ugoiraProgress.value = null
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
}
