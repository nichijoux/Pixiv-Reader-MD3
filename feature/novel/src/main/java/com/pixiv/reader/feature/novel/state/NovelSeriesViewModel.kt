package com.pixiv.reader.feature.novel.state

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.pixiv.api.model.Novel
import com.pixiv.api.model.NovelSeriesDetail
import com.pixiv.reader.core.common.MessageType
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.network.paging.PagedState
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.core.network.session.SeriesDetailCache
import com.pixiv.reader.core.network.session.SeriesDetailInfo
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
 * 小说系列详情页 ViewModel：系列信息 + 分册列表（`/v2/novel/series` 分页）。
 * 系列封面（第一册 medium）走 [SeriesDetailCache]，与用户主页系列列表共享缓存。
 * 关注作者：内嵌 `user.is_followed` 初始 + `getUserDetail` 权威刷新（与详情页一致）。
 * 导出（worker）：整系列 / 部分分册（合并为一个文件），完成/失败观察下载索引发应用内通知。
 */
@HiltViewModel
class NovelSeriesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val pixivRepository: PixivRepository,
    private val seriesDetailCache: SeriesDetailCache,
    private val downloadEntryDao: DownloadEntryDao,
) : ViewModel() {

    private val seriesId: Long = savedStateHandle.get<Long>("seriesId") ?: 0L

    private val _detail = MutableStateFlow<NovelSeriesDetail?>(null)
    val detail: StateFlow<NovelSeriesDetail?> = _detail.asStateFlow()

    /** 系列第一册封面 URL（系列无独立封面，用首册 `image_urls` 兜底；null 表示无可用图）。 */
    private val _firstNovelCover = MutableStateFlow<String?>(null)
    val firstNovelCover: StateFlow<String?> = _firstNovelCover.asStateFlow()

    val paged = PagedState<Novel>()

    // ── 关注作者 ─────────────────────────────────────────────────────────────

    private val _isAuthorFollowed = MutableStateFlow(false)
    val isAuthorFollowed: StateFlow<Boolean> = _isAuthorFollowed.asStateFlow()

    private val _isAuthorFollowing = MutableStateFlow(false)
    val isAuthorFollowing: StateFlow<Boolean> = _isAuthorFollowing.asStateFlow()

    private val _message = Channel<UiMessage>(Channel.BUFFERED)
    val message = _message.receiveAsFlow()

    // ── 下载 / 导出 ──────────────────────────────────────────────────────────

    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow<String?>(null)
    val downloadProgress: StateFlow<String?> = _downloadProgress.asStateFlow()

    /** 系列全量分册（供「选取部分」下载；首次按需拉取并缓存，非分页）。 */
    private val _allChapters = MutableStateFlow<List<Novel>>(emptyList())
    val allChapters: StateFlow<List<Novel>> = _allChapters.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            paged.loadInitial(
                fetch = {
                    pixivRepository.api.getNovelSeries(seriesId).also { resp ->
                        _detail.value = resp.novel_series_detail
                        // 走进程级缓存：用户主页列表已取过系列详情则零请求
                        _firstNovelCover.value = seriesDetailCache.getOrFetch(seriesId) {
                            SeriesDetailInfo(
                                coverUrl = resp.novel_series_first_novel?.image_urls?.medium
                                    ?: resp.novel_series_first_novel?.image_urls?.square_medium,
                                caption = resp.novel_series_detail?.caption,
                                isConcluded = resp.novel_series_detail?.is_concluded,
                                totalChars = resp.novel_series_detail?.total_character_count ?: 0,
                                updatedAt = resp.novel_series_latest_novel?.create_date,
                            )
                        }?.coverUrl
                        val seriesDetail = resp.novel_series_detail
                        seriesDetail?.user?.id?.let { userId ->
                            _isAuthorFollowed.value = seriesDetail.user?.is_followed == true
                            loadAuthorFollowState(userId)
                        }
                    }
                },
                fetchNext = { pixivRepository.api.getNextNovelSeriesDetail(it) },
            )
        }
    }

    fun loadMore() {
        viewModelScope.launch { paged.loadMore() }
    }

    /** 收藏 / 取消收藏小说（nowFavorite 为目标状态，由组件回调）。 */
    fun toggleNovelFavorite(novelId: Long, nowFavorite: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (nowFavorite) pixivRepository.api.bookmarkNovel(novelId, "public", emptyList())
                else pixivRepository.api.unbookmarkNovel(novelId)
            }
        }
    }

    /** 用 user/detail 权威刷新作者关注态（best-effort，失败保留内嵌值）。 */
    private fun loadAuthorFollowState(userId: Long) {
        viewModelScope.launch {
            runCatching { pixivRepository.api.getUserDetail(userId) }
                .onSuccess { resp ->
                    resp.user?.is_followed?.let { _isAuthorFollowed.value = it }
                }
        }
    }

    /** 关注 / 取关作者（系列页作者行按钮，乐观翻转 + 防连点）。 */
    fun toggleFollowAuthor() {
        if (_isAuthorFollowing.value) return
        val userId = _detail.value?.user?.id ?: return
        viewModelScope.launch {
            _isAuthorFollowing.value = true
            val current = _isAuthorFollowed.value
            runCatching {
                if (current) pixivRepository.api.unfollowUser(userId)
                else pixivRepository.api.followUser(userId, "public")
            }.onSuccess {
                _isAuthorFollowed.value = !current
                _message.send(if (!current) UiMessage(R.string.novel_msg_followed) else UiMessage(R.string.novel_msg_unfollowed))
            }.onFailure {
                _message.send(UiMessage(R.string.novel_msg_action_failed, listOf(it.message ?: "")))
            }
            _isAuthorFollowing.value = false
        }
    }

    /** 拉取系列全量分册（供「选取部分下载」多选；复用已加载分页数据，避免重复请求）。 */
    fun ensureAllChaptersLoaded() {
        if (_allChapters.value.isNotEmpty()) return
        // 复用 paged 已加载的分册作初始快照（含触底已加载的）：弹窗立即有内容，不空白等待
        val initial = paged.items.value
        if (initial.isNotEmpty()) _allChapters.value = initial
        // 分页已全部加载完（hasMore=false，如小系列第一页即全量）：直接复用，零额外请求
        if (!paged.hasMore.value) return

        viewModelScope.launch {
            // 大系列补拉剩余页：LinkedHashMap 按 id 去重（初始快照 + 补拉合并，保持顺序）
            val result = LinkedHashMap<Long, Novel>().apply { initial.forEach { put(it.id, it) } }
            var lastOrder: Int? = null
            runCatching {
                while (true) {
                    val resp = pixivRepository.api.getNovelSeries(seriesId, lastOrder)
                    resp.novels?.forEach { result[it.id] = it }
                    val next = resp.next_url
                    if (next.isNullOrBlank()) break
                    lastOrder = parseLastOrder(next) ?: break
                }
            }.onSuccess {
                _allChapters.value = result.values.toList()
            }
        }
    }

    private fun parseLastOrder(nextUrl: String?): Int? {
        if (nextUrl.isNullOrBlank()) return null
        return nextUrl.substringAfter('?', "").split('&')
            .firstOrNull { it.startsWith("last_order=") }
            ?.substringAfter('=')
            ?.toIntOrNull()
    }

    /** 导出小说为指定格式文件（整系列或部分分册，后台队列，支持断点续传）。 */
    fun export(format: NovelExportFormat, chapterIds: List<Long>) {
        if (_downloading.value) return
        // targetId 用系列首册（全量未加载时用分页已加载的第一本兜底）
        val novelId = _allChapters.value.firstOrNull()?.id
            ?: paged.items.value.firstOrNull()?.id
            ?: return
        val data = mutableListOf<Pair<String, Any?>>()
        data += NovelExportWorker.KEY_NOVEL_ID to novelId
        data += NovelExportWorker.KEY_FORMAT to format.name
        data += NovelExportWorker.KEY_SERIES_ID to seriesId
        if (chapterIds.isNotEmpty()) {
            data += NovelExportWorker.KEY_CHAPTER_IDS to chapterIds.toLongArray()
        }
        val request = OneTimeWorkRequestBuilder<NovelExportWorker>()
            .setInputData(workDataOf(*data.toTypedArray()))
            .build()
        WorkManager.getInstance(context).enqueue(request)
        _downloading.value = true
        _downloadProgress.value = context.getString(R.string.novel_msg_export_queued)
        _message.trySend(UiMessage(R.string.novel_msg_export_queued))
        observeExportCompletion(novelId)
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
