package com.pixiv.reader.feature.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixivapi.model.Illust
import com.pixiv.reader.core.model.IllustPageInfo
import com.pixiv.reader.core.model.toPages
import com.pixiv.reader.core.network.session.PixivRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pixivRepository: PixivRepository,
    private val ugoiraLoader: UgoiraLoader,
    private val imageSaver: ImageSaver,
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

    fun download(page: IllustPageInfo) {
        viewModelScope.launch {
            val url = page.originalUrl ?: page.displayUrl ?: return@launch
            imageSaver.save(url, "pixiv_${illustId}.jpg")
                .onSuccess { _message.send("已保存到下载目录") }
                .onFailure { _message.send("下载失败：${it.message}") }
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
