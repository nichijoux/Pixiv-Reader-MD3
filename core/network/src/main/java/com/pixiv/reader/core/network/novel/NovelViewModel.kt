package com.pixiv.reader.core.network.novel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.pixiv.api.model.Novel
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.common.loadFailureMessage
import com.pixiv.reader.core.common.R as CoreR
import com.pixiv.reader.core.common.model.toCardData
import com.pixiv.reader.core.database.dao.BrowseHistoryDao
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.dao.ReadingProgressDao
import com.pixiv.reader.core.database.entity.BrowseHistoryEntity
import com.pixiv.reader.core.database.entity.ReadingProgressEntity
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.message.MessageViewModel
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 小说详情 ViewModel：详情 / 系列章节 / 阅读进度 / 收藏 / 追更。
 * 评论已独立（详情页不加载评论）。导出触发经 [exportRequest] 回调交 feature 层处理
 * （core 不依赖 feature 的 NovelExportWorker/Format）。
 *
 * 排行右栏复用：无路由参数时 init 不预载，等 [switchTo] 换作品加载。
 */
@HiltViewModel
class NovelViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val pixivRepository: PixivRepository,
    private val readingProgressDao: ReadingProgressDao,
    private val browseHistoryDao: BrowseHistoryDao,
    private val downloadEntryDao: DownloadEntryDao,
    private val favoriteActions: FavoriteActions,
) : MessageViewModel() {

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

    /** 作者是否已关注（详情页作者名旁关注按钮）。 */
    private val _isAuthorFollowed = MutableStateFlow(false)
    val isAuthorFollowed: StateFlow<Boolean> = _isAuthorFollowed.asStateFlow()

    /** 作者关注操作进行中（防连点）。 */
    private val _isAuthorFollowing = MutableStateFlow(false)
    val isAuthorFollowing: StateFlow<Boolean> = _isAuthorFollowing.asStateFlow()

    /** 下载/导出进行中 */
    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading.asStateFlow()

    /** 下载/导出进度文案（如"正在下载第 3/12 章…"） */
    private val _downloadProgress = MutableStateFlow<String?>(null)
    val downloadProgress: StateFlow<String?> = _downloadProgress.asStateFlow()

    /**
     * 导出请求回调（feature 层接线：收到后调自己的 export 实现，如 NovelExportWorker 入队）。
     * core 只负责把触发事件 + 导出中状态（downloading 复位）转发，不依赖 feature 导出实现。
     * @param novelId 目标小说 id；@param seriesId 非空时导出整个系列；@param formatName NovelExportFormat.name
     */
    var exportRequest: ((novelId: Long, seriesId: Long?, formatName: String) -> Unit)? = null

    init {
        // 详情路由必有 id；排行右栏（无路由参数）不预载，等 switchTo
        if (novelId > 0L) load()
    }

    /**
     * 切换到另一小说（排行右栏选中项变化时调用）。
     * 清空旧作品全部状态后重新加载；加载期间旧内容先清空避免错位。
     */
    fun switchTo(id: Long) {
        if (id == novelId || id <= 0L) return
        _novel.value = null
        _seriesNovels.value = emptyList()
        _error.value = null
        _isBookmarked.value = false
        _isBookmarking.value = false
        _isWatchlisted.value = false
        _isWatchlisting.value = false
        _isAuthorFollowed.value = false
        _isAuthorFollowing.value = false
        _downloading.value = false
        _downloadProgress.value = null
        load(id)
    }

    fun load(id: Long = novelId) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching { pixivRepository.api.getNovel(id) }
                .onSuccess { resp ->
                    val detail = resp.novel ?: return@onSuccess
                    _novel.value = detail
                    _isBookmarked.value = detail.is_bookmarked == true
                    // 详情内嵌 user.is_followed 可能缺失，用 user/detail 权威刷新关注态（失败保留内嵌值）
                    _isAuthorFollowed.value = detail.user?.is_followed == true
                    detail.user?.id?.let { loadAuthorFollowState(it) }
                    recordHistory(detail)
                    loadProgress(id)
                    loadSeries(detail)
                }
                .onFailure {
                    _error.value = loadFailureMessage(
                        it,
                        CoreR.string.core_novel_load_failed_reason,
                        CoreR.string.core_novel_load_failed,
                    )
                }
            _isLoading.value = false
        }
    }

    /** 打开详情时写入浏览历史（先删旧记录避免重复；payloadJson 存完整卡片数据供历史页展示）。 */
    private fun recordHistory(detail: Novel) {
        viewModelScope.launch {
            runCatching {
                val card = detail.toCardData()
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

    private suspend fun loadProgress(id: Long) {
        _progress.value = readingProgressDao.getByNovel(id)
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
            favoriteActions.toggleNovelFavorite(novelId, !current)
                .onSuccess {
                    _isBookmarked.value = !current
                    sendMessage(if (!current) UiMessage(CoreR.string.core_msg_bookmarked) else UiMessage(
                        CoreR.string.core_msg_unbookmarked
                    ))
                }
                .onFailure {
                    sendMessage(UiMessage(
                        CoreR.string.core_msg_action_failed,
                        listOf(it.message ?: "")
                    ))
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
                sendMessage(if (!current) UiMessage(CoreR.string.core_msg_watching_added) else UiMessage(
                    CoreR.string.core_msg_watching_removed
                ))
            }.onFailure {
                sendMessage(UiMessage(CoreR.string.core_msg_action_failed, listOf(it.message ?: "")))
            }
            _isWatchlisting.value = false
        }
    }

    // ── 关注作者 ─────────────────────────────────────────────────────────────

    /** 用 user/detail 权威刷新作者关注态（best-effort，失败保留内嵌值）。 */
    private fun loadAuthorFollowState(userId: Long) {
        viewModelScope.launch {
            runCatching { pixivRepository.api.getUserDetail(userId) }
                .onSuccess { resp ->
                    resp.user?.is_followed?.let { _isAuthorFollowed.value = it }
                }
        }
    }

    /** 关注 / 取关作者（详情页作者名旁按钮，乐观翻转 + 防连点）。 */
    fun toggleFollowAuthor() {
        if (_isAuthorFollowing.value) return
        val userId = _novel.value?.user?.id ?: return
        viewModelScope.launch {
            _isAuthorFollowing.value = true
            val current = _isAuthorFollowed.value
            runCatching {
                if (current) pixivRepository.api.unfollowUser(userId)
                else pixivRepository.api.followUser(userId, "public")
            }.onSuccess {
                _isAuthorFollowed.value = !current
                sendMessage(if (!current) UiMessage(CoreR.string.core_msg_followed_author) else UiMessage(CoreR.string.core_msg_unfollowed))
            }.onFailure {
                sendMessage(UiMessage(CoreR.string.core_msg_action_failed, listOf(it.message ?: "")))
            }
            _isAuthorFollowing.value = false
        }
    }

    // ── 下载 / 导出 ──────────────────────────────────────────────────────────

    /**
     * 导出小说（单本或整个系列）。core 层不依赖 feature 的导出实现：
     * 仅触发 [exportRequest] 回调（feature 层入队 Worker），并管理导出中状态复位。
     * @param formatName NovelExportFormat.name（"TXT"/"EPUB"/"PDF"/"MARKDOWN"/"DOCX"）
     */
    fun export(formatName: String, series: Boolean = false) {
        val detail = _novel.value ?: return
        if (_downloading.value) return
        val seriesId = if (series) detail.series?.id else null
        if (series && seriesId == null) return
        exportRequest?.invoke(detail.id, seriesId, formatName)
        _downloading.value = true
        _downloadProgress.value = context.getString(CoreR.string.core_msg_export_queued)
        trySendMessage(UiMessage(CoreR.string.core_msg_export_queued))
        observeExportStateReset(detail.id)
    }

    /** 观察导出结束：等 downloading 出现后，等 done/failed，复位导出中状态。
     * 完成/失败通知由全局 DownloadCompletionNotifier 统一负责（离开页面也能收到）。 */
    private fun observeExportStateReset(id: Long) {
        viewModelScope.launch {
            downloadEntryDao.observeAll().first { entries ->
                entries.any { it.targetId == id && it.targetType == "novel" && it.status == "downloading" }
            }
            downloadEntryDao.observeAll().first { entries ->
                entries.any { it.targetId == id && it.targetType == "novel" && (it.status == "done" || it.status == "failed") }
            }
            _downloading.value = false
            _downloadProgress.value = null
        }
    }
}
