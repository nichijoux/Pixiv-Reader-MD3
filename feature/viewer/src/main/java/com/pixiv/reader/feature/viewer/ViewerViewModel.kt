package com.pixiv.reader.feature.viewer

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.Illust
import com.pixiv.reader.core.common.MessageType
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.common.ViewerOrientation
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.datastore.UserPreferences
import com.pixiv.reader.core.model.IllustPageInfo
import com.pixiv.reader.core.model.toPages
import com.pixiv.reader.core.network.download.ProgressDownloader
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * 全屏查看器 ViewModel：多图横滑 / 动图（UgoiraLoader）/ 预览·原图切换 /
 * 壁纸设置 / 收藏 / 当前页下载（前台分块下载，写下载索引含字节进度）。
 * illustId 与初始 page 从 SavedStateHandle 读取（路由参数）。
 */
@HiltViewModel
class ViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val pixivRepository: PixivRepository,
    private val progressDownloader: ProgressDownloader,
    private val downloadEntryDao: DownloadEntryDao,
    private val ugoiraLoader: UgoiraLoader,
    private val userPreferences: UserPreferences,
) : ViewModel() {

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

    /** 是否显示原图（false 显示预览图 displayUrl，true 显示原图 originalUrl）。 */
    private val _isOriginal = MutableStateFlow(false)
    val isOriginal: StateFlow<Boolean> = _isOriginal.asStateFlow()

    private val _message = Channel<UiMessage>(Channel.BUFFERED)
    val message = _message.receiveAsFlow()

    /** 查看器翻页方向：横向翻页 / 竖向翻页 / 无缝竖向（我的页-浏览设置控制）。 */
    val viewerOrientation: StateFlow<ViewerOrientation> =
        userPreferences.viewerOrientation.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ViewerOrientation.HORIZONTAL)

    /** 设置查看器翻页方向（横向翻页 / 竖向翻页 / 无缝竖向）。 */
    fun setViewerOrientation(value: ViewerOrientation) {
        viewModelScope.launch { userPreferences.setViewerOrientation(value) }
    }

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
                    if (ill.isGif()) {
                        _isGif.value = true
                        loadUgoira()
                    }
                }
                .onFailure { _message.send(UiMessage(R.string.viewer_msg_load_failed_reason, listOf(it.message ?: ""))) }
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
                _message.send(UiMessage(R.string.viewer_msg_ugoira_load_failed))
            }
        }
    }

    fun toggleBookmark() {
        viewModelScope.launch {
            val current = _isBookmarked.value
            runCatching {
                if (current) {
                    pixivRepository.api.unbookmarkIllust(illustId)
                } else {
                    pixivRepository.api.bookmarkIllust(illustId, "public", emptyList())
                }
            }
                .onSuccess { _isBookmarked.value = !current }
                .onFailure { _message.send(UiMessage(R.string.viewer_msg_action_failed, listOf(it.message ?: ""))) }
        }
    }

    /** 切换预览 / 原图显示。 */
    fun toggleOriginal() {
        _isOriginal.value = !_isOriginal.value
        viewModelScope.launch {
            _message.send(UiMessage(if (_isOriginal.value) R.string.viewer_msg_loaded_original else R.string.viewer_msg_switched_preview))
        }
    }

    /** 把当前页设为手机壁纸（下载原图 → WallpaperManager）。 */
    fun wallpaper(page: IllustPageInfo) {
        val url = page.originalUrl ?: page.displayUrl ?: return
        viewModelScope.launch {
            _message.send(UiMessage(R.string.viewer_msg_wallpaper_setting))
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
                .onSuccess { _message.send(UiMessage(R.string.viewer_msg_wallpaper_set)) }
                .onFailure { _message.send(UiMessage(R.string.viewer_msg_wallpaper_failed, listOf(it.message ?: ""))) }
        }
    }

    /** 下载当前页原图（前台分块下载，写索引含字节进度；单页快，切走页面会中断）。 */
    fun download(page: IllustPageInfo) {
        val url = page.originalUrl ?: page.displayUrl ?: return
        val index = _pages.value.indexOf(page).takeIf { it >= 0 } ?: 0
        viewModelScope.launch {
            _message.trySend(UiMessage(R.string.viewer_msg_download_started))
            recordDownload(null, "downloading", progress = 0)
            var lastWritten = -1
            progressDownloader.download(url, "pixiv_${illustId}/p_${index + 1}.jpg") { done, total ->
                // 字节进度 → 百分比，节流（≥2% 才写数据库）
                val pct = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 99) else 0
                if (pct - lastWritten >= 2) {
                    lastWritten = pct
                    downloadEntryDao.updateProgress(illustId, pct)
                }
            }
                .onSuccess { file ->
                    recordDownload(file.path, "done", progress = 100)
                    _message.trySend(UiMessage(R.string.viewer_msg_saved_to_downloads, type = MessageType.SUCCESS))
                }
                .onFailure {
                    recordDownload(null, "failed")
                    _message.trySend(UiMessage(R.string.viewer_msg_download_failed, listOf(it.message ?: ""), type = MessageType.ERROR))
                }
        }
    }

    /** 写入下载索引（targetType=illust；解析本地文件真实宽高）。 */
    private fun recordDownload(localPath: String?, status: String, progress: Int = 0) {
        viewModelScope.launch {
            runCatching {
                var w = 0
                var h = 0
                if (localPath != null) {
                    val file = java.io.File(localPath)
                    if (file.exists()) {
                        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        android.graphics.BitmapFactory.decodeFile(file.path, opts)
                        w = opts.outWidth
                        h = opts.outHeight
                    }
                }
                downloadEntryDao.upsert(
                    DownloadEntryEntity(
                        targetId = illustId,
                        targetType = "illust",
                        title = _illust.value?.title,
                        coverUrl = _illust.value?.image_urls?.medium
                            ?: _illust.value?.image_urls?.square_medium,
                        localPath = localPath,
                        status = status,
                        progress = progress,
                        pageCount = _pages.value.size,
                        width = if (w > 0) w else (_illust.value?.width ?: 0),
                        height = if (h > 0) h else (_illust.value?.height ?: 0),
                    ),
                )
            }
        }
    }

    /** 动图下载（zip）占位：P6 下载管理中实现 */
    fun downloadGifStub() {
        viewModelScope.launch { _message.send(UiMessage(R.string.viewer_msg_ugoira_download_wip)) }
    }

    /** 举报占位：P7 接入 /v2/illust/report */
    fun report() {
        viewModelScope.launch { _message.send(UiMessage(R.string.viewer_msg_report_wip)) }
    }
}
