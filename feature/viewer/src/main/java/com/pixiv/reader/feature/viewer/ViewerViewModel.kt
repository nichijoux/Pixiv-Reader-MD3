package com.pixiv.reader.feature.viewer

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixivapi.model.Illust
import com.pixiv.reader.core.database.dao.DownloadEntryDao
import com.pixiv.reader.core.database.entity.DownloadEntryEntity
import com.pixiv.reader.core.model.IllustPageInfo
import com.pixiv.reader.core.model.toPages
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * 全屏查看器 ViewModel：多图横滑 / 动图（UgoiraLoader）/ 预览·原图切换 /
 * 壁纸设置 / 收藏 / 原图下载（写下载索引，含真实宽高）。
 * illustId 与初始 page 从 SavedStateHandle 读取（路由参数）。
 */
@HiltViewModel
class ViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val pixivRepository: PixivRepository,
    private val ugoiraLoader: UgoiraLoader,
    private val imageSaver: ImageSaver,
    private val downloadEntryDao: DownloadEntryDao,
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

    private val _message = Channel<String>(Channel.BUFFERED)
    val message = _message.receiveAsFlow()

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
                .onFailure { _message.send("加载失败：${it.message}") }
        }
    }

    private fun loadUgoira() {
        viewModelScope.launch {
            _ugoiraFrames.value = ugoiraLoader.prepare(illustId).orEmpty()
            if (_ugoiraFrames.value.isEmpty()) {
                _message.send("动图加载失败")
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
                .onFailure { _message.send("操作失败：${it.message}") }
        }
    }

    /** 切换预览 / 原图显示。 */
    fun toggleOriginal() {
        _isOriginal.value = !_isOriginal.value
        viewModelScope.launch {
            _message.send(if (_isOriginal.value) "已加载原图" else "已切换预览")
        }
    }

    /** 把当前页设为手机壁纸（下载原图 → WallpaperManager）。 */
    fun wallpaper(page: IllustPageInfo) {
        val url = page.originalUrl ?: page.displayUrl ?: return
        viewModelScope.launch {
            _message.send("正在设置壁纸…")
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = pixivRepository.imageClient.newCall(Request.Builder().url(url).build())
                        .execute()
                        .use { resp ->
                            if (!resp.isSuccessful) error("HTTP ${resp.code}")
                            resp.body?.bytes() ?: error("空响应")
                        }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("图片解析失败")
                    WallpaperManager.getInstance(context).setBitmap(bitmap)
                }
            }
            result
                .onSuccess { _message.send("已设为壁纸") }
                .onFailure { _message.send("设置壁纸失败：${it.message}") }
        }
    }

    fun download(page: IllustPageInfo) {
        viewModelScope.launch {
            val url = page.originalUrl ?: page.displayUrl ?: return@launch
            imageSaver.save(url, "pixiv_${illustId}.jpg")
                .onSuccess { file ->
                    recordDownload(file.path, "done")
                    _message.send("已保存到下载目录")
                }
                .onFailure {
                    recordDownload(null, "failed")
                    _message.send("下载失败：${it.message}")
                }
        }
    }

    /** 写入下载索引（targetType=illust；解析本地文件真实宽高）。 */
    private fun recordDownload(localPath: String?, status: String) {
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
                        pageCount = _pages.value.size,
                        width = w,
                        height = h,
                    ),
                )
            }
        }
    }

    /** 动图下载（zip）占位：P6 下载管理中实现 */
    fun downloadGifStub() {
        viewModelScope.launch { _message.send("动图下载开发中（P6）") }
    }

    /** 举报占位：P7 接入 /v2/illust/report */
    fun report() {
        viewModelScope.launch { _message.send("举报功能开发中") }
    }
}
