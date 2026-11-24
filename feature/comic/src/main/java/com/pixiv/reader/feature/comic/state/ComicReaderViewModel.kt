package com.pixiv.reader.feature.comic.state

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.pixiv.api.model.ComicPage
import com.pixiv.api.model.ComicReadEpisode
import com.pixiv.reader.core.common.UiMessage
import com.pixiv.reader.core.common.loadFailureMessage
import com.pixiv.reader.core.network.comic.ComicPageLoader
import com.pixiv.reader.core.network.comic.ComicRepository
import com.pixiv.reader.core.network.message.MessageViewModel
import com.pixiv.reader.feature.comic.R
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 占位（按元数据宽高）→ 就绪（去扰还原后的本地文件）→ 失败（可单页重试）。 */
sealed interface ComicPageUi {

    data object Loading : ComicPageUi

    data class Ready(val file: File, val width: Int, val height: Int) : ComicPageUi

    data object Failed : ComicPageUi
}

sealed interface ComicReaderUiState {
    data object Loading : ComicReaderUiState

    data class Ready(
        val episode: ComicReadEpisode,
        val pages: List<ComicPageUi>,
    ) : ComicReaderUiState

    data class Error(val message: String? = null) : ComicReaderUiState
}

/**
 * fetchReadEpisode 两步签名流取页数据后逐页下载去扰落盘（[ComicPageLoader.loadPage]
 * 内部信号量限并发），失败可单页重试。
 */
@HiltViewModel
class ComicReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val comicRepository: ComicRepository,
    private val pageLoader: ComicPageLoader,
) : MessageViewModel() {

    private val episodeId: Long = savedStateHandle["episodeId"] ?: 0L

    private val _state = MutableStateFlow<ComicReaderUiState>(ComicReaderUiState.Loading)
    val state: StateFlow<ComicReaderUiState> = _state.asStateFlow()

    /** 加载代次：重进下一话 / 重试时作废旧代次的页结果，防止旧页覆盖新章节。 */
    private var loadGeneration = 0

    init {
        load()
    }

    fun load() {
        val gen = ++loadGeneration
        _state.value = ComicReaderUiState.Loading
        viewModelScope.launch {
            try {
                // 两步流：viewer 页 salt → read_v4；失败走统一错误态
                val episode = comicRepository.fetchReadEpisode(episodeId)
                if (gen != loadGeneration) return@launch
                _state.value = ComicReaderUiState.Ready(
                    episode = episode,
                    pages = episode.pages.orEmpty().map { ComicPageUi.Loading },
                )
                // 页数据齐了才排产；逐页协程 + loader 内信号量限流
                episode.pages.orEmpty().forEachIndexed { index, page -> launchPageLoad(gen, index, page) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen != loadGeneration) return@launch
                _state.value = ComicReaderUiState.Error(e.message)
                trySendMessage(loadFailureMessage(e, R.string.comic_load_failed_reason, R.string.comic_load_failed))
            }
        }
    }

    fun retryPage(index: Int) {
        val ready = _state.value as? ComicReaderUiState.Ready ?: return
        val page = ready.episode.pages?.getOrNull(index) ?: return
        if (ready.pages.getOrNull(index) is ComicPageUi.Ready) return
        updatePage(index, ComicPageUi.Loading)
        launchPageLoad(loadGeneration, index, page)
    }

    private fun launchPageLoad(gen: Int, index: Int, page: ComicPage) {
        viewModelScope.launch {
            try {
                val file = pageLoader.loadPage(episodeId, index, page)
                if (gen != loadGeneration) return@launch
                updatePage(index, ComicPageUi.Ready(file, page.width, page.height))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen != loadGeneration) return@launch
                updatePage(index, ComicPageUi.Failed)
            }
        }
    }

    private fun updatePage(index: Int, ui: ComicPageUi) {
        val ready = _state.value as? ComicReaderUiState.Ready ?: return
        if (index >= ready.pages.size) return
        val pages = ready.pages.toMutableList()
        pages[index] = ui
        _state.value = ready.copy(pages = pages)
    }
}
