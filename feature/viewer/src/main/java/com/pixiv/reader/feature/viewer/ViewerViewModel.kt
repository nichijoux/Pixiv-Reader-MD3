package com.pixiv.reader.feature.viewer

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.common.MessageType
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.common.R as CoreR
import com.pixiv.reader.core.common.config.ViewerOrientation
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.datastore.UserPreferences
import com.pixiv.reader.core.network.model.IllustPageInfo
import com.pixiv.reader.core.network.model.toPages
import com.pixiv.reader.core.network.download.DownloadQueue
import com.pixiv.reader.core.network.download.UgoiraExportFormat
import com.pixiv.reader.core.network.download.UgoiraExportWorker
import com.pixiv.reader.core.network.illust.IllustDownloadWorker
import com.pixiv.reader.core.network.favorite.BookmarkEditor
import com.pixiv.reader.core.network.favorite.FavoriteActions
import com.pixiv.reader.core.network.message.MessageViewModel
import com.pixiv.reader.core.network.session.PixivRepository
import com.pixiv.reader.core.network.ugoira.UgoiraFrame
import com.pixiv.reader.core.network.ugoira.UgoiraLoader
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 全屏查看器 ViewModel：多图横滑 / 动图（UgoiraLoader）/ 预览·原图切换 /
 * 壁纸设置 / 收藏 / 当前页与动图导出下载（Worker 后台执行，断网自动排队）。
 * illustId 与初始 page 从 SavedStateHandle 读取（路由参数）。
 */
@HiltViewModel
class ViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val pixivRepository: PixivRepository,
    private val downloadEntryDao: DownloadEntryDao,
    private val ugoiraLoader: UgoiraLoader,
    private val userPreferences: UserPreferences,
    private val favoriteActions: FavoriteActions,
) : MessageViewModel() {

    private val illustId: Long = savedStateHandle.get<Long>("illustId") ?: 0L
    val initialPage: Int = savedStateHandle.get<Int>("page") ?: 0

    private val _illust = MutableStateFlow<Illust?>(null)
    val illust: StateFlow<Illust?> = _illust.asStateFlow()

    private val _pages = MutableStateFlow<List<IllustPageInfo>>(emptyList())
    val pages: StateFlow<List<IllustPageInfo>> = _pages.asStateFlow()

    private val _isGif = MutableStateFlow(false)
    val isGif: StateFlow<Boolean> = _isGif.asStateFlow()

    private val _ugoiraFrames = MutableStateFlow<List<UgoiraFrame>>(emptyList())
    val ugoiraFrames: StateFlow<List<UgoiraFrame>> = _ugoiraFrames.asStateFlow()

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked: StateFlow<Boolean> = _isBookmarked.asStateFlow()

    /** 收藏请求进行中（防连点：在线双击重复提交收藏接口）。 */
    private val _isBookmarking = MutableStateFlow(false)
    val isBookmarking: StateFlow<Boolean> = _isBookmarking.asStateFlow()

    /** 是否显示原图（false 显示预览图 displayUrl，true 显示原图 originalUrl）。 */
    private val _isOriginal = MutableStateFlow(false)
    val isOriginal: StateFlow<Boolean> = _isOriginal.asStateFlow()

    /** 查看器翻页方向：横向翻页 / 竖向翻页 / 无缝竖向（我的页-浏览设置控制）。 */
    val viewerOrientation: StateFlow<ViewerOrientation> =
        userPreferences.viewerOrientation.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ViewerOrientation.HORIZONTAL)

    /** 设置查看器翻页方向（横向翻页 / 竖向翻页 / 无缝竖向）。 */
    fun setViewerOrientation(value: ViewerOrientation) {
        viewModelScope.launch { userPreferences.setViewerOrientation(value) }
    }

    /** 收藏编辑器（公开/私密 + 标签收藏）：详情加载回显当前设置，弹层保存。 */
    val bookmarkEditor = BookmarkEditor(
        scope = viewModelScope,
        api = pixivRepository.api,
        targetType = "illust",
        targetId = { illustId },
        uid = { pixivRepository.pixivApi.session.loggedInUid },
    )

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            runCatching { pixivRepository.api.getIllust(illustId) }
                .onSuccess { resp ->
                    val ill = resp.illust ?: return@onSuccess
                    _illust.value = ill
                    _pages.value = ill.toPages()
                    _isBookmarked.value = ill.is_bookmarked == true
                    bookmarkEditor.onTargetLoaded(ill.is_bookmarked == true)
                    if (ill.isGif()) {
                        _isGif.value = true
                        loadUgoira()
                    }
                }
                .onFailure { sendMessage(UiMessage(R.string.viewer_msg_load_failed_reason, listOf(it.message ?: ""))) }
        }
        loadRealSizes()
    }

    /** 网页接口补齐每 P 真实宽高（无缝竖向模式按自然宽高比堆叠用；app-api 不提供） */
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

    private fun loadUgoira() {
        viewModelScope.launch {
            _ugoiraFrames.value = ugoiraLoader.prepare(illustId).orEmpty()
            if (_ugoiraFrames.value.isEmpty()) {
                sendMessage(UiMessage(R.string.viewer_msg_ugoira_load_failed))
            }
        }
    }

    /** 收藏 / 取消收藏（乐观翻转 + 防连点；断网自动入队待同步）。 */
    fun toggleBookmark() {
        if (_isBookmarking.value) return
        viewModelScope.launch {
            _isBookmarking.value = true
            val current = _isBookmarked.value
            favoriteActions.toggleIllustFavorite(illustId, !current)
                .onSuccess {
                    _isBookmarked.value = !current
                    // 收藏成功 → 编辑器回显当前设置；取消收藏 → 清空回显
                    bookmarkEditor.onTargetLoaded(!current)
                }
                .onFailure { sendMessage(UiMessage(CoreR.string.core_msg_action_failed, listOf(it.message ?: ""))) }
            _isBookmarking.value = false
        }
    }

    /**
     * 收藏编辑器保存（公开/私密 + 标签收藏）：成功后刷新收藏态、关闭弹层并提示。
     * 保存中由弹层确认按钮禁用（[BookmarkEditor.saving] 驱动），无需额外防连点。
     */
    fun saveBookmarkEditor() {
        viewModelScope.launch {
            bookmarkEditor.save()
                .onSuccess {
                    _isBookmarked.value = true
                    bookmarkEditor.close()
                    sendMessage(UiMessage(CoreR.string.core_msg_bookmark_updated))
                }
                .onFailure { sendMessage(UiMessage(CoreR.string.core_msg_action_failed, listOf(it.message ?: ""))) }
        }
    }

    /** 切换预览 / 原图显示。 */
    fun toggleOriginal() {
        _isOriginal.value = !_isOriginal.value
        viewModelScope.launch {
            sendMessage(UiMessage(if (_isOriginal.value) R.string.viewer_msg_loaded_original else R.string.viewer_msg_switched_preview))
        }
    }

    /** 把当前页设为手机壁纸（下载原图 → WallpaperManager）。 */
    fun wallpaper(page: IllustPageInfo) {
        val url = page.originalUrl ?: page.displayUrl ?: return
        viewModelScope.launch {
            sendMessage(UiMessage(R.string.viewer_msg_wallpaper_setting))
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = pixivRepository.imageClient.newCall(Request.Builder().url(url).build())
                        .execute()
                        .use { resp ->
                            if (!resp.isSuccessful) error("HTTP ${resp.code}")
                            resp.body?.bytes() ?: error("Empty response")
                        }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("Image decode failed")
                    WallpaperManager.getInstance(context).setBitmap(bitmap)
                }
            }
            result
                .onSuccess { sendMessage(UiMessage(R.string.viewer_msg_wallpaper_set)) }
                .onFailure { sendMessage(UiMessage(R.string.viewer_msg_wallpaper_failed, listOf(it.message ?: ""))) }
        }
    }

    /**
     * 下载当前页原图：走 [IllustDownloadWorker] 单页任务（后台执行，切走页面不中断）。
     * 入队即建「待同步」索引条目：断网自动排队，联网自动开始（`.part` 断点语义与整本一致）。
     */
    fun download(page: IllustPageInfo) {
        val index = _pages.value.indexOf(page).takeIf { it >= 0 } ?: 0
        viewModelScope.launch {
            trySendMessage(UiMessage(R.string.viewer_msg_download_started))
            // 入队即建「待同步」条目（卡片立即可见；Worker 起跑覆写为下载中）
            runCatching {
                DownloadQueue.markPending(
                    downloadEntryDao,
                    DownloadEntryEntity(
                        targetId = illustId,
                        targetType = "illust",
                        title = _illust.value?.title,
                        coverUrl = _illust.value?.image_urls?.medium
                            ?: _illust.value?.image_urls?.square_medium,
                        pageCount = _pages.value.size,
                        width = _illust.value?.width ?: 0,
                        height = _illust.value?.height ?: 0,
                        payloadJson = illustPayloadJson(),
                    ),
                )
            }
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<IllustDownloadWorker>()
                    .setInputData(
                        workDataOf(
                            IllustDownloadWorker.KEY_ILLUST_ID to illustId,
                            IllustDownloadWorker.KEY_PAGE_INDEX to index.toLong(),
                        ),
                    )
                    .setConstraints(DownloadQueue.networkConstraints())
                    .addTag(DownloadQueue.workTag("illust", illustId, "", ""))
                    .build(),
            )
        }
    }

    /** 作品卡片快照 JSON（与浏览历史同格式，下载管理页完整显示用；无详情时 null 走结构字段回退）。 */
    private fun illustPayloadJson(): String? = _illust.value?.let { ill ->
        org.json.JSONObject().apply {
            put("id", ill.id)
            put("title", ill.title.orEmpty())
            put("coverUrl", ill.image_urls?.medium ?: ill.image_urls?.square_medium)
            put("width", ill.width)
            put("height", ill.height)
            put("bookmarks", ill.total_bookmarks ?: 0)
            put("pageCount", ill.page_count ?: 0)
            put("isBookmarked", ill.is_bookmarked == true)
        }.toString()
    }

    /** 导出动图（MP4 视频 / ZIP 帧包）：后台 Worker 执行，进度见下载管理页（Range 断点续传 + 有限重试）。
     * 入队即建「待同步」索引条目：断网自动排队，联网后自动开始。 */
    fun downloadGif(format: UgoiraExportFormat) {
        viewModelScope.launch {
            runCatching {
                DownloadQueue.markPending(
                    downloadEntryDao,
                    DownloadEntryEntity(
                        targetId = illustId,
                        targetType = "ugoira",
                        title = _illust.value?.title,
                        coverUrl = _illust.value?.image_urls?.medium
                            ?: _illust.value?.image_urls?.square_medium,
                        format = format.format,
                        width = _illust.value?.width ?: 0,
                        height = _illust.value?.height ?: 0,
                        payloadJson = illustPayloadJson(),
                    ),
                )
            }
        }
        WorkManager.getInstance(context).enqueue(
            OneTimeWorkRequestBuilder<UgoiraExportWorker>()
                .setInputData(
                    workDataOf(
                        UgoiraExportWorker.KEY_ILLUST_ID to illustId,
                        UgoiraExportWorker.KEY_FORMAT to format.format,
                    )
                )
                .setConstraints(DownloadQueue.networkConstraints())
                .addTag(DownloadQueue.workTag("ugoira", illustId, format.format, ""))
                .build(),
        )
        viewModelScope.launch { sendMessage(UiMessage(R.string.viewer_msg_ugoira_export_started)) }
    }

    /** 举报占位：P7 接入 /v2/illust/report */
    fun report() {
        viewModelScope.launch { sendMessage(UiMessage(R.string.viewer_msg_report_wip)) }
    }
}
